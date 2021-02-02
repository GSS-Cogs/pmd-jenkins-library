import uk.org.floop.jenkins_pmd.Drafter

def call() {
    def pmd = pmdConfig("pmd")
    def drafter = pmd.drafter
    String draftId = drafter.findDraftset(env.JOB_NAME, Drafter.Include.OWNED).id
    drafter.publishDraftset(draftId)
}