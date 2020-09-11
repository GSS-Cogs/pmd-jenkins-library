import uk.org.floop.jenkins_pmd.Job
import uk.org.floop.jenkins_pmd.PMD

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