package uk.org.floop.jenkins_pmd

import uk.org.floop.jenkins_pmd.enums.DrafterAction

/**
 * It's unpleasant to have place this class outside of the integrationtest library, but we can't directly reference
 * the project from the integration tests due to Jenkins then complaining that the pipeline isn't CSC compliant.
 */
class MockDrafter extends AbstractDrafter {

    MockDrafter(PMD pmd) {}

    @Override
    Collection<LinkedHashMap<String, Object>> listDraftsets(Drafter.Include include) {
        []
    }

    @Override
    LinkedHashMap<String, Object> createDraftset(String label) throws DrafterException {
        [
                "id": "7F2AF1C2-3957-4DC8-A001-1A9071AE1E5C"
        ]
    }

    @Override
    LinkedHashMap<String, Object> deleteGraph(String draftsetId, String graph) throws DrafterException {
        [:]
    }

    @Override
    LinkedHashMap<String, Object> deleteDraftset(String draftsetId) throws DrafterException {
        [:]
    }

    @Override
    LinkedHashMap<String, Object> addData(String draftId, String source, String mimeType, String encoding, String graph) throws DrafterException {
        // Let's ensure that the file we've been told about actually exists.
        // This is to ensure we at least deleteGraphsForJob that the path provided to the function is acceptable.
        if (!(new File(source).exists())) {
            throw new IllegalArgumentException("File '${source}' does not exist.")
        }
        [:]
    }

    @Override
    LinkedHashMap<String, Object> findDraftset(String displayName, Drafter.Include include) throws DrafterException {
        [
                "id": "7F2AF1C2-3957-4DC8-A001-1A9071AE1E5C"
        ]
    }

    @Override
    LinkedHashMap<String, Object> submitDraftsetTo(String id, Drafter.Role role, String user) throws DrafterException {
        [:]
    }

    @Override
    LinkedHashMap<String, Object> claimDraftset(String id) throws DrafterException {
        [:]
    }

    @Override
    LinkedHashMap<String, Object> publishDraftset(String id) throws DrafterException {
        [:]
    }

    @Override
    URI getDraftsetEndpoint(String id, DrafterAction action) {
        new URI("http://example.org/${id}")
    }

    @Override
    Object query(String id, String query, Boolean unionWithLive,
                                                 Integer timeout, String accept) throws DrafterException {
        def retVal = new Expando()
        if (query.contains(Job.catalogEntryGraphIdentifier)) {
            def catalogEntryGraph = new Expando()
            String graphUri = "https://gss-data.org.uk/graph/some-graph-uri"
            catalogEntryGraph.value = graphUri

            def binding = new Expando()
            binding.catalogEntryGraph = catalogEntryGraph

            def result = new Expando()
            result.bindings = [binding]

            retVal.results = [result]
        } else {
            retVal.results = []
        }

        return retVal
    }

    void update(String draftId, String query, Integer timeout = null) {}

    @Override
    String getToken() {
        "7763FFBB-5E60-4BFD-8EF1-00B153E0CC83"
    }
}
