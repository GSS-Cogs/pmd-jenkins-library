package uk.org.floop.jenkins_pmd

import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.jenkinsci.lib.configprovider.ConfigProvider
import org.jenkinsci.plugins.configfiles.ConfigFileStore
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles
import org.jenkinsci.plugins.configfiles.custom.CustomConfig

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule

class DrafterTests {

    @Rule
    public JenkinsRule rule = new JenkinsRule()

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8123)

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
                new CustomConfig("pmd", "config.json", "Details of endpoint URLs and credentials", '''{
  "pmd_api": "https://production-drafter-ons-alpha.publishmydata.com",
  "credentials": "onspmd",
  "pipeline_api": "http://production-grafter-ons-alpha.publishmydata.com/v1/pipelines",
  "default_mapping": "https://github.com/ONS-OpenData/ref_trade/raw/master/columns.csv",
  "base_uri": "http://gss-data.org.uk"
}''', configProvider.getProviderId()))
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
    void "testing library that uses declarative pipeline libraries"() {
        stubFor(get(urlMatching("/v1/draftsets.*"))
                .willReturn(okJson('{"blah"}')))
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        
        echo drafter.listDraftsets('http://localhost:8123', null, 'true')
    '''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        rule.assertLogContains('Listing draftsets...', firstResult)
        verify(getRequestedFor(urlEqualTo("/v1/draftsets")))
    }

}
