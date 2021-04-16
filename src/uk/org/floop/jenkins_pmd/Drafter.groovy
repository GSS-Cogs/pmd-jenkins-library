package uk.org.floop.jenkins_pmd

import groovy.json.JsonSlurper
import groovy.transform.InheritConstructors
import hudson.FilePath
import org.apache.http.Consts
import org.apache.http.HttpHeaders
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Executor
import org.apache.http.client.fluent.Form
import org.apache.http.client.fluent.Request
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils

class Drafter extends AbstractDrafter implements Serializable {
    private PMD pmd
    private URI apiBase, authToken
    private HttpHost host
    String token
    private long expires = -1

    enum Include {
        ALL("all"), OWNED("owned"), CLAIMABLE("claimable")
        public final String value

        Include(String v) {
            this.value = v
        }
    }

    enum Role {
        EDITOR("editor"), PUBLISHER("publisher"), MANAGER("manager")
        public final String value

        Role(String v) {
            this.value = v
        }
    }

    Drafter(PMD pmd) {
        this.pmd = pmd
        this.apiBase = new URI(pmd.config.pmd_api)
        this.authToken = new URI(pmd.config.oauth_token_url)
        this.host = new HttpHost(apiBase.getHost(), apiBase.getPort(), apiBase.getScheme())
    }

    private Executor getExec() {
        Executor exec = Executor.newInstance()
        if ((expires == -1) || ((System.currentTimeMillis() / 1000l) > expires)) {
            def js = new JsonSlurper()
            def response = js.parse(
                    exec.execute(
                            Request.Post(authToken)
                                    .addHeader('Accept', 'application/json')
                                    .bodyForm(Form.form()
                                            .add("grant_type", "client_credentials")
                                            .add("client_id", pmd.clientID)
                                            .add("client_secret", pmd.clientSecret)
                                            .add("audience", pmd.config.oauth_audience)
                                            .build(), Consts.UTF_8
                                    )
                    ).returnContent().asStream()
            )
            this.token = response['access_token']
            this.expires = System.currentTimeMillis() / 1000L + response['expires_in']
        }
        return exec
    }

