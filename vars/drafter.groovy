def listDraftsets(String baseUrl, String credentials, String include) {
    echo "Listing draftsets..."
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
            authentication: credentials,
            httpMode: 'GET',
            url: "${baseUrl}/v1/draftsets?include=${include}")
    if (response.status == 200) {
        return readJSON(text: response.content)
    } else {
        error "Problem listing draftsets ${response.status} : ${response.content}"
    }
}

def findDraftset(String baseUrl, String, credentials, String displayName) {
    echo "Finding draftset with display name '${displayName}'"

    def draftset = drafter.listDraftsets(baseUrl, credentials, 'owned')
    def draftset = drafts.find  { it['display-name'] == displayName }
    if (draftset) {
        draftset
    } else {
        error "Can't find draftset with the display-name '${displayName}'"
    }
}

def deleteDraftset(String baseUrl, String credentials, String id) {
    echo "Deleting draftset ${id}"
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
            authentication: credentials,
            httpMode: 'DELETE',
            url: "${baseUrl}/v1/draftset/${id}")
    if (response.status == 202) {
        def job = readJSON(text: response.content)
        waitForJob(
                "${baseUrl}${job['finished-job']}" as String,
                credentials, job['restart-id'] as String)
    } else {
        error "Problem deleting draftset ${response.status} : ${response.content}"
    }
}

def createDraftset(String baseUrl, String credentials, String label) {
    echo "Creating draftset ${label}"
    String displayName = java.net.URLEncoder.encode(label, "UTF-8")
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
            authentication: credentials,
            httpMode: 'POST',
            url: "${baseUrl}/v1/draftsets?display-name=${displayName}")
    if (response.status == 200) {
        return readJSON(text: response.content)
    } else {
        error "Problem creating draftset ${response.status} : ${response.content}"
    }
}

def deleteGraph(String baseUrl, String credentials, String id, String graph) {
    echo "Deleting graph <${graph}> from draftset ${id}"
    String encGraph = java.net.URLEncoder.encode(graph, "UTF-8")
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
                               authentication: credentials,
                               httpMode: 'DELETE',
                               url: "${baseUrl}/v1/draftset/${id}/graph?graph=${encGraph}&silent=true")
    if (response.status == 200) {
        return readJSON(text: response.content)
    } else {
        error "Problem deleting graph ${response.status} : ${response.content}"
    }
}

def addData(String baseUrl, String credentials, String id, data, String type, String graph=null) {
    echo "Adding data to draftset ${id}"
    String url = "${baseUrl}/v1/draftset/${id}/data"
    if (graph) {
        String encGraph = java.net.URLEncoder.encode(graph, "UTF-8")
        url = url + "?graph=${encGraph}"
    }
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
            authentication: credentials,
            httpMode: 'PUT',
            url: url,
            requestBody: data,
            customHeaders: [[name: 'Content-Type',
                             value: type]])
    if (response.status == 202) {
        def job = readJSON(text: response.content)
        waitForJob(
                "${baseUrl}${job['finished-job']}" as String,
                credentials, job['restart-id'] as String)
    } else {
        error "Problem adding data ${response.status} : ${response.content}"
    }
}

def publishDraftset(String baseUrl, String credentials, String id) {
    echo "Publishing draftset ${id}"
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
            authentication: credentials,
            httpMode: 'POST',
            url: "${baseUrl}/v1/draftset/${id}/publish")
    if (response.status == 202) {
        def job = readJSON(text: response.content)
        waitForJob(
                "${baseUrl}${job['finished-job']}" as String,
                credentials, job['restart-id'] as String)
        def cacheClearResponse = httpRequest(acceptType: 'APPLICATION_JSON',
                httpMode: 'PUT',
                authentication: '57e63e26-4dea-4679-ab47-840dc199e133',
                url: "http://gss-data.org.uk/_clear_cache")
        echo readJSON(text: cacheClearResponse.content)['message']
        def syncSearchResponse = httpRequest(acceptType: 'APPLICATION_JSON',
                httpMode: 'PUT',
                authentication: '57e63e26-4dea-4679-ab47-840dc199e133',
                url: "http://gss-data.org.uk/_sync_search")
        echo readJSON(text: syncSearchResponse.content)['message']
    } else {
        error "Problem publishing draftset ${response.status} : ${response.content}"
    }
}

def waitForJob(String pollUrl, String credentials, String restartId) {
    while (true) {
        jobResponse = httpRequest(acceptType: 'APPLICATION_JSON', authentication: credentials,
                httpMode: 'GET', url: pollUrl, validResponseCodes: '200:599')
        if (jobResponse.status == 404) {
            if (readJSON(text: jobResponse.content)['restart-id'] != restartId) {
                error "Failed waiting for job to finish, restart-id different."
            } else {
                sleep 10
            }
        } else if (jobResponse.status == 200) {
            def jobResponseObj = readJSON(text: jobResponse.content)
            if (jobResponseObj['restart-id'] != restartId) {
                error "Failed waiting for job to finish, restart-id different."
            } else if (jobResponseObj.type == 'ok') {
                return
            } else if (jobResponseObj.type == "error") {
                error "Pipeline error in ${jobResponseObj.details?.pipeline?.name}. ${jobResponseObj.message}"
            }
        } else {
            echo jobResponse.content
            jobResponseObj = readJSON(text: jobResponse.content)
            error "Unexpected response waiting for job to complete: ${jobResponseObj.message}"
        }
    }
}
