import uk.org.floop.jenkins_pmd.PMD

def delete(String datasetPath) {
    echo "Deleting data/ meta graphs for path ${datasetPath}"

    PMD pmd = pmdConfig("pmd")

    def draftset = jobDraft.find() // assume it already exists

    String datasetGraph = "${pmd.config.base_uri}/graph/${datasetPath}"
    String metadataGraph = "${datasetGraph}/metadata"
    pmd.drafter.deleteGraph(draftset.id, metadataGraph)
    pmd.drafter.deleteGraph(draftset.id, datasetGraph)
}
