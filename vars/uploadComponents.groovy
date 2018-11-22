def call(String csv) {
    configFileProvider([configFile(fileId: 'pmdConfig', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']
        String PIPELINE = config['pipeline_api']
        String baseURI = config['base_uri']

        def draft = jobDraft.find()

        runPipeline("${PIPELINE}/ons-table2qb.core/components/import",
                draft.id, credentials, [[name: 'components-csv',
                                         file: [name: csv, type: 'text/csv']]])
    }
}
