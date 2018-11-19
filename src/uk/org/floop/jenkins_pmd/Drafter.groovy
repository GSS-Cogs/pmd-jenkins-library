package uk.org.floop.jenkins_pmd

import groovy.json.JsonSlurper
import org.apache.http.HttpHost
import org.apache.http.client.fluent.Executor
import org.apache.http.client.fluent.Request

class Drafter implements Serializable {
    private Executor exec
    private URI apiBase
    private HttpHost host

    enum Include {
        ALL("all"), OWNED("owned"), CLAIMABLE("claimable")
        public final String value
        Include(String v) {
            this.value = v
        }
    }

    Drafter(String api, String user, String pass) {
        this.apiBase = new URI(api)
        this.host = new HttpHost(apiBase.getHost())
        exec = Executor.newInstance()
            .auth(this.host, user, pass)
            .authPreemptive(this.host)
    }

    def listDraftsets(Include include=Include.ALL) {
        def js = new JsonSlurper()
        String path = (include == Include.ALL) ? "/v1/draftsets" : "/v1/draftsets?include=" + Include.value
        def response = js.parse(
                exec.execute(
                        Request.Get(apiBase.resolve(path))
                ).returnContent().asStream()
        )
        response
    }
}