import uk.org.floop.jenkins_pmd.PMD

def create() {
    echo "Creating job draft"

    PMD pmd = pmdConfig("pmd")
    pmd.drafter.createDraftset(env.JOB_NAME)
}

def delete() {
    echo "Deleting job draft"

    PMD pmd = pmdConfig("pmd")
    def draft = pmd.drafter.findDraftset(env.JOB_NAME)
    pmd.drafter.deleteDraftset(draft.id)
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

    PMD pmd = pmdConfig("pmd")
    pmd.drafter.findDraftset(env.JOB_NAME)
}

def publish() {
    echo "Publishing job draft"

    PMD pmd = pmdConfig("pmd")
    def draft = pmd.drafter.findDraftset(env.JOB_NAME)
    pmd.drafter.publishDraftset(draft.id)
}
