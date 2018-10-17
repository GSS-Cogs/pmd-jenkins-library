def delete(String datasetPath) {
    echo "Deleting data/ meta graphs for path ${datasetPath}"

    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']
        String baseURI = config['base_uri']

        def draftset = jobDraft.find() // assume it already exists

        String datasetGraph = "${baseURI}/graph/${datasetPath}"
        String metadataGraph = "${datasetGraph}/metadata"
        drafter.deleteGraph(PMD, credentials, draftset.id, metadataGraph)
        drafter.deleteGraph(PMD, credentials, draftset.id, datasetGraph)
    }
}
