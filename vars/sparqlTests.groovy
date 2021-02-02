import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.enums.SparqlTestGroup

def run(Map config) {
    boolean includeGraphsReferencedByDataset =
            (config == null || !config.containsKey("includeGraphsReferencedByDataset"))
                    ? true
                    : config.includeGraphsReferencedByDataset

    SparqlTestGroup[] testGroups =
            (config == null || !config.containsKey("sparqlTestGroups"))
                    ? [SparqlTestGroup.PMD, SparqlTestGroup.QB, SparqlTestGroup.SKOS]
                    : config.sparqlTestGroups

    def pmd = pmdConfig("pmd")
    def drafter = pmd.drafter
    String draftId = drafter.findDraftset(env.JOB_NAME, Drafter.Include.OWNED).id
    String endpoint = drafter.getDraftsetEndpoint(draftId)
    String dspath = util.slugise(env.JOB_NAME)
    def fromGraphs = util.jobGraphs(pmd, draftId)
    if (includeGraphsReferencedByDataset) {
        fromGraphs += util.referencedGraphs(pmd, draftId)
    }
    String fromArgs = fromGraphs.unique().collect { '-f ' + it }.join(' ')
    String TOKEN = drafter.getToken()
    try {
        wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: TOKEN, var: 'TOKEN']]]) {
            for (SparqlTestGroup testGroup in testGroups) {
                String testDirectoryName = SparqlTestGroup.toDirectoryName(testGroup)
                sh "sparql-test-runner -t /usr/local/tests/${testDirectoryName} -s '${endpoint}?union-with-live=true&timeout=180' -l 10 -k '${TOKEN}' ${fromArgs} -r 'reports/TESTS-${dspath}-qb.xml'"
            }
        }
    } catch (err) {
        // Ensure we still submit the draftset to editors, so it's still
        echo "SPARQL Test Failure. Still submitting draft to editor to help diagnosis."
        drafter.submitDraftsetTo(draftId, Drafter.Role.EDITOR, null)

        // Re-throw the error to stop the build.
        throw err
    }
}
