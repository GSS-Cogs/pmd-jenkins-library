package uk.org.floop.jenkins_pmd

import uk.org.floop.jenkins_pmd.enums.DrafterAction

/**
 * This abstract base class allows us to mock the `Drafter` class in integration tests.
 * It's an abstract class rather than an interface as this will allow me to include the default params.
 */
abstract class AbstractDrafter {
    abstract Collection<LinkedHashMap<String, Object>> listDraftsets(Drafter.Include include = Drafter.Include.ALL)
    abstract LinkedHashMap<String, Object> createDraftset(String label) throws DrafterException
    abstract LinkedHashMap<String, Object> deleteGraph(String draftsetId, String graph) throws DrafterException
    abstract LinkedHashMap<String, Object> deleteDraftset(String draftsetId) throws DrafterException
    abstract LinkedHashMap<String, Object> addData(String draftId, String source, String mimeType, String encoding, String graph = null) throws DrafterException
    abstract LinkedHashMap<String, Object> findDraftset(String displayName, Drafter.Include include = Drafter.Include.ALL) throws DrafterException
    abstract LinkedHashMap<String, Object> submitDraftsetTo(String id, Drafter.Role role, String user) throws DrafterException
    abstract LinkedHashMap<String, Object> claimDraftset(String id) throws DrafterException
    abstract LinkedHashMap<String, Object> publishDraftset(String id) throws DrafterException
    abstract URI getDraftsetEndpoint(String id, DrafterAction action)
    abstract Object query(String id, String query, Boolean unionWithLive = false,
                          Integer timeout = null, String accept = "application/sparql-results+json") throws DrafterException
    abstract void update(String draftId, String query, Integer timeout = null)
            throws DrafterException
    abstract String getToken()
}