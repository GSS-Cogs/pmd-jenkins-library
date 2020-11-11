package uk.org.floop.jenkins_pmd

import org.jenkinsci.plugins.uniqueid.IdStore
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

import java.time.Instant
import java.util.regex.Pattern

class Job {
    static String getID(RunWrapper build) {
        def job = build.rawBuild.parent
        String id = IdStore.getId(job)
//        build.getBuildVariables().get("RepositoryName")
        if (id == null) {
            IdStore.makeId(job)
            id = IdStore.getId(job)
        }
        return id
    }

    static String getPROV(RunWrapper build, String graph) {
        String jobId = getID(build)
        String generatedAt = Instant.ofEpochMilli(build.timeInMillis).toString()

        return """
@prefix prov: <http://www.w3.org/ns/prov#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix gdp: <http://gss-data.org.uk/def/gdp#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .

<${graph}> prov:wasGeneratedBy <${build.absoluteUrl}> .

<${build.absoluteUrl}> a prov:Activity ;
  prov:wasAssociatedWith <${build.rawBuild.parent.absoluteUrl}> ;
  prov:generatedAtTime "${generatedAt}"^^xsd:dateTime ;
  rdfs:label "${build.fullDisplayName}".

<${build.rawBuild.parent.absoluteUrl}> a prov:Agent ;
  gdp:uniqueID "${jobId}" ;
  rdfs:label "${build.rawBuild.parent.fullDisplayName}" .

${getInfoJsonProvidenceSparql(build)}
"""
    }

    private static String getInfoJsonProvidenceSparql(RunWrapper build) {
        return getMaybeInfoJsonLabelAndUrl(build)
                .map({
                    """
                    <${build.absoluteUrl}> prov:wasInfluencedBy <${it.second}>.
                    <${it.second}> a prov:Entity;
                        rdfs:label "${it.first}"@en;
                        foaf:page <${it.second}>.
                """
                })
                .orElse("")
    }

    /**
     * Returns an optional tuple containing (label, url) describing the info.json used in this build.
     * Used to describe the providence of data.
     * @param build
     * @return an optional tuple containing (label, url)
     */
    private static Optional<Tuple2<String, String>> getMaybeInfoJsonLabelAndUrl(RunWrapper build) {
        def buildVariables = build.getBuildVariables()
        // See https://ci.floop.org.uk/env-vars.html/ for full list of available environmental variables.
        String gitCommitHash = buildVariables["GIT_COMMIT"]
        String gitRemoteUrl = buildVariables["GIT_URL"]
                .split(",") // May be multiple listed push/pull remotes
                .first()
        String datasetDir = buildVariables["DATASET_DIR"]

        // Expecting a gitRemoteUrl of the form:
        // "git@github.com:GSS-Cogs/pmd-jenkins-library.git"
        // OR "https://github.com/GSS-Cogs/family-covid-19"
        def regex = new Pattern("^.*?GSS-Cogs/(.?)(.git)?\$")
        def matches = regex.matcher(gitRemoteUrl)

        if (!matches.matches()) {
            error "Could not comprehend git remote URL '${gitRemoteUrl}'."
            return Optional.empty()
        }

        String repoName = matches.group(1);
        String repoBaseUrl = "https://github.com/GSS-Cogs/${repoName}"
        String infoJsonAtCommitUrl = "${repoBaseUrl}/tree/${gitCommitHash}/${datasetDir}/info.json"
        String infoJsonCommitLabel = "info.json inside ${repoName}/${datasetDir}"

        return Optional.of(
                new Tuple2<String, String>(infoJsonCommitLabel, infoJsonAtCommitUrl)
        )
    }

    static List<String> graphs(RunWrapper build, PMD pmd, String draftId) {
        String jobId = getID(build)
        def results = pmd.drafter.query(draftId, """
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX gdp: <http://gss-data.org.uk/def/gdp#>

SELECT DISTINCT ?graph WHERE {
  ?graph prov:wasGeneratedBy [ prov:wasAssociatedWith [ gdp:uniqueID "${jobId}" ] ] .
}
""", true)
        return results.results.bindings.collect {
                it.graph.value
        }
    }

    static List<String> referencedGraphs(RunWrapper build, PMD pmd, String draftId) {
        String jobId = getID(build)
        List<String> jobGraphs = graphs(build, pmd, draftId).unique()
        def datasets = pmd.drafter.query(draftId, """
PREFIX qb: <http://purl.org/linked-data/cube#>
SELECT DISTINCT ?ds WHERE {
    ?ds a qb:DataSet .
}""", false).results.bindings.collect { it.ds.value }
        String dsValues = datasets.collect { "( <" + it + "> )" }.join(' ')
        return pmd.drafter.query(draftId, """
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX qb: <http://purl.org/linked-data/cube#>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
SELECT DISTINCT ?graph WHERE {
  {
    ?ds qb:structure/qb:component/qb:componentProperty ?prop .
    GRAPH ?graph { ?prop rdfs:label ?l }
  } UNION {
    ?ds qb:structure/qb:component/qb:componentProperty/qb:codeList ?cs .
    GRAPH ?graph { ?cs a skos:ConceptScheme }
  }
}
VALUES ( ?ds ) {
  ${dsValues}
}""", true).results.bindings.collect { it.graph.value }
    }

}
