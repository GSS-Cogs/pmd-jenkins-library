package uk.org.floop.jenkins_pmd

import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import com.github.tomakehurst.wiremock.stubbing.Scenario
import hudson.FilePath
import jenkins.model.Jenkins
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.lang.StreamRDFCounting
import org.apache.jena.riot.system.ErrorHandlerFactory
import org.apache.jena.riot.system.StreamRDFCountingBase
import org.apache.jena.riot.system.StreamRDFLib
import org.jenkinsci.lib.configprovider.ConfigProvider
import org.jenkinsci.plugins.configfiles.ConfigFileStore
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles
import org.jenkinsci.plugins.configfiles.custom.CustomConfig
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule

import static com.github.tomakehurst.wiremock.client.WireMock.*

class DrafterTests {

    @Rule
    public JenkinsRule rule = new JenkinsRule()

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(WireMockConfiguration.options()
            .dynamicPort()
    //.port(8123)
            .usingFilesUnderClasspath("test/resources")
            .notifier(new ConsoleNotifier(true))
    )

    @Rule
    public WireMockClassRule instanceRule = wireMockRule

    @ClassRule
    public static WireMockClassRule oauthWireMockRule = new WireMockClassRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("test/resources")
    )

    @Rule
    public WireMockClassRule oauthRule = oauthWireMockRule


    @Before
    void configureGlobalGitLibraries() {
        RuleBootstrapper.setup(rule)
    }

    @Before
    void setupConfigFile() {
        GlobalConfigFiles globalConfigFiles = rule.jenkins
                .getExtensionList(ConfigFileStore.class)
                .get(GlobalConfigFiles.class)
        CustomConfig.CustomConfigProvider configProvider = rule.jenkins
                .getExtensionList(ConfigProvider.class)
                .get(CustomConfig.CustomConfigProvider.class)
        globalConfigFiles.save(
                new CustomConfig("pmd", "config.json", "Details of endpoint URLs and credentials", """{
  "pmd_api": "http://localhost:${wireMockRule.port()}",
  "oauth_token_url": "http://localhost:${oauthWireMockRule.port()}/oauth/token",
  "oauth_audience": "jenkins",
  "credentials": "onspmd4",
  "default_mapping": "https://github.com/ONS-OpenData/ref_trade/raw/master/columns.csv",
  "base_uri": "http://gss-data.org.uk"
}""", configProvider.getProviderId()))

    }

    @Before
    void setupEnvVariables() {

    }

    @Before
    void setupCredentials() {
        StandardUsernamePasswordCredentials key = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                "onspmd4", "Access to PMD APIs",
                "client_id", "client_secret")
        SystemCredentialsProvider.getInstance().getCredentials().add(key)
        SystemCredentialsProvider.getInstance().save()
    }

    @Test
    void "credentials and endpoint URLs stored outside library"() {
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            configFileProvider([configFile(fileId: 'pmd', variable: 'configfile')]) {
                def config = readJSON(text: readFile(file: configfile))
                String PMD = config['pmd_api']
                String credentials = config['credentials']
                echo "API URL = <$PMD>"
                withCredentials([usernamePassword(credentialsId: credentials, usernameVariable: 'client_id', passwordVariable: 'client_secret')]) {
                    echo "ID = <$client_id>"
                    echo "Secret = <$client_secret>"
                }
             }
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        rule.assertLogContains('API URL = <http', firstResult)
        rule.assertLogContains('ID = <', firstResult)
        rule.assertLogContains('Secret = <', firstResult)
    }

    @Before
    void setUpOAuthMock() {
        oauthRule.stubFor(post("/oauth/token")
                .withHeader("Content-Type", matching("application/x-www-form-urlencoded.*UTF.*"))
                .withHeader("Accept", equalTo("application/json"))
        /*                .withRequestBody(equalToJson("""
    {
      "audience": "jenkins",
      "grant_type": "client_credentials",
      "client_id": "client_id",
      "client_secret": "client_secret"
    }""")) */
                .willReturn(ok()
                        .withHeader("Contenty-Type", "application/json")
                        .withBodyFile("accessToken.json")
                ))

    }

    @Test
    void "listDraftsets"() {
        oauthRule.resetRequests()
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("listDraftsets.json")
                        .withHeader("Content-Type", "application/json")))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            def pmd = pmdConfig("pmd")
            echo pmd.drafter.listDraftsets()[0].id
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        oauthRule.verify(postRequestedFor(urlEqualTo("/oauth/token")))
        instanceRule.verify(getRequestedFor(urlEqualTo("/v1/draftsets")))
        rule.assertLogContains('de305d54-75b4-431b-adb2-eb6b9e546014', firstResult)
    }

    @Test
    void "uploadDataset"() {
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("listDraftsets.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("newDraftset.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(delete("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withBodyFile("deleteJob.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(delete(urlMatching("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/graph.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("deleteGraph.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withBodyFile("addDataJob.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse().withStatus(404).withBodyFile('notFinishedJob.json'))
                .willSetStateTo("Finished"))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs("Finished")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("finishedJobOk.json")))
        instanceRule.stubFor(get("/columns.csv").willReturn(ok().withBodyFile('columns.csv')))
        instanceRule.stubFor(get('/v1/status/finished-jobs/4fc9ad42-f964-4f56-a1ab-a00bd622b84c')
                .withHeader('Accept', equalTo('application/json'))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok().withBodyFile('finishedImportJobOk.json')))

        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            dir('out') {
              writeFile file:'dataset.trig', text:'dummy:data'
              writeFile file:'observations.ttl', text:'Dummy turtle'
            }
            def pmd = pmdConfig("pmd")
            pmd.drafter
                    .listDraftsets()
                    .findAll { it['display-name'] == env.JOB_NAME }
                    .each {
                        pmd.drafter.deleteDraftset(it.id)
                    }
            String id = pmd.drafter.createDraftset(env.JOB_NAME).id
            pmd.drafter.addData(
                    id,
                    "${WORKSPACE}/out/observations.ttl",
                    "text/turtle",
                    "UTF-8"
            )
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
    }

    @Test
    void "publish draftset"() {
        instanceRule.stubFor(post(urlMatching("/v1/draftset/.*/publish"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withBodyFile("publicationJob.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("listDraftsets.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get('/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400')
                .withHeader('Accept', equalTo('application/json'))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok().withBodyFile('finishedPublicationJobOk.json')))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            pmd = pmdConfig("pmd")
            String draftId = pmd.drafter.findDraftset(env.JOB_NAME).id
            pmd.drafter.publishDraftset(draftId)
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        instanceRule.verify(postRequestedFor(urlEqualTo("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/publish")))
    }

    @Test
    void "replace non-existant draftset"() {
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("listDraftsetsWithoutProject.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("newDraftset.json")
                        .withHeader("Content-Type", "application/json")))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            def pmd = pmdConfig("pmd")
            pmd.drafter
                    .listDraftsets()
                    .findAll { it['display-name'] == env.JOB_NAME }
                    .each {
                        pmd.drafter.deleteDraftset(it.id)
                    }
            String id = pmd.drafter.createDraftset(env.JOB_NAME).id
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
    }

    @Test
    void "publish blocks writes"() {
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse().withStatus(503)))
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .inScenario("Write lock")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("listDraftsets.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("newDraftset.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(delete("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse().withStatus(503)))
        instanceRule.stubFor(delete("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .inScenario("Write lock")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withBodyFile("deleteJob.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(delete(urlMatching("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/graph.*"))
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse().withStatus(503)))
        instanceRule.stubFor(delete(urlMatching("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/graph.*"))
                .inScenario("Write lock")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("deleteGraph.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data")
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse().withStatus(503)))
        instanceRule.stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data")
                .inScenario("Write lock")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withBodyFile("addDataJob.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(aResponse().withStatus(404).withBodyFile('notFinishedJob.json'))
                .willSetStateTo("Finished"))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs("Finished")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("finishedJobOk.json")))
        instanceRule.stubFor(get('/v1/status/finished-jobs/4fc9ad42-f964-4f56-a1ab-a00bd622b84c')
                .withHeader('Accept', equalTo('application/json'))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok().withBodyFile('finishedImportJobOk.json')))
        instanceRule.stubFor(get('/v1/status/writes-locked')
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader('Accept', equalTo('application/json'))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok().withBody('false'))
                .willSetStateTo('Still Publishing'))
        instanceRule.stubFor(get('/v1/status/writes-locked')
                .inScenario("Write lock")
                .whenScenarioStateIs('Still publishing')
                .withHeader('Accept', equalTo('application/json'))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok().withBody('true'))
                .willSetStateTo('Published'))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            dir('out') {
              writeFile file:'dataset.trig', text:'dummy:data'
              writeFile file:'observations.ttl', text:'Dummy turtle'
            }
            def pmd = pmdConfig("pmd")
            pmd.drafter
                    .listDraftsets()
                    .findAll { it['display-name'] == env.JOB_NAME }
                    .each {
                        pmd.drafter.deleteDraftset(it.id)
                    }
            String id = pmd.drafter.createDraftset(env.JOB_NAME).id
            pmd.drafter.addData(
                    id,
                    "${WORKSPACE}/out/observations.ttl",
                    "text/turtle",
                    "UTF-8"
            )
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
    }

    @Test
    void "addCompressedData"() {
        instanceRule.stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data?graph=some-graph")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .withHeader("Content-Encoding", equalTo("gzip"))
                .withRequestBody(matching(".*prefix.*<[^ ]+>.*"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withBodyFile("addDataJob.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("finishedJobOk.json")))

        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            def pmd = pmdConfig("pmd")
            pmd.drafter.addData(
                "4e376c57-6816-404a-8945-94849299f2a0",
                "${WORKSPACE}/out/observations.ttl.gz",
                "text/turtle",
                "UTF-8",
                "some-graph"
            )
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow
        FilePath workspace = rule.jenkins.getWorkspaceFor(workflowJob)
        FilePath compressedTurtle = workspace.child("out").child("observations.ttl.gz")
        compressedTurtle.copyFrom(new FileInputStream(new File("test/resources/__files/observations.ttl.gz")))

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        rule.assertLogContains('SUCCESS', firstResult)
    }

    @Test
    void "jobID"() {
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            echo "Unique id: ${util.getJobID()}"
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        rule.assertLogContains('Unique id: ', firstResult)
        rule.assertLogContains('SUCCESS', firstResult)
        assert rule.getLog(firstResult) =~ /Unique id: [^ ]+/
    }

    @Test
    void "job PROV"() {
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("listDraftsetsWithoutProject.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("newDraftset.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data?graph=http%3A%2F%2Fbar.com")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .withRequestBody(matching(".*prefix.*<[^ ]+>.*"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withBodyFile("addDataJob.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("finishedJobOk.json")))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            def pmd = pmdConfig("pmd")
            String id = pmd.drafter.createDraftset(env.JOB_NAME).id
            writeFile(file: "${WORKSPACE}/out/datasetPROV.ttl", text: util.jobPROV("http://example.com"))
            pmd.drafter.addData(
                id,
                "${WORKSPACE}/out/datasetPROV.ttl",
                "text/turtle",
                "UTF-8",
                "http://bar.com"
            )

        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        rule.assertLogContains('SUCCESS', firstResult)
        FilePath workspace = rule.jenkins.getWorkspaceFor(workflowJob)
        FilePath provTurtle = workspace.child("out").child("datasetPROV.ttl")
        def counter = StreamRDFLib.count()
        RDFParser
                .create()
                .source(provTurtle.read())
                .lang(RDFLanguages.TURTLE)
                .errorHandler(ErrorHandlerFactory.errorHandlerStrict)
                .parse(counter)
        assert counter.count() > 0
    }

    @Test
    void "list graphs associated with this job"() {
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("listDraftsetsWithoutProject.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("newDraftset.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(post("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/query?union-with-live=true")
                .withHeader("Accept", equalTo("application/sparql-results+json"))
                .withHeader("Authorization", equalTo("Bearer eyJz93a...k4laUWw"))
                .willReturn(ok()
                        .withBodyFile("sparql-results-graphs.json")
                        .withHeader("Content-Type", "application/json")))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            def pmd = pmdConfig("pmd")
            String id = pmd.drafter.createDraftset(env.JOB_NAME).id
            echo "Job graphs: ${util.jobGraphs(pmd, id)}"
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        rule.assertLogContains('SUCCESS', firstResult)
        rule.assertLogContains('http://example.org/data-graph', firstResult)
    }
}