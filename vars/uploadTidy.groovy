def call(csvs, String mapping=null, oldLabel=null) {
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']
        String PIPELINE = config['pipeline_api']
        String baseURI = config['base_uri']
        if (!mapping) {
            if (fileExists('metadata/columns.csv')) {
                mapping = 'metadata/columns.csv'
            } else {
                mapping = config['default_mapping']
            }
        }
        if (mapping.startsWith('http')) {
            def response = httpRequest(
                    httpMode: 'GET',
                    url: mapping)
            dir ('metadata') {
                writeFile file: 'columns.csv', text: response.content
            }
            mapping = 'metadata/columns.csv'
        }
        def drafts = drafter.listDraftsets(PMD, credentials, 'owned')
        def jobDraft = drafts.find { it['display-name'] == env.JOB_NAME }
        if (jobDraft) {
            drafter.deleteDraftset(PMD, credentials, jobDraft.id)
        }
        def newJobDraft = drafter.createDraftset(PMD, credentials, env.JOB_NAME)
        String datasetPath = env.JOB_NAME.toLowerCase()
                .replaceAll('[^\\w/]', '-')
                .replaceAll('-+', '-')
                .replaceAll('-\$', '')
        String datasetGraph = "${baseURI}/graph/${datasetPath}"
        String metadataGraph = "${datasetGraph}/metadata"
        drafter.deleteGraph(PMD, credentials, newJobDraft.id, metadataGraph)
        drafter.deleteGraph(PMD, credentials, newJobDraft.id, datasetGraph)
        if (oldLabel) {
            String oldDatasetPath = oldLabel
                    .replaceAll('[^\\w/]', '-')
                    .replaceAll('-+', '-')
                    .replaceAll('-\$', '')
            String oldDatasetGraph = "${baseURI}/graph/${oldDatasetPath}"
            String oldMetadataGraph = "${oldDatasetGraph}/metadata"
            drafter.deleteGraph(PMD, credentials, newJobDraft.id, oldMetadataGraph)
            drafter.deleteGraph(PMD, credentials, newJobDraft.id, oldDatasetGraph)
        }
        drafter.addData(PMD, credentials, newJobDraft.id,
                readFile("out/dataset.trig"), "application/trig;charset=UTF-8")

        csvs.each { csv ->
            echo "Uploading ${csv}"
            runPipeline("${PIPELINE}/ons-table2qb.core/data-cube/import",
                    newJobDraft.id, credentials, [
                    [name: 'observations-csv',
                     file: [name: csv, type: 'text/csv;charset=UTF-8']],
                    [name: 'dataset-name', value: ''],
                    [name: 'dataset-slug', value: datasetPath],
                    [name: 'columns-csv', file: [name: mapping, type: 'text/csv;charset=UTF-8']]
            ])
        }
    }
}