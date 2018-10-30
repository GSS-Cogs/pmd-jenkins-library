def call(String datasetLabel, csvs, String mapping=null, String datasetPath=null) {
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
            dir ('metadata') {
                writeFile file: 'columns.csv', text: response.content
            }
            mapping = 'metadata/columns.csv'
        }
        jobDraft.replace()
        def draft = jobDraft.find()
        if (!datasetPath) {
            datasetPath = util.slugise(datasetLabel)
        }
        dataset.delete(datasetPath)
        drafter.addData(PMD, credentials, draft.id,
                readFile("out/dataset.trig"), "application/trig;charset=UTF-8")

        csvs.each { csv ->
            echo "Uploading ${csv}"
            runPipeline("${PIPELINE}/ons-table2qb.core/data-cube/import",
                    draft.id, credentials, [
                    [name: 'observations-csv',
                     file: [name: csv, type: 'text/csv;charset=UTF-8']],
                    [name: 'dataset-name', value: datasetLabel],
                    [name: 'dataset-slug', value: datasetPath],
                    [name: 'columns-csv', file: [name: mapping, type: 'text/csv;charset=UTF-8']]
            ])
        }
    }
}
