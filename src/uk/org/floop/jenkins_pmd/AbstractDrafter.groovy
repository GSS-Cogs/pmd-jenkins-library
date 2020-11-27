package uk.org.floop.jenkins_pmd

/**
 * This abstract base class allows us to mock the `Drafter` class in integration tests.
 * It's an abstract class rather than an interface as this will allow me to include the default params.
 */
abstract class AbstractDrafter {
    abstract Collection<Dictionary<String, Object>> listDraftsets(Drafter.Include include = Drafter.Include.ALL)
    abstract Dictionary<String, Object> createDraftset(String label) throws DrafterException
    abstract Dictionary<String, Object> deleteGraph(String draftsetId, String graph) throws DrafterException
    abstract Dictionary<String, Object> deleteDraftset(String draftsetId) throws DrafterException
    abstract Dictionary<String, Object> addData(String draftId, String source, String mimeType, String encoding, String graph = null) throws DrafterException
    abstract Dictionary<String, Object> findDraftset(String displayName, Drafter.Include include = Drafter.Include.ALL) throws DrafterException
    abstract Dictionary<String, Object> submitDraftsetTo(String id, Drafter.Role role, String user) throws DrafterException
    abstract Dictionary<String, Object> publishDraftset(String id) throws DrafterException
    abstract URI getDraftsetEndpoint(String id)
    abstract Collection<Dictionary<String, Object>> query(String id, String query, Boolean unionWithLive = false,
                          Integer timeout = null, String accept = "application/sparql-results+json") throws DrafterException
    abstract String getToken()
}