def delete(String datasetLabel) {
    echo "Deleting dataset graphs from label ${datasetLabel}"

    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']
        String baseURI = config['base_uri']

        def draftset = drafter.findDraftset(PMD, credentials, env.JOB_NAME as String) // assume it already exists

        String datasetPath = util.slugise(datasetLabel)
        String datasetGraph = "${baseURI}/graph/${datasetPath}"
        String metadataGraph = "${datasetGraph}/metadata"
        drafter.deleteGraph(PMD, credentials, draftset.id, metadataGraph)
        drafter.deleteGraph(PMD, credentials, draftset.id, datasetGraph)
    }
}
