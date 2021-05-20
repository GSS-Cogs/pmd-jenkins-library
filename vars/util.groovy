import uk.org.floop.jenkins_pmd.CatalogMetadataHelper
import uk.org.floop.jenkins_pmd.ConceptScheme
import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.Job
import uk.org.floop.jenkins_pmd.PMD
import uk.org.floop.jenkins_pmd.SparqlQueries
import uk.org.floop.jenkins_pmd.SparqlQuery
import uk.org.floop.jenkins_pmd.models.CatalogMetadata
import org.apache.http.client.fluent.Request


String slugise(String string) {
  string.toLowerCase()
        .replaceAll('[^\\w/]', '-')
        .replaceAll('-+', '-')
        .replaceAll('-\$', '')
}

String getJobID() {
  Job.getID(currentBuild)
}

List<String> jobGraphs(PMD pmd, String draftId) {
  Job.graphs(currentBuild, pmd, draftId)
}

String jobPROV(String graph) {
  Job.getPROV(currentBuild, graph)
}

String getSparqlInsertAllGraphsProv() {
  Job.getSparqlInsertAllGraphsProv(currentBuild)
}

List<String> referencedGraphs(PMD pmd, String draftId) {
  Job.referencedGraphs(pmd, draftId, isAccretiveUpload())
}

String conceptSchemeMetadata(String csvFile, String baseURI, String id, String label) {
  def cs = new ConceptScheme(csvFile: csvFile, baseURI: baseURI, id: id, label: label)
  cs.metadata()
}

String getSparqlQuery(SparqlQuery queryType, boolean insertsRequired = false){
  SparqlQueries.getSparqlQuery(queryType, insertsRequired)
}

String getCatalogMetadata(String graph, CatalogMetadata metadata){
  CatalogMetadataHelper.getCatalogMetadata(graph, metadata)
}

String getUrlAsText(String url, String acceptMimeType = null) {
  def request = Request.Get(url)

  if (acceptMimeType != null){
    request = request.addHeader('Accept', acceptMimeType)
  }

  request.execute().returnContent().asString()
}

void outputJUnitResults() {
  if (!Boolean.parseBoolean(env.SUPPRESS_JUNIT)) {
    junit allowEmptyResults: true, testResults: 'reports/**/*.xml'
  }
}

void publishDraftset() {
  def pmd = pmdConfig("pmd")
  def drafter = pmd.drafter
  String draftId = drafter.findDraftset(env.JOB_NAME, Drafter.Include.OWNED).id
  drafter.publishDraftset(draftId)
}

boolean isAccretiveUpload() {
  def infoJsonPath = "${DATASET_DIR}/info.json"
  def info = readJSON(text: readFile(file: infoJsonPath))
  if (info.containsKey('load') && info['load'].containsKey('accretiveUpload')) {
    return info['load']['accretiveUpload']
  }

  return false
}

boolean hasCmdOutputs() {
  def cmdOutputPath = "cmd-out"
  if cmdOutputPath.exists() {
    return true
  }

  return false
}

String getCatalogGraphForDataSet(String draftId, String dataSetUri) {
  Job.getCatalogEntryGraphForDataSet(pmdConfig("pmd"), draftId, dataSetUri)
}

String getCatalogEntryTriplesToAdd(Map config) {
  String draftId = config.get("draftId")
  String dataSetUri = config.get("dataSetUri")
  String[] dataSetGraphUris = config.get("expectedGraphs")
  Job.getCatalogEntryTriplesToAdd(pmdConfig("pmd"), draftId, dataSetUri, dataSetGraphUris)
}

void incrementallyDeleteAndPublishAllGraphsForJob() {
  def pmd = pmdConfig("pmd")
  def drafter = pmd.drafter

  Job.deleteAllGraphsCreatedByJob(drafter, getJobID())
}