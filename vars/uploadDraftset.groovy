def call(String datasetLabel, csvs, String mapping=null) {
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']
        String PIPELINE = config['pipeline_api']
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
            writeFile file: 'out/columns.csv', text: response.content
            mapping = 'out/columns.csv'
        }
        def drafts = drafter.listDraftsets(PMD, credentials, 'owned')
        def jobDraft = drafts.find { it['display-name'] == env.JOB_NAME }
        if (jobDraft) {
            drafter.deleteDraftset(PMD, credentials, jobDraft.id)
        }
        def newJobDraft = drafter.createDraftset(PMD, credentials, env.JOB_NAME)
        String datasetPath = datasetLabel.toLowerCase()
                .replaceAll('[^\\w/]', '-')
                .replaceAll('-+', '-')
                .replaceAll('-\$', '')
        drafter.deleteGraph(PMD, credentials, newJobDraft.id,
                "http://gss-data.org.uk/graph/${datasetPath}/metadata")
        drafter.deleteGraph(PMD, credentials, newJobDraft.id,
                "http://gss-data.org.uk/graph/${datasetPath}")
        drafter.addData(PMD, credentials, newJobDraft.id,
                readFile("out/dataset.trig"), "application/trig;charset=UTF-8")

        csvs.each { csv ->
            echo "Uploading ${csv}"
            runPipeline("${PIPELINE}/ons-table2qb.core/data-cube/import",
                    newJobDraft.id, credentials, [[name: 'observations-csv',
                                                   file: [name: csv, type: 'text/csv;charset=UTF-8']],
                                                  [name: 'dataset-name', value: datasetLabel],
                                                  [name: 'columns-csv',
                                                   file: [name: mapping, type: 'text/csv;charset=UTF-8']]])
        }
    }
}