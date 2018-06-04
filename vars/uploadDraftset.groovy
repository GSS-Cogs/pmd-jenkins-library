def call(String datasetLabel, List<String> csvs) {
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']
        String PIPELINE = config['pipeline_api']
        def drafts = drafter.listDraftsets(PMD, credentials, 'owned')
        def jobDraft = drafts.find { it['display-name'] == env.JOB_NAME }
        if (jobDraft) {
            drafter.deleteDraftset(PMD, credentials, jobDraft.id)
        }
        def newJobDraft = drafter.createDraftset(PMD, credentials, env.JOB_NAME)
        String datasetPath = datasetLabel
                .replaceAll('[^\\w/]', '-')
                .replaceAll('-+', '-')
                .replaceAll('-\$', '')
        drafter.deleteGraph(PMD, credentials, newJobDraft.id,
                "http://gss-data.org.uk/graph/${datasetPath}/metadata")
        drafter.deleteGraph(PMD, credentials, newJobDraft.id,
                "http://gss-data.org.uk/graph/${datasetPath}")
        drafter.addData(PMD, credentials, newJobDraft.id,
                readFile("out/dataset.trig"), "application/trig")
        for (int i = 0; i < csvs.size(), i++ ) {
            echo "Uploading ${csvs[i]}"
            runPipeline("${PIPELINE}/ons-table2qb.core/data-cube/import",
                    newJobDraft.id, credentials, [[name: 'observations-csv',
                                                   file: [name: csvs[i], type: 'text/csv']],
                                                  [name: 'dataset-name', value: datasetLabel]])
        }
    }
}