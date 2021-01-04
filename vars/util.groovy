import uk.org.floop.jenkins_pmd.CatalogMetadataHelper
import uk.org.floop.jenkins_pmd.ConceptScheme
import uk.org.floop.jenkins_pmd.Job
import uk.org.floop.jenkins_pmd.PMD
import uk.org.floop.jenkins_pmd.SparqlQueries
import uk.org.floop.jenkins_pmd.SparqlQuery
import uk.org.floop.jenkins_pmd.models.CatalogMetadata

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

List<String> referencedGraphs(PMD pmd, String draftId) {
  Job.referencedGraphs(currentBuild, pmd, draftId)
}

String conceptSchemeMetadata(String csvFile, String baseURI, String id, String label) {
  def cs = new ConceptScheme(csvFile: csvFile, baseURI: baseURI, id: id, label: label)
  cs.metadata()
}

String getSparqlQuery(SparqlQuery queryType){
  SparqlQueries.getSparqlQuery(queryType)
}

String getCatalogMetadata(CatalogMetadata metadata){
  CatalogMetadataHelper.getCatalogMetadata(metadata)
}