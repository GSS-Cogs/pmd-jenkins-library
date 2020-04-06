package uk.org.floop.jenkins_pmd

import groovy.json.JsonSlurper
import groovy.transform.InheritConstructors
import hudson.FilePath
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.client.fluent.Executor
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils
import org.apache.http.client.utils.URIBuilder

import java.util.zip.GZIPInputStream

class Drafter implements Serializable {
    private PMD pmd
    private URI apiBase
    private HttpHost host, cacheHost
    private String user, pass, cacheUser, cachePass

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

    Drafter(PMD pmd, String user, String pass, String cacheUser, String cachePass) {
        this.pmd = pmd
        this.apiBase = new URI(pmd.config.pmd_api)
        this.host = new HttpHost(apiBase.getHost(), apiBase.getPort(), apiBase.getScheme())
        this.user = user
        this.pass = pass
        if (pmd.config.empty_cache) {
            URI cacheBase = new URI(pmd.config.empty_cache)
            this.cacheHost = new HttpHost(cacheBase.getHost(), cacheBase.getPort(), cacheBase.getScheme())
            this.cacheUser = cacheUser
            this.cachePass = cachePass
        }
    }

    private Executor getExec() {
        Executor exec = Executor.newInstance()
                .auth(this.host, this.user, this.pass)
                .authPreemptive(this.host)
        if (pmd.config.cache_credentials) {
            return exec
                    .auth(this.cacheHost, this.cacheUser, this.cachePass)
                    .authPreemptive(this.cacheHost)
        } else {
            return exec
        }
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
        int retries = 5
        while (retries > 0) {
            HttpResponse response = getExec().execute(
                            Request.Post(apiBase.resolve(path))
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
        while(waiting) {
            sleep(holdOffTime * 1000)
            HttpResponse response = getExec().execute(
                    Request.Get(apiBase.resolve('/v1/status/writes-locked'))
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

    def deleteGraph(String draftsetId, String graph) {
        String encGraph = URLEncoder.encode(graph, "UTF-8")
        String path = "/v1/draftset/${draftsetId}/graph?graph=${encGraph}&silent=true"
        int retries = 5
        while (retries > 0) {
            HttpResponse response = getExec().execute(
                    Request.Delete(apiBase.resolve(path))
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

    def deleteDraftset(String draftsetId) {
        String path = "/v1/draftset/${draftsetId}"
        int retries = 5
        while (retries > 0) {
            HttpResponse response = getExec().execute(
                            Request.Delete(apiBase.resolve(path))
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

    def addData(String draftId, String source, String mimeType, String encoding, String graph=null) {
        String path = "/v1/draftset/${draftId}/data"
        if (graph) {
            String encGraph = URLEncoder.encode(graph, "UTF-8")
            path = path + "?graph=${encGraph}"
        }
        int retries = 5
        while (retries > 0) {
            InputStream streamSource
            if (source.startsWith('http')) {
                streamSource = Request
                    .Get(source)
                    .userAgent(PMDConfig.UA)
                    .addHeader('Accept' ,mimeType)
                    .execute().returnContent().asStream()
            } else if (source.endsWith('.gz')) {
                streamSource = new GZIPInputStream(new FilePath(new File(source)).read())
            } else {
                streamSource = new FilePath(new File(source)).read()
            }
            HttpResponse response = getExec().execute(
                    Request.Put(apiBase.resolve(path))
                            .addHeader("Accept", "application/json")
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

    def findDraftset(String displayName) {
        def drafts = listDraftsets(Include.ALL)
        def draftset = drafts.find  { it['display-name'] == displayName }
        if (draftset) {
            draftset
        } else {
            throw new DrafterException("Can't find draftset with the display-name '${displayName}'")
        }

    }

    def submitDraftsetTo(String id, Role role, String user) {
        String path = "/v1/draftset/${id}/submit-to"
        Executor exec = getExec()
        URIBuilder uriBuilder = new URIBuilder(apiBase.resolve(path))
        if (role != null) {
            uriBuilder.setParameter("role", role.value)
        }
        if (user != null) {
            uriBuilder.setParameter("user", user)
        }
        HttpResponse response = exec.execute(
                Request.Post(uriBuilder.build())
                        .addHeader("Accept", "application/json")
                        .userAgent(PMDConfig.UA)
        ).returnResponse()
        if (response.getStatusLine().statusCode == 200) {
            return new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
        } else if (response.getStatusLine().statusCode == 422) {
            throw new DrafterException("The submit request could not be processed ${errorMsg(response)}")
        } else {
            throw new DrafterException("Problem submitting draftset ${errorMsg(response)}")
        }
    }

    def publishDraftset(String id) {
        String path = "/v1/draftset/${id}/publish"
        Executor exec = getExec()
        int retries = 5
        while (retries > 0) {
            HttpResponse response = exec.execute(
                    Request.Post(apiBase.resolve(path))
                            .addHeader("Accept", "application/json")
                            .userAgent(PMDConfig.UA)
            ).returnResponse()
            if (response.getStatusLine().statusCode == 202) {
                def jobObj = new JsonSlurper().parse(EntityUtils.toByteArray(response.getEntity()))
                waitForJob(apiBase.resolve(jobObj['finished-job'] as String), jobObj['restart-id'] as String)
                if (pmd.config.empty_cache) {
                    try {
                        exec.execute(
                                Request.Put(pmd.config.empty_cache)
                                        .addHeader("Accept", "application/json")
                                        .userAgent(PMDConfig.UA))
                    } catch (org.apache.http.impl.execchain.RequestAbortedException e) {
                        println('Request aborted while attempting to empty cache.')
                    }
                }
                if (pmd.config.sync_search) {
                    try {
                        exec.execute(
                                Request.Put(pmd.config.sync_search)
                                        .addHeader("Accept", "application/json")
                                        .userAgent(PMDConfig.UA))
                    } catch (org.apache.http.impl.execchain.RequestAbortedException e) {
                        println('Request aborted while attempting to sync search.')
                    }
                }
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

    def getDraftsetEndpoint(String id) {
        String path = "/v1/draftset/${id}/query"
        apiBase.resolve(path)
    }

}

@InheritConstructors
class DrafterException extends Exception { }
