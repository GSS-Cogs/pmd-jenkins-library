def call() {
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']
        def drafts = drafter.listDraftsets(PMD, credentials, 'owned')
        def jobDraft = drafts.find  { it['display-name'] == env.JOB_NAME }
        if (jobDraft) {
            drafter.publishDraftset(PMD, credentials, jobDraft.id)
        } else {
            error "Expecting a draftset for this job."
        }
    }
}