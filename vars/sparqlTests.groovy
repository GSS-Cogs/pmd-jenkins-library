import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.enums.DrafterAction
import uk.org.floop.jenkins_pmd.enums.SparqlTestGroup

def test(Map config = null) {
    if (Boolean.parseBoolean(env.SUPPRESS_JUNIT)) {
        return
    }

    boolean includeGraphsReferencedByDataset =
            (config == null || !config.containsKey("includeGraphsReferencedByDataset"))
                    ? true
                    : config.includeGraphsReferencedByDataset

    SparqlTestGroup[] testGroups =
            (config == null || !config.containsKey("sparqlTestGroups"))
                    ? [SparqlTestGroup.PMD, SparqlTestGroup.QB, SparqlTestGroup.SKOS]
                    : config.sparqlTestGroups

    boolean ignoreErrors =
            (config == null || !config.containsKey("ignoreErrors"))
                    ? true
                    : config.ignoreErrors

    def pmd = pmdConfig("pmd")
    def drafter = pmd.drafter
    String draftId = drafter.findDraftset(env.JOB_NAME, Drafter.Include.OWNED).id
    String endpoint = drafter.getDraftsetEndpoint(draftId, DrafterAction.Query)
    String dspath = util.slugise(env.JOB_NAME)
    def fromGraphs = util.jobGraphs(pmd, draftId)
    if (includeGraphsReferencedByDataset) {
        fromGraphs += util.referencedGraphs(pmd, draftId)
    }
    String fromArgs = fromGraphs.unique().collect { "-f '${it}'" }.join(' ')
    String testDirArgs = testGroups.collect {
        '-t /usr/local/tests/' + SparqlTestGroup.toDirectoryName(it)
    }.join(' ')
    String test_timeout = (pmd.config.test_timeout != null) ? "&timeout=${pmd.config.test_timeout}" : ''
    String TOKEN = drafter.getToken()
    try {
        wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: TOKEN, var: 'TOKEN']]]) {
            sh "sparql-test-runner ${ignoreErrors ? '-i' : ''} ${testDirArgs} -s '${endpoint}?union-with-live=true${test_timeout}' -l 10 -k '${TOKEN}' ${fromArgs} -r 'reports/TESTS-${dspath}.xml'"
        }
    } catch (err) {
        // Ensure we still submit the draftset to editors, so it's still
        echo "SPARQL Test Failure. Still submitting draft to editor to help diagnosis."
        drafter.submitDraftsetTo(draftId, Drafter.Role.EDITOR, null)

        // Re-throw the error to stop the build.
        throw err
    }
}
