def call(pipelineUrl, draftsetId, credentials, params) {
    withCredentials([usernameColonPassword(credentialsId: credentials, variable: 'USERPASS')]) {
        String boundary = UUID.randomUUID().toString()
        def allParams = [
            [name: '__endpoint-type', value: 'grafter-server.destination/draftset-update'],
            [name: '__endpoint', value: groovy.json.JsonOutput.toJson([
                url: "http://localhost:3001/v1/draftset/${draftsetId}/data",
                headers: [Authorization: "Basic ${USERPASS.bytes.encodeBase64()}"]
            ])]] + params
        String body = ""
        allParams.each { param ->
            body += "--${boundary}\r\n"
            body += 'Content-Disposition: form-data; name="' + param.name + '"'
            if (param.containsKey('file')) {
                body += '; filename="' + param.file.name + '"\r\nContent-Type: "' + param.file.type + '\r\n\r\n'
                body += readFile(param.file.name) + '\r\n'
            } else {
                body += "\r\n\r\n${param.value}\r\n"
            }
        }
        body += "--${boundary}--\r\n"
        def importRequest = httpRequest(acceptType: 'APPLICATION_JSON', authentication: credentials,
                                        httpMode: 'POST', url: pipelineUrl, requestBody: body,
                                        customHeaders: [[name: 'Content-Type', value: 'multipart/form-data;boundary="' + boundary + '"']])
        if (importRequest.status == 202) {
            def importJob = readJSON(text: importRequest.content)
            String jobUrl = new java.net.URI(pipelineUrl).resolve(importJob['finished-job']) as String
            drafter.waitForJob(jobUrl, credentials, importJob['restart-id'])
        } else {
            error "Failed import, ${importRequest.status} : ${importRequest.content}"
        }
    }
}
