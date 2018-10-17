def create() {
    echo "Creating job draft"
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']

        drafter.createDraftset(PMD, credentials, env.JOB_NAME)
    }
}

def delete() {
    echo "Deleting job draft"
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']

        def draft = drafter.findDraftset(PMD, credentials, env.JOB_NAME)
        drafter.deleteDraftset(PMD, credentials, draft.id)
    }
}

def replace() {
    echo "Replacing job draft"
    try {
        delete()
    } catch(e) {
        echo "(no job draft to delete)"
    } finally {
        create()
    }
}

def find() {
    echo "Finding job draft"
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']

        drafter.findDraftset(PMD, credentials, env.JOB_NAME)
    }
}

def publish() {
    echo "Publishing job draft"
    configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
        def config = readJSON(text: readFile(file: configfile))
        String PMD = config['pmd_api']
        String credentials = config['credentials']

        def draft = drafter.findDraftset(PMD, credentials, env.JOB_NAME)
        drafter.publishDraftset(PMD, credentials, draft.id)
    }
}
