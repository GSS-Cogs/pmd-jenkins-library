def create() {
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']

        drafter.createDraftset(PMD, credentials, env.JOB_NAME)
    }
}

def delete() {
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']

        def draft = drafter.findDraftset(PMD, credentials, env.JOB_NAME)
        drafter.deleteDraftset(PMD, credentials, draft.id)
    }
}

def replace() {
    try {
      delete()
    }
    create()
}

def find() {
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']

        drafter.findDraftset(PMD, credentials, env.JOB_NAME)
    }
}

def publish() {
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']

        def draft = drafter.findDraftset(PMD, credentials, env.JOB_NAME)
        drafter.publishDraftset(PMD, credentials, draft.id)
    }
}
