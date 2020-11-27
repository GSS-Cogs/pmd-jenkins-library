package uk.org.floop.jenkins_pmd

import org.jenkinsci.plugins.uniqueid.IdStore
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

import java.time.Instant

class Job {
    static String getID(RunWrapper build) {
        def job = build.rawBuild.parent
        String id = IdStore.getId(job)
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

<${graph}> prov:wasGeneratedBy <${build.absoluteUrl}> .
<${build.absoluteUrl}> a prov:Activity ;
  prov:wasAssociatedWith <${build.rawBuild.parent.absoluteUrl}> ;
  prov:generatedAtTime "${generatedAt}"^^xsd:dateTime ;
  rdfs:label "${build.fullDisplayName}" .
<${build.rawBuild.parent.absoluteUrl}> a prov:Agent ;
  gdp:uniqueID "${jobId}" ;
  rdfs:label "${build.rawBuild.parent.fullDisplayName}" .
"""
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
