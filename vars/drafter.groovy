def listDraftsets(baseUrl, credentials, include) {
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

def deleteDraftset(baseUrl, credentials, id) {
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
            authentication: credentials,
            httpMode: 'DELETE',
            url: "${baseUrl}/v1/draftset/${id}")
    if (response.status == 202) {
        def job = readJSON(text: response.content)
        drafter.waitForJob(
                "${baseUrl}${job['finished-job']}",
                credentials, job['restart-id'])
    } else {
        error "Problem deleting draftset ${response.status} : ${response.content}"
    }
}

def createDraftset(baseUrl, credentials, label) {
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

def deleteGraph(baseUrl, credentials, id, graph) {
    String encGraph = java.net.URLEncoder.encode(graph, "UTF-8")
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
                               authentication: 'onspmd',
                               httpMode: 'DELETE',
                               url: "${baseUrl}/v1/draftset/${id}/graph?graph=${encGraph}&silent=true")
    if (response.status == 200) {
        return readJSON(text: response.content)
    } else {
        error "Problem deleting graph ${response.status} : ${response.content}"
    }
}

def addData(baseUrl, credentials, id, data, type) {
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
            authentication: credentials,
            httpMode: 'PUT',
            url: "${baseUrl}/v1/draftset/${id}/data",
            requestBody: data,
            customHeaders: [[name: 'Content-Type',
                             value: type]])
    if (response.status == 202) {
        def job = readJSON(text: response.content)
        drafter.waitForJob(
                "${baseUrl}${job['finished-job']}",
                credentials, job['restart-id'])
    } else {
        error "Problem adding data ${response.status} : ${response.content}"
    }
}

def publishDraftset(baseUrl, credentials, id) {
    def response = httpRequest(acceptType: 'APPLICATION_JSON',
            authentication: credentials,
            httpMode: 'POST',
            url: "${baseUrl}/v1/draftset/${id}/publish")
    if (response.status == 202) {
        def job = readJSON(text: response.content)
        drafter.waitForJob(
                "${baseUrl}${job['finished-job']}",
                credentials, job['restart-id'])
    } else {
        error "Problem publishing draftset ${response.status} : ${response.content}"
    }
}

def waitForJob(pollUrl, credentials, restartId) {
    while (true) {
        jobResponse = httpRequest(acceptType: 'APPLICATION_JSON', authentication: credentials,
                httpMode: 'GET', url: pollUrl, validResponseCodes: '200:404')
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
        }
    }
}