    Collection<Dictionary<String, Object>> listDraftsets(Include include) {
        def js = new JsonSlurper()
        String path = (include == Include.ALL) ? "/v1/draftsets" : "/v1/draftsets?include=" + include.value
        def response = js.parse(
                getExec().execute(
                        Request.Get(apiBase.resolve(path))
                                .addHeader("Authorization", "Bearer ${token}")
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

    Dictionary<String, Object> createDraftset(String label) {
        String displayName = URLEncoder.encode(label, "UTF-8")
        String path = "/v1/draftsets?display-name=${displayName}"
        int retries = 5
        while (retries > 0) {
            HttpResponse response = getExec().execute(
                    Request.Post(apiBase.resolve(path))
                            .addHeader("Authorization", "Bearer ${token}")
                            .addHeader("Accept", "application/json")
                            .userAgent(PMDConfig.UA)
            ).returnResponse()
            if (response.getStatusLine().statusCode == 200) {
                return new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
            } else if (response.getStatusLine().statusCode == 503) {
                waitForLock()
            } else {
                throw new DrafterException("Problem creating draftset ${errorMsg(response)}")
            }
            retries = retries - 1
        }
        throw new DrafterException("Problem creating draftset, maximum retries reached while waiting for lock.")
    }

    def waitForLock() {
        Boolean waiting = true
        int holdOffTime = 5
        while (waiting) {
            sleep(holdOffTime * 1000)
            HttpResponse response = getExec().execute(
                    Request.Get(apiBase.resolve('/v1/status/writes-locked'))
                            .addHeader("Authorization", "Bearer ${token}")
                            .addHeader('Accept', 'application/json')
                            .userAgent(PMDConfig.UA)
            ).returnResponse()
            if (response.getStatusLine().statusCode == 200) {
                waiting = new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
            } else {
                throw new DrafterException("Problem waiting for write-lock ${errorMsg(response)}")
            }
            if (waiting && (holdOffTime < 60)) {
                holdOffTime = holdOffTime * 2
            }
        }
    }

    Dictionary<String, Object> deleteGraph(String draftsetId, String graph) {
        String encGraph = URLEncoder.encode(graph, "UTF-8")
        String path = "/v1/draftset/${draftsetId}/graph?graph=${encGraph}&silent=true"
        int retries = 5
        while (retries > 0) {
            HttpResponse response = getExec().execute(
                    Request.Delete(apiBase.resolve(path))
                            .addHeader("Authorization", "Bearer ${token}")
                            .addHeader("Accept", "application/json")
                            .userAgent(PMDConfig.UA)
            ).returnResponse()
            if (response.getStatusLine().statusCode == 200) {
                return new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
            } else if (response.getStatusLine().statusCode == 503) {
                waitForLock()
            } else {
                throw new DrafterException("Problem deleting graph ${errorMsg(response)}")
            }
            retries = retries - 1
        }
        throw new DrafterException("Problem deleting graph, maximum retries reached while waiting for lock.")
    }

    Dictionary<String, Object> deleteDraftset(String draftsetId) {
        String path = "/v1/draftset/${draftsetId}"
        int retries = 5
        while (retries > 0) {
            HttpResponse response = getExec().execute(
                    Request.Delete(apiBase.resolve(path))
                            .addHeader("Authorization", "Bearer ${token}")
                            .addHeader("Accept", "application/json")
                            .userAgent(PMDConfig.UA)
            ).returnResponse()
            if (response.getStatusLine().statusCode == 202) {
                def jobObj = new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
                waitForJob(apiBase.resolve(jobObj['finished-job'] as String), jobObj['restart-id'] as String)
                return
            } else if (response.getStatusLine().statusCode == 503) {
                waitForLock()
            } else {
                throw new DrafterException("Problem deleting draftset ${errorMsg(response)}")
            }
            retries = retries - 1
        }
        throw new DrafterException("Problem deleting draftset, maximum retries reached while waiting for lock.")
    }

    def waitForJob(URI finishedJob, String restartId) {
        Executor exec = getExec()
        int delay = 1000

        while (true) {
            HttpResponse jobResponse = exec.execute(
                    Request.Get(finishedJob)
                            .addHeader("Authorization", "Bearer ${token}")
                            .setHeader("Accept", "application/json")
                            .userAgent(PMDConfig.UA)
            ).returnResponse()
            int status = jobResponse.getStatusLine().statusCode
            def jobObj
            try {
                jobObj = new JsonSlurper().parse(EntityUtils.toByteArray(jobResponse.getEntity()))
            } catch (e) {
                throw new DrafterException("Failed waiting for job ${errorMsg(jobResponse)}.\n${e}")
            }
            if (status == 404) {
                if (jobObj['restart-id'] != restartId) {
                    throw new DrafterException("Failed waiting for job to finish, no/different restart-id ${errorMsg(jobResponse)}")
                } else {
                    sleep(delay)
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

    Dictionary<String, Object> addData(String draftId, String source, String mimeType, String encoding, String graph) {
        String path = "/v1/draftset/${draftId}/data"
        if (graph) {
            String encGraph = URLEncoder.encode(graph, "UTF-8")
            path = path + "?graph=${encGraph}"
        }
        int retries = 5
        while (retries > 0) {
            InputStream streamSource
            if (source.startsWith('http')) {
                streamSource = Request.Get(source)
                        .userAgent(PMDConfig.UA)
                        .addHeader('Accept', mimeType)
                        .execute().returnContent().asStream()
            } else {
                streamSource = new FilePath(new File(source)).read()
            }
            Request request = Request.Put(apiBase.resolve(path))
            if (source.endsWith('.gz')) {
                request = request.addHeader(HttpHeaders.CONTENT_ENCODING, "gzip")
            }
            HttpResponse response = getExec().execute(request
                    .addHeader(HttpHeaders.AUTHORIZATION, "Bearer ${token}")
                    .addHeader(HttpHeaders.ACCEPT, "application/json")
                    .userAgent(PMDConfig.UA)
                    .bodyStream(streamSource, ContentType.create(mimeType, encoding))
            ).returnResponse()
            if (response.getStatusLine().statusCode == 202) {
                def jobObj = new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
                waitForJob(apiBase.resolve(jobObj['finished-job'] as String), jobObj['restart-id'] as String)
                return
            } else if (response.getStatusLine().statusCode == 503) {
                waitForLock()
            } else {
                throw new DrafterException("Problem adding data ${errorMsg(response)}")
            }
            retries = retries - 1
        }
        throw new DrafterException("Problem adding data, maximum retries reached while waiting for lock.")
    }

    Dictionary<String, Object> findDraftset(String displayName, Include include) {
        def drafts = listDraftsets(include)
        def draftset = drafts.find { it['display-name'] == displayName }
        if (draftset) {
            draftset
        } else {
            throw new DrafterException("Can't find draftset with the display-name '${displayName}'")
        }

    }

    Dictionary<String, Object> submitDraftsetTo(String id, Role role, String user) {
        String path = "/v1/draftset/${id}/submit-to"
        Executor exec = getExec()
        URIBuilder uriBuilder = new URIBuilder(apiBase.resolve(path))
        if (role != null) {
            uriBuilder.setParameter("role", role.value)
        }
        if (user != null) {
            uriBuilder.setParameter("user", user)
        }
        int retries = 5
        while (retries > 0) {
            HttpResponse response = exec.execute(
                    Request.Post(uriBuilder.build())
                            .addHeader("Authorization", "Bearer ${token}")
                            .addHeader("Accept", "application/json")
                            .userAgent(PMDConfig.UA)
            ).returnResponse()
            if (response.getStatusLine().statusCode == 200) {
                return new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
            } else if (response.getStatusLine().statusCode == 422) {
                throw new DrafterException("The submit request could not be processed ${errorMsg(response)}")
            } else if (response.getStatusLine().statusCode == 503) {
                waitForLock()
            } else {
                throw new DrafterException("Problem submitting draftset ${errorMsg(response)}")
            }
            retries = retries - 1
        }
        throw new DrafterException("Problem submitting draftset, maximum retries reached while waiting for lock.")
    }

    Dictionary<String, Object> claimDraftset(String id) {
        String path = "/v1/draftset/${id}/claim"
        Executor exec = getExec()
        HttpResponse response = exec.execute(
                Request.Put(apiBase.resolve(path))
                        .addHeader("Authorization", "Bearer ${token}")
                        .addHeader("Accept", "application/json")
                        .userAgent(PMDConfig.UA)
        ).returnResponse()
        if (response.getStatusLine().statusCode != 200) {
            throw new DrafterException("Unable to claim draftset ${errorMsg(response)}")
        }
        return new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
    }

    Dictionary<String, Object> publishDraftset(String id) {
        String path = "/v1/draftset/${id}/publish"
        Executor exec = getExec()
        int retries = 5
        while (retries > 0) {
            HttpResponse response = exec.execute(
                    Request.Post(apiBase.resolve(path))
                            .addHeader("Authorization", "Bearer ${token}")
                            .addHeader("Accept", "application/json")
                            .userAgent(PMDConfig.UA)
            ).returnResponse()
            if (response.getStatusLine().statusCode == 202) {
                def jobObj = new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
                waitForJob(apiBase.resolve(jobObj['finished-job'] as String), jobObj['restart-id'] as String)
                return
            } else if (response.getStatusLine().statusCode == 503) {
                waitForLock()
            } else {
                throw new DrafterException("Problem publishing draftset ${errorMsg(response)}")
            }
            retries = retries - 1
        }
        throw new DrafterException("Problem publishing draftset, maximum retries reached while waiting for lock.")
    }

    URI getDraftsetEndpoint(String id, String action) {
        String path = "/v1/draftset/${id}/${action}"
        apiBase.resolve(path)
    }

    Object query(String id, String query, Boolean unionWithLive, Integer timeout, String accept) {
        URIBuilder uriBuilder = new URIBuilder(getDraftsetEndpoint(id, "query"))
        uriBuilder.setParameter("union-with-live", unionWithLive.toString())
        if (timeout != null) {
            uriBuilder.setParameter("timeout", timeout.toString())
        }
        Executor exec = getExec()
        HttpResponse response = exec.execute(
                Request.Post(uriBuilder.build())
                        .addHeader("Authorization", "Bearer ${token}")
                        .addHeader("Accept", accept)
                        .userAgent(PMDConfig.UA)
                        .bodyForm(Form.form().add("query", query).build())
        ).returnResponse()
        if (response.getStatusLine().statusCode == 200) {
            if (accept == "application/sparql-results+json") {
                return new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
            } else {
                return EntityUtils.toString(response.getEntity())
            }
        } else {
            throw new DrafterException("Problem running query ${errorMsg(response)}")
        }
    }

    void update(String draftId, String query, Integer timeout = null) {
        URIBuilder uriBuilder = new URIBuilder(getDraftsetEndpoint(draftId, "update"))
        if (timeout != null) {
            uriBuilder.setParameter("timeout", timeout.toString())
        }
        Executor exec = getExec()
        HttpResponse response = exec.execute(
                Request.Post(uriBuilder.build())
                        .addHeader("Authorization", "Bearer ${token}")
                        .userAgent(PMDConfig.UA)
                        .bodyForm(Form.form().add("query", query).build())
        ).returnResponse()

        if (response.getStatusLine().statusCode != 204) {
            throw new DrafterException("Problem running query ${errorMsg(response)}")
        }
    }


}

@InheritConstructors
class DrafterException extends Exception {}
