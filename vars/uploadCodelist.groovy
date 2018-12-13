def call(String csv, String name) {
    configFileProvider([configFile(fileId: 'pmdConfig', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']
        String PIPELINE = config['pipeline_api']
        String baseURI = config['base_uri']

        def draft = jobDraft.find()

        string codelistGraph = "${baseURI}/graph/${util.slugise(name)}"
        drafter.deleteGraph(PMD, credentials, draft.id, codelistGraph)

        runPipeline("${PIPELINE}/ons-table2qb.core/codelist/import",
                draft.id, credentials, [[name: 'codelist-csv',
                                         file: [name: csv, type: 'text/csv']],
                                        [name: 'codelist-name', value: name]])
    }
}
