package uk.org.floop.jenkins_pmd

import sun.reflect.generics.reflectiveObjects.NotImplementedException

/**
 * It's unpleasant to have place this class outside of the integrationtest library, but we can't directly reference
 * the project from the integration tests due to Jenkins then complaining that the pipeline isn't CSC compliant.
 */
class MockDrafter extends AbstractDrafter {

    MockDrafter(PMD pmd) {}

    @Override
    Collection<Dictionary<String, Object>> listDraftsets(Drafter.Include include) {
        []
    }

    @Override
    Dictionary<String, Object> createDraftset(String label) throws DrafterException {
        [
                "id": "7F2AF1C2-3957-4DC8-A001-1A9071AE1E5C"
        ]
    }

    @Override
    Dictionary<String, Object> deleteGraph(String draftsetId, String graph) throws DrafterException {
        [:]
    }

    @Override
    Dictionary<String, Object> deleteDraftset(String draftsetId) throws DrafterException {
        [:]
    }

    @Override
    Dictionary<String, Object> addData(String draftId, String source, String mimeType, String encoding, String graph) throws DrafterException {
        [:]
    }

    @Override
    Dictionary<String, Object> findDraftset(String displayName, Drafter.Include include) throws DrafterException {
        [
                "id": "7F2AF1C2-3957-4DC8-A001-1A9071AE1E5C"
        ]
    }

    @Override
    Dictionary<String, Object> submitDraftsetTo(String id, Drafter.Role role, String user) throws DrafterException {
        [:]
    }

    @Override
    Dictionary<String, Object> publishDraftset(String id) throws DrafterException {
        [:]
    }

    @Override
    URI getDraftsetEndpoint(String id) {
        new URI("http://example.org/${id}")
    }

    @Override
    Collection<Dictionary<String, Object>> query(String id, String query, Boolean unionWithLive,
                                                 Integer timeout, String accept) throws DrafterException {
        return []
    }

    @Override
    String getToken() {
        "7763FFBB-5E60-4BFD-8EF1-00B153E0CC83"
    }
}