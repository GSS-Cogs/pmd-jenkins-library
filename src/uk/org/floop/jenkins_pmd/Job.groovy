package uk.org.floop.jenkins_pmd

import org.codehaus.groovy.tools.shell.util.Logger
import org.jenkinsci.plugins.uniqueid.IdStore
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

import java.time.Instant

class Job {
    static Logger logger = Logger.create(Job.class)
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

    static String getSparqlInsertAllGraphsProv(RunWrapper build) {
        String jobId = getID(build)
        String generatedAt = Instant.ofEpochMilli(build.timeInMillis).toString()
        return """
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX gdp: <http://gss-data.org.uk/def/gdp#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

INSERT {
    GRAPH ?g1 {
        ?g1 prov:wasGeneratedBy <${build.absoluteUrl}> .
        <${build.absoluteUrl}> a prov:Activity ;
          prov:wasAssociatedWith <${build.rawBuild.parent.absoluteUrl}> ;
          prov:generatedAtTime "${generatedAt}"^^xsd:dateTime ;
          rdfs:label "${build.fullDisplayName}" .
        <${build.rawBuild.parent.absoluteUrl}> a prov:Agent ;
          gdp:uniqueID "${jobId}" ;
          rdfs:label "${build.rawBuild.parent.fullDisplayName}" .
    }
}
WHERE {
    GRAPH ?g1 {
        ?s ?p ?o.
    }
}
"""
    }

    static String catalogEntryGraphIdentifier = "DataSetUri -> catalogEntry graph"
    static String getCatalogEntryGraphForDataSet(PMD pmd, String draftId, String dataSetUri) {
        def result = pmd.drafter.query(draftId, """
            # ${catalogEntryGraphIdentifier}
            PREFIX qb: <http://purl.org/linked-data/cube#>
            PREFIX pmdcat: <http://publishmydata.com/pmdcat#>
            SELECT DISTINCT ?catalogEntryGraph
            WHERE {
                BIND(<${dataSetUri}> as ?ds).
                
                ?ds a qb:DataSet.
                GRAPH ?catalogEntryGraph {
                    ?catalogEntry pmdcat:datasetContents ?ds
                }
            }
        """, true)
        String graphUri = result.results.bindings[0].catalogEntryGraph.value
        return graphUri
    }


    static String catalogEntryGraphLinkId = "DataSetUri -> Construct ?catalogEntry pmdcat:graph ?dataSetGraphUri"
    static String getCatalogEntryTriplesToAdd(PMD pmd, String draftId, String dataSetUri,
                                              String[] possiblyNewDataSetGraphUris) {
        String newTriplesToInsert = pmd.drafter.query(draftId, """
            # ${catalogEntryGraphLinkId}
            PREFIX qb: <http://purl.org/linked-data/cube#>
            PREFIX pmdcat: <http://publishmydata.com/pmdcat#>
            
            CONSTRUCT {
                ?catalogEntry pmdcat:graph ?dataSetGraphUri.
            }
            WHERE {              
                BIND(<${dataSetUri}> as ?ds).
                
                ?ds a qb:DataSet.
                ?catalogEntry pmdcat:datasetContents ?ds
                
                VALUES (?dataSetGraphUri) {
                    ${
                        possiblyNewDataSetGraphUris.collect({"(<${it}>)"}).join("\n")
                    }
                }
                
                FILTER NOT EXISTS {
                    ?catalogEntry pmdcat:graph ?dataSetGraphUri.
                }
            } 
        """, true, null, "text/turtle")

        return newTriplesToInsert
    }

    static ArrayList graphs(RunWrapper build, PMD pmd, String draftId) {
        String jobId = getID(build)
        return getDistinctGraphsOwnedByJob(pmd.drafter, draftId, jobId)
    }

    private static ArrayList getDistinctGraphsOwnedByJob(Drafter drafter, String draftId, String jobId) {
        def results = drafter.query(draftId, """
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

    static ArrayList referencedGraphs(PMD pmd, String draftId, boolean isAccretiveUpload) {
        def datasets = pmd.drafter.query(draftId, """
        PREFIX qb: <http://purl.org/linked-data/cube#>
        SELECT DISTINCT ?ds WHERE {
            ${isAccretiveUpload
                ? "[] a qb:Observation; qb:dataSet ?ds."
                : "?ds a qb:DataSet."}            
        }
        """, false).results.bindings.collect { it.ds.value }
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

    /**
     * Creates (and deletes) its own draftset to run the query.
     * @param drafter
     * @param jobId
     * @return
     */
    private static ArrayList getGraphsCreatedByJob(Drafter drafter, String jobId) {
        String draftsetId = drafter.createDraftset("Remove large dataset graph-by-graph.").id
        def distinctGraphs = getDistinctGraphsOwnedByJob(drafter, draftsetId, jobId)
        drafter.deleteDraftset(draftsetId)

        return distinctGraphs
    }


    static void deleteAllGraphsCreatedByJob(Drafter drafter, String jobId) {
        def distinctGraphs = getGraphsCreatedByJob(drafter, jobId)
        if (distinctGraphs.any()) {
            logger.debug("Found graphs ${distinctGraphs.join(", ")} for job ${jobId}")
        } else {
            logger.warn("No graphs found for job ${jobId}.")
        }

        for (def graph : distinctGraphs) {
            String draftsetId = drafter.createDraftset("Remove large dataset graph-by-graph.").id
            logger.warn("Deleting graph ${graph} in draftset ${draftsetId}")
            drafter.deleteGraph(draftsetId, graph)
            drafter.publishDraftset(draftsetId)
            logger.debug("Draftset ${draftsetId} published")
        }

        def remainingGraphs = getGraphsCreatedByJob(drafter, jobId)
        if (remainingGraphs.any()) {
            throw new Exception("Job ${jobId} has remaining graphs ${remainingGraphs.join(", ")}")
        } else {
            logger.debug("No remaining graphs found for job ${jobId}")
        }
    }


}
