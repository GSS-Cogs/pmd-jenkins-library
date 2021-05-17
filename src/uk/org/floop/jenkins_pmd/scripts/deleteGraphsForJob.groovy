package uk.org.floop.jenkins_pmd.scripts

import groovy.json.JsonSlurper
import org.codehaus.groovy.tools.shell.util.Logger
import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.Job
import uk.org.floop.jenkins_pmd.PMD
import uk.org.floop.jenkins_pmd.PMDConfig

private PMDConfig getPmdConfig(String json){
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

/**
 * Begin Configuration Section.
 */
def jobId = "HMRC-regional-trade-statistics"

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


def l = Logger.create deleteGraphsForJob.class
l.warn("Starting")

def pmd = new PMD(getPmdConfig(pmdConfigJson), Secrets.clientId, Secrets.clientSecret)
def drafter = new Drafter(pmd)

Job.deleteAllGraphsCreatedByJob(drafter, jobId)

l.warn("Finished")
