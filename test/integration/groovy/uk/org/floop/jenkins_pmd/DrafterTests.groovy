package uk.org.floop.jenkins_pmd

import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import com.github.tomakehurst.wiremock.stubbing.Scenario
import hudson.FilePath
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
    )

    @Rule
    public WireMockClassRule instanceRule = wireMockRule

    @ClassRule
    public static WireMockClassRule cacheWireMockRule = new WireMockClassRule(WireMockConfiguration.options()
            .dynamicPort()
    )

    @Rule
    public WireMockClassRule cacheRule = cacheWireMockRule

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
  "credentials": "onspmd",
  "pipeline_api": "http://localhost:${wireMockRule.port()}",
  "default_mapping": "https://github.com/ONS-OpenData/ref_trade/raw/master/columns.csv",
  "base_uri": "http://gss-data.org.uk",
  "empty_cache": "http://localhost:${cacheWireMockRule.port()}/_clear_cache",
  "sync_search": "http://localhost:${cacheWireMockRule.port()}/_sync_search",
  "cache_credentials": "cachepmd"
}""", configProvider.getProviderId()))
    }

    @Before
    void setupCredentials() {
        StandardUsernamePasswordCredentials key = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                "onspmd", "Access to PMD APIs", "admin", "admin")
        SystemCredentialsProvider.getInstance().getCredentials().add(key)
        StandardUsernamePasswordCredentials cacheKey = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                "cachepmd", "Access to PMD cache buster", "cache", "cache")
        SystemCredentialsProvider.getInstance().getCredentials().add(cacheKey)
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
                withCredentials([usernamePassword(credentialsId: credentials, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    echo "User = <$USER>"
                    echo "Pass = <$PASS>"
                }
             }
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        rule.assertLogContains('API URL = <http', firstResult)
        rule.assertLogContains('User = <', firstResult)
        rule.assertLogContains('Pass = <', firstResult)
    }

    @Test
    void "listDraftsets"() {
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
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
        instanceRule.verify(getRequestedFor(urlEqualTo("/v1/draftsets")))
        rule.assertLogContains('de305d54-75b4-431b-adb2-eb6b9e546014', firstResult)
    }

    @Test
    void "uploadTidy"() {
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                    .withBodyFile("listDraftsets.json")
                    .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                    .withBodyFile("newDraftset.json")
                    .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(delete("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                    .withStatus(202)
                    .withBodyFile("deleteJob.json")
                    .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(delete(urlMatching("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/graph.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBodyFile("deleteGraph.json")
                    .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                    .withStatus(202)
                    .withBodyFile("addDataJob.json")
                    .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse().withStatus(404).withBodyFile('notFinishedJob.json'))
                .willSetStateTo("Finished"))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs("Finished")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                    .withBodyFile("finishedJobOk.json")))
        instanceRule.stubFor(get("/columns.csv").willReturn(ok().withBodyFile('columns.csv')))
        instanceRule.stubFor(post("/v1/pipelines/ons-table2qb.core/data-cube/import")
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
/*                .withMultipartRequestBody(
                    aMultipart()
                            .withName('observations-csv')
                            .withHeader('Content-Type', equalTo('text/csv'))
                            .withBody(equalTo('Dummy,CSV'))) */
                .willReturn(aResponse().withStatus(202).withBodyFile('cubeImportJob.json')))
        instanceRule.stubFor(get('/v1/status/finished-jobs/4fc9ad42-f964-4f56-a1ab-a00bd622b84c')
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
                .willReturn(ok().withBodyFile('finishedImportJobOk.json')))

        final CpsFlowDefinition flow = new CpsFlowDefinition("""
        node {
            dir('out') {
              writeFile file:'dataset.trig', text:'dummy:data'
              writeFile file:'observations.csv', text:'Dummy,CSV'
            }
            jobDraft.replace()
            uploadTidy(['out/observations.csv'],
                       '${wireMockRule.baseUrl()}/columns.csv')
        }""".stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        instanceRule.verify(postRequestedFor(urlEqualTo('/v1/pipelines/ons-table2qb.core/data-cube/import'))
                .withHeader('Accept', equalTo('application/json')))

    }

    @Test
    void "publish draftset"() {
        instanceRule.stubFor(post(urlMatching("/v1/draftset/.*/publish"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                    .withStatus(202)
                    .withBodyFile("publicationJob.json")
                    .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                .withBodyFile("listDraftsets.json")
                .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get('/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400')
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
                .willReturn(ok().withBodyFile('finishedPublicationJobOk.json')))
        cacheRule.stubFor(get('/_clear_cache').withBasicAuth('cache', 'cache').willReturn(ok()))
        cacheRule.stubFor(get('/_sync_search').withBasicAuth('cache', 'cache').willReturn(ok()))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            jobDraft.publish()
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        instanceRule.verify(postRequestedFor(urlEqualTo("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/publish")))
        rule.assertLogContains('Publishing job draft', firstResult)
    }

    @Test
    void "replace non-existant draftset"() {
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                .withBodyFile("listDraftsetsWithoutProject.json")
                .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                .withBodyFile("newDraftset.json")
                .withHeader("Content-Type", "application/json")))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            jobDraft.replace()
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        //instanceRule.verify(postRequestedFor(urlEqualTo("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/publish")))
        rule.assertLogContains('no job draft to delete', firstResult)
    }

    @Test
    void "publish blocks writes"() {
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse().withStatus(503)))
        instanceRule.stubFor(post("/v1/draftsets?display-name=project")
                .inScenario("Write lock")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        instanceRule.stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                .withBodyFile("listDraftsets.json")
                .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                .withBodyFile("newDraftset.json")
                .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(delete("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse().withStatus(503)))
        instanceRule.stubFor(delete("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .inScenario("Write lock")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                .withStatus(202)
                .withBodyFile("deleteJob.json")
                .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(delete(urlMatching("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/graph.*"))
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse().withStatus(503)))
        instanceRule.stubFor(delete(urlMatching("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/graph.*"))
                .inScenario("Write lock")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                .withStatus(200)
                .withBodyFile("deleteGraph.json")
                .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data")
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse().withStatus(503)))
        instanceRule.stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data")
                .inScenario("Write lock")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                .withStatus(202)
                .withBodyFile("addDataJob.json")
                .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse().withStatus(404).withBodyFile('notFinishedJob.json'))
                .willSetStateTo("Finished"))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs("Finished")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                .withBodyFile("finishedJobOk.json")))
        instanceRule.stubFor(get("/columns.csv").willReturn(ok().withBodyFile('columns.csv')))
        instanceRule.stubFor(post("/v1/pipelines/ons-table2qb.core/data-cube/import")
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
                .willReturn(aResponse().withStatus(503)))
        instanceRule.stubFor(post("/v1/pipelines/ons-table2qb.core/data-cube/import")
                .inScenario("Write lock")
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
                .willReturn(aResponse().withStatus(202).withBodyFile('cubeImportJob.json')))
        instanceRule.stubFor(get('/v1/status/finished-jobs/4fc9ad42-f964-4f56-a1ab-a00bd622b84c')
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
                .willReturn(ok().withBodyFile('finishedImportJobOk.json')))
        instanceRule.stubFor(get('/v1/status/writes-locked')
                .inScenario("Write lock")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
                .willReturn(ok().withBody('false'))
                .willSetStateTo('Still Publishing'))
        instanceRule.stubFor(get('/v1/status/writes-locked')
                .inScenario("Write lock")
                .whenScenarioStateIs('Still publishing')
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
                .willReturn(ok().withBody('true'))
                .willSetStateTo('Published'))
        final CpsFlowDefinition flow = new CpsFlowDefinition("""
        node {
            dir('out') {
              writeFile file:'dataset.trig', text:'dummy:data'
              writeFile file:'observations.csv', text:'Dummy,CSV'
            }
            jobDraft.replace()
            uploadTidy(['out/observations.csv'],
                       '${wireMockRule.baseUrl()}/columns.csv')
        }""".stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        instanceRule.verify(postRequestedFor(urlEqualTo('/v1/pipelines/ons-table2qb.core/data-cube/import'))
                .withHeader('Accept', equalTo('application/json')))
    }

    @Test
    void "addCompressedData"() {
        instanceRule.stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data?graph=some-graph")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .withRequestBody(matching(".*prefix.*<[^ ]+>.*"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withBodyFile("addDataJob.json")
                        .withHeader("Content-Type", "application/json")))
        instanceRule.stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
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

}
