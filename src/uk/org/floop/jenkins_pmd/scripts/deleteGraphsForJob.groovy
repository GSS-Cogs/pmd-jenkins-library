package uk.org.floop.jenkins_pmd.scripts

import groovy.json.JsonSlurper
import groovyjarjarantlr.collections.List
import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.PMD
import uk.org.floop.jenkins_pmd.PMDConfig

PMDConfig getPmdConfig(String json){
    def jsonSlurper = new JsonSlurper()
    def pmdConfigDict = jsonSlurper.parseText(json)
    def pmdConfig = new PMDConfig()
    pmdConfig.pmd_api = pmdConfigDict["pmd_api"]
    pmdConfig.oauth_token_url = pmdConfigDict["oauth_token_url"]
    pmdConfig.oauth_audience = pmdConfigDict["oauth_audience"]
    pmdConfig.credentials = pmdConfigDict["credentials"]
    pmdConfig.default_mapping = pmdConfigDict["default_mapping"]
    pmdConfig.base_uri = pmdConfigDict["base_uri"]
    pmdConfig.test_timeout = pmdConfigDict["test_timeout"]
    pmdConfig.pmd_public_sparql_endpoint = pmdConfigDict["pmd_public_sparql_endpoint"]
    return pmdConfig
}

private List getDistinctGraphsForJenkinsJobId(Drafter drafter, String jobId) {
    String draftsetId = drafter.createDraftset("Remove large dataset graph-by-graph.").id

    def graphResults = drafter.query(draftsetId, """
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX gdp: <http://gss-data.org.uk/def/gdp#>

SELECT DISTINCT ?graph WHERE {
  ?graph prov:wasGeneratedBy [ prov:wasAssociatedWith [ gdp:uniqueID "${jobId}" ] ] .
}
""", true)

    List distinctGraphs = graphResults.results.bindings.collect {
        it.graph.value
    }

    drafter.deleteDraftset(draftsetId)
    distinctGraphs
}

private void deleteAllGraphsCreatedByJob(Drafter drafter, String jobId) {
    List distinctGraphs = getDistinctGraphsForJenkinsJobId(drafter, jobId)

    if (distinctGraphs.any()) {
        println "Found graphs ${distinctGraphs.join(", ")} for job ${jobId}"
    } else {
        println "No graphs found for job ${jobId}."
    }

    for (def graph : distinctGraphs) {
        String draftsetId = drafter.createDraftset("Remove large dataset graph-by-graph.").id
        println "Deleting graph ${graph} in draftset ${draftsetId}"
        drafter.deleteGraph(draftsetId, graph)
        drafter.publishDraftset(draftsetId)
        println "Draftset ${} published"
    }

    List remainingGraphs = getDistinctGraphsForJenkinsJobId(drafter, jobId)
    if (remainingGraphs.any()) {
        throw new Exception("Job ${jobId} has remaining graphs ${remainingGraphs.join(", ")}")
    } else {
        println "No remaining graphs found for job ${jobId}"
    }
}

/**
 * Begin Configuration Section.
 */
def jobId = ""

def pmdConfigJson = """{
  "pmd_api": "https://cogs-staging-drafter.publishmydata.com/",
  "oauth_token_url": "https://swirrl-staging.eu.auth0.com/oauth/token",
  "oauth_audience": "https://pmd",
  "credentials": "onspmd4",
  "default_mapping": "https://gss-cogs.github.io/ref_trade/columns.csv",
  "base_uri": "http://gss-data.org.uk",
  "test_timeout": 5,
  "pmd_public_sparql_endpoint": "https://staging.gss-data.org.uk/sparql"
}"""

/**
 * End Configuration Section
 */


println "Starting"

def pmd = new PMD(getPmdConfig(pmdConfigJson), Secrets.clientId, Secrets.clientSecret)
def drafter = new Drafter(pmd)

deleteAllGraphsCreatedByJob(drafter, jobId)

println "Finished"
