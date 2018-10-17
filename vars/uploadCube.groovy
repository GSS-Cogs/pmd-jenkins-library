def call(String datasetLabel, obslist) {
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']
        String PIPELINE = config['pipeline_api']
        def drafts = drafter.listDraftsets(PMD, credentials, 'owned')
        def jobDraft = drafts.find { it['display-name'] == env.JOB_NAME }
        if (jobDraft) {
            drafter.deleteDraftset(PMD, credentials, jobDraft.id as String)
        }
        def newJobDraft = drafter.createDraftset(PMD, credentials, env.JOB_NAME as String)
        String datasetPath = util.slugise(datasetLabel)
        drafter.deleteGraph(PMD, credentials, newJobDraft.id as String,
                "http://gss-data.org.uk/graph/${datasetPath}/metadata")
        drafter.deleteGraph(PMD, credentials, newJobDraft.id as String,
                "http://gss-data.org.uk/graph/${datasetPath}")
        drafter.addData(PMD, credentials, newJobDraft.id as String,
                readFile("out/dataset.trig"), "application/trig;charset=UTF-8")
        obslist.each { obsfile ->
            echo "Uploading ${obsfile}"
            drafter.addData(PMD, credentials, newJobDraft.id as String,
                    readFile(obsfile), "text/turtle;charset=UTF-8",
                    "http://gss-data.org.uk/graph/${datasetPath}")
        }
    }
}
