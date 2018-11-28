package uk.org.floop.jenkins_pmd

import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import com.github.tomakehurst.wiremock.stubbing.Scenario
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
            //.dynamicPort()
            .port(8123)
            .usingFilesUnderClasspath("test/resources")
    )

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;


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
  "base_uri": "http://gss-data.org.uk"
}""", configProvider.getProviderId()))
    }

    @Before
    void setupCredentials() {
        StandardUsernamePasswordCredentials key = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                "onspmd", "Access to PMD APIs", "admin", "admin")
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
        stubFor(get(urlMatching("/v1/draftsets.*"))
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
        verify(getRequestedFor(urlEqualTo("/v1/draftsets")))
        rule.assertLogContains('de305d54-75b4-431b-adb2-eb6b9e546014', firstResult)
    }

    @Test
    void "uploadTidy"() {
        stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                    .withBodyFile("listDraftsets.json")
                    .withHeader("Content-Type", "application/json")))
        stubFor(get("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                    .withBodyFile("newDraftset.json")
                    .withHeader("Content-Type", "application/json")))
        stubFor(delete("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                    .withStatus(202)
                    .withBodyFile("deleteJob.json")
                    .withHeader("Content-Type", "application/json")))
        stubFor(delete(urlMatching("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/graph.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBodyFile("deleteGraph.json")
                    .withHeader("Content-Type", "application/json")))
        stubFor(put("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/data")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                    .withStatus(202)
                    .withBodyFile("addDataJob.json")
                    .withHeader("Content-Type", "application/json")))
        stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse().withStatus(404).withBodyFile('notFinishedJob.json'))
                .willSetStateTo("Finished"))
        stubFor(get("/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400").inScenario("Delete draftset")
                .whenScenarioStateIs("Finished")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                    .withBodyFile("finishedJobOk.json")))
        stubFor(get("/columns.csv").willReturn(ok().withBodyFile('columns.csv')))
        stubFor(post("/v1/pipelines/ons-table2qb.core/data-cube/import")
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
/*                .withMultipartRequestBody(
                    aMultipart()
                            .withName('observations-csv')
                            .withHeader('Content-Type', equalTo('text/csv'))
                            .withBody(equalTo('Dummy,CSV'))) */
                .willReturn(aResponse().withStatus(202).withBodyFile('cubeImportJob.json')))
        stubFor(get('/status/finished-jobs/4fc9ad42-f964-4f56-a1ab-a00bd622b84c')
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
        verify(postRequestedFor(urlEqualTo('/v1/pipelines/ons-table2qb.core/data-cube/import'))
                .withHeader('Accept', equalTo('application/json')))

    }

    @Test
    void "publish draftset"() {
        stubFor(post(urlMatching("/v1/draftset/.*/publish"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(aResponse()
                    .withStatus(202)
                    .withBodyFile("publicationJob.json")
                    .withHeader("Content-Type", "application/json")))
        stubFor(post("/v1/draftsets?display-name=project")
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(seeOther("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0")))
        stubFor(get(urlMatching("/v1/draftsets.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withBasicAuth("admin", "admin")
                .willReturn(ok()
                .withBodyFile("listDraftsets.json")
                .withHeader("Content-Type", "application/json")))
        stubFor(get('/v1/status/finished-jobs/2c4111e5-a299-4526-8327-bad5996de400')
                .withHeader('Accept', equalTo('application/json'))
                .withBasicAuth('admin', 'admin')
                .willReturn(ok().withBodyFile('finishedPublicationJobOk.json')))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            jobDraft.publish()
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        verify(postRequestedFor(urlEqualTo("/v1/draftset/4e376c57-6816-404a-8945-94849299f2a0/publish")))
        rule.assertLogContains('Publishing job draft', firstResult)
    }


}
