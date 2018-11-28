package uk.org.floop.jenkins_pmd

import groovy.json.JsonSlurper
import hudson.FilePath
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Executor
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils

class Drafter implements Serializable {
    private PMD pmd
    private URI apiBase
    private HttpHost host
    private String user, pass

    enum Include {
        ALL("all"), OWNED("owned"), CLAIMABLE("claimable")
        public final String value
        Include(String v) {
            this.value = v
        }
    }

    Drafter(PMD pmd, String user, String pass) {
        this.pmd = pmd
        this.apiBase = new URI(pmd.config.pmd_api)
        this.host = new HttpHost(apiBase.getHost(), (apiBase.getPort() != -1) ? apiBase.getPort() :
                apiBase.getScheme() == "http" ? 80 : 443)
        this.user = user
        this.pass = pass
    }

    private Executor getExec() {
        Executor.newInstance()
                .auth(this.host, this.user, this.pass)
                .authPreemptive(this.host)
    }

    def listDraftsets(Include include=Include.ALL) {
        def js = new JsonSlurper()
        String path = (include == Include.ALL) ? "/v1/draftsets" : "/v1/draftsets?include=" + include.value
        def response = js.parse(
                getExec().execute(
                        Request.Get(apiBase.resolve(path))
                                .addHeader("Accept", "application/json")
                                .userAgent(PMDConfig.UA)
                ).returnContent().asStream()
        )
        response
    }

    private static String errorMsg(HttpResponse response) {
        EntityUtils.consume(response.getEntity())
        "${response.getStatusLine()} : ${EntityUtils.toString(response.getEntity())}"
    }

    def createDraftset(String label) {
        String displayName = URLEncoder.encode(label, "UTF-8")
        String path = "/v1/draftsets?display-name=${displayName}"
        HttpResponse response = getExec().execute(
                        Request.Post(apiBase.resolve(path))
                                .addHeader("Accept", "application/json")
                                .userAgent(PMDConfig.UA)
                ).returnResponse()
        if (response.getStatusLine().statusCode == 200) {
            return new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
        } else {
            throw new DrafterException("Problem creating draftset ${errorMsg(response)}")
        }
    }

    def deleteGraph(String draftsetId, String graph) {
        String encGraph = URLEncoder.encode(graph, "UTF-8")
        String path = "/v1/draftset/${draftsetId}/graph?graph=${encGraph}&silent=true"
        HttpResponse response = getExec().execute(
                Request.Delete(apiBase.resolve(path))
                        .addHeader("Accept", "application/json")
                        .userAgent(PMDConfig.UA)
        ).returnResponse()
        if (response.getStatusLine().statusCode == 200) {
            return new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
        } else {
            throw new DrafterException("Problem deleting graph ${errorMsg(response)}")
        }
    }

    def deleteDraftset(String draftsetId) {
        String path = "/v1/draftset/${draftsetId}"
        HttpResponse response = getExec().execute(
                        Request.Delete(apiBase.resolve(path))
                                .addHeader("Accept", "application/json")
                                .userAgent(PMDConfig.UA)
        ).returnResponse()
        if (response.getStatusLine().statusCode == 202) {
            def jobObj = new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
            waitForJob(apiBase.resolve(jobObj['finished-job'] as String), jobObj['restart-id'] as String)
        } else {
            throw new DrafterException("Problem deleting draftset ${jobObj['message']}")
        }
    }

    def waitForJob(URI finishedJob, String restartId) {
        Executor exec = getExec()
        while (true) {
            HttpResponse jobResponse = exec.execute(
                    Request.Get(finishedJob)
                            .setHeader("Accept", "application/json")
                            .userAgent(PMDConfig.UA)
            ).returnResponse()
            int status = jobResponse.getStatusLine().statusCode
            def jobObj
            try {
                jobObj = new JsonSlurper().parse(EntityUtils.toByteArray(jobResponse.getEntity()))
            } catch(e) {
                throw new DrafterException("Failed waiting for job ${errorMsg(jobResponse)}.\n${e}")
            }
            if (status == 404) {
                if (jobObj['restart-id'] != restartId) {
                    throw new DrafterException("Failed waiting for job to finish, no/different restart-id ${errorMsg(jobResponse)}")
                } else {
                    sleep(10000)
                }
            } else if (status == 200) {
                if (jobObj['restart-id'] != restartId) {
                    throw new DrafterException("Failed waiting for job to finish, restart-id is different.")
                } else if (jobObj['type'] != 'ok') {
                    throw new DrafterException("Pipeline error in ${jobObj.details?.pipeline?.name}. ${jobObj.message}")
                } else {
                    break
                }
            } else {
                throw new DrafterException("Unexpected response waiting for job to complete: ${errorMsg(jobResponse)}")
            }
        }
    }

    def addData(String draftId, String fileName, String mimeType, String encoding, String graph=null) {
        String path = "/v1/draftset/${draftId}/data"
        if (graph) {
            String encGraph = URLEncoder.encode(graph, "UTF-8")
            path = path + "?graph=${encGraph}"
        }
        HttpResponse response = getExec().execute(
                Request.Put(apiBase.resolve(path))
                        .addHeader("Accept", "application/json")
                        .userAgent(PMDConfig.UA)
                        .bodyStream(new FilePath(new File(fileName)).read(), ContentType.create(mimeType, encoding))
        ).returnResponse()
        if (response.getStatusLine().statusCode == 202) {
            def jobObj = new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
            waitForJob(apiBase.resolve(jobObj['finished-job'] as String), jobObj['restart-id'] as String)
        } else {
            throw new DrafterException("Problem adding data ${errorMsg(response)}")
        }
    }

    def findDraftset(String displayName) {
        def drafts = listDraftsets(Include.OWNED)
        def draftset = drafts.find  { it['display-name'] == displayName }
        if (draftset) {
            draftset
        } else {
            throw new DrafterException("Can't find draftset with the display-name '${displayName}'")
        }

    }

}

class DrafterException extends Throwable {
    String message

    DrafterException(String message) {
        this.message = message
    }

}
