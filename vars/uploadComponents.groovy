import uk.org.floop.jenkins_pmd.PMD

def call(String csv) {
    PMD pmd = pmdConfig('pmd')
    def draft = jobDraft.find()
    pmd.pipelines.components(draft.id as String, csv)
}