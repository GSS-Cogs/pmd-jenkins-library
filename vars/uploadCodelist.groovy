import uk.org.floop.jenkins_pmd.PMD

def call(String csv, String name) {
    PMD pmd = pmdConfig('pmd')
    def draft = jobDraft.find()
    pmd.drafter.deleteGraph(draft.id as String, "${pmd.config.base_uri}/graph/${util.slugize(name)}")
    pmd.pipelines.codelist(draft.id as String, csv, name)
}
