package uk.org.floop.jenkins_pmd

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.InheritConstructors
import hudson.FilePath
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Executor
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.util.EntityUtils

import java.nio.charset.Charset

class Pipelines implements Serializable {
    private PMD pmd
    private URI apiBase
    private HttpHost host
    private String user, pass, basicAuth

    Pipelines(PMD pmd, String user, String pass) {
        this.pmd = pmd
        this.apiBase = new URI(pmd.config.pipeline_api)
        this.host = new HttpHost(apiBase.getHost(), apiBase.getPort(), apiBase.getScheme())
        this.user = user
        this.pass = pass
        this.basicAuth = "${user}:${pass}".bytes.encodeBase64()
    }

    private Executor getExec() {
        Executor.newInstance()
                .auth(this.host, this.user, this.pass)
                .authPreemptive(this.host)
    }

    private static String errorMsg(HttpResponse response) {
        "${response.getStatusLine()} : ${EntityUtils.toString(response.getEntity())}"
    }

    private MultipartEntityBuilder createDrafterBody(String draftsetId) {
        MultipartEntityBuilder.create()
                .addTextBody('__endpoint-type', 'grafter-server.destination/draftset-update')
                .addTextBody('__endpoint', JsonOutput.toJson([
                        url: "http://localhost:3001/v1/draftset/${draftsetId}/data",
                        headers: [Authorization: "Basic ${basicAuth}"]
                ]))
    }

    private def execAndWait(String apiPath, HttpEntity body) {
        HttpResponse response = getExec().execute(
                Request.Post(apiBase.resolve(apiPath))
                        .addHeader("Accept", "application/json")
                        .userAgent(PMDConfig.UA)
                        .body(body)
        ).returnResponse()
        if (response.getStatusLine().statusCode == 202) {
            def jobObj = new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
            this.pmd.drafter.waitForJob(apiBase.resolve(jobObj['finished-job'] as String), jobObj['restart-id'] as String)
        } else {
            throw new PipelineException("Failed pipeline import: ${errorMsg(response)}")
        }
    }

    def dataCube(String draftsetId, String observationsFilename, String datasetName, String datasetPath, String mapping) {
        String path = "/v1/pipelines/ons-table2qb.core/data-cube/import"
        MultipartEntityBuilder body = createDrafterBody(draftsetId)
        body.addBinaryBody(
                'observations-csv',
                new FilePath(new File(observationsFilename)).read(),
                ContentType.create('text/csv', 'UTF-8'),
                observationsFilename
        )
        body.addTextBody('dataset-name', datasetName)
        body.addTextBody('dataset-slug', datasetPath)
        if (mapping.startsWith('http')) {
            body.add(
                    'columns-csv',
                    Request
                            .Get(mapping)
                            .connectTimeout(0).socketTimeout(0)
                            .userAgent(PMDConfig.UA)
                            .addHeader('Accept', 'text/csv')
                            .execute().returnContent().asBytes(),
                    ContentType.create('text/csv', 'UTF-8'),
                    mapping
            )
        } else {
            body.addBinaryBody(
                    'columns-csv',
                    new FilePath(new File(mapping)).read(),
                    ContentType.create('text/csv', 'UTF-8'),
                    mapping
            )
        }
        execAndWait(path, body.build())
    }

    def codelist(String draftsetId, String codelistFilename, String codelistName) {
        String path = "/v1/pipelines/ons-table2qb.core/codelist/import"
        MultipartEntityBuilder body = createDrafterBody(draftsetId)
        body.addBinaryBody(
                'codelist-csv',
                new FilePath(new File(codelistFilename)).read(),
                ContentType.create('text/csv', 'UTF-8'),
                codelistFilename
        )
        body.addTextBody('codelist-name', codelistName)
        execAndWait(path, body.build())
    }

    def components(String draftsetId, String csvFilename) {
        String path = "/v1/pipelines/ons-table2qb.core/components/import"
        MultipartEntityBuilder body = createDrafterBody(draftsetId)
        body.addBinaryBody(
                'components-csv',
                new FilePath(new File(csvFilename)).read(),
                ContentType.create('text/csv', 'UTF-8'),
                csvFilename
        )
        execAndWait(path, body.build())
    }
}

@InheritConstructors
class PipelineException extends Exception { }