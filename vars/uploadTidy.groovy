def call(csvs, String mapping=null, String oldLabel=null) {
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

        try {
            def oldJobDraft = drafter.findDraftset(PMD, credentials, env.JOB_NAME)
            drafter.deleteDraftset(PMD, credentials, oldJobDraft.id)
        } catch(e) {
            echo 'No old job draft to delete'
        }

        def newJobDraft = drafter.createDraftset(PMD, credentials, env.JOB_NAME)

        dataset.delete(env.JOB_NAME)
        if (oldLabel) {
            dataset.delete(oldLabel)
        }
        drafter.addData(PMD, credentials, newJobDraft.id,
                readFile("out/dataset.trig"), "application/trig;charset=UTF-8")

        String datasetPath = util.slugise(env.JOB_NAME)
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
