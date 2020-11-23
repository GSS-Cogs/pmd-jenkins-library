package uk.org.floop.jenkins_pmd

import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import hudson.slaves.EnvironmentVariablesNodeProperty
import org.jenkinsci.lib.configprovider.ConfigProvider
import org.jenkinsci.plugins.configfiles.ConfigFileStore
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles
import org.jenkinsci.plugins.configfiles.custom.CustomConfig
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.recipes.LocalData
import org.jvnet.hudson.test.recipes.WithTimeout

class PipelineTests {

    @Rule
    public JenkinsRule rule = new JenkinsRule()

    @Rule
    public WireMockClassRule instanceRule = DrafterTests.wireMockRule

    @Rule
    public WireMockClassRule oauthRule = DrafterTests.oauthWireMockRule

    @Before
    void setupConfigFile() {
        DrafterTests.setUpConfigFile(rule, instanceRule, oauthRule)
    }

    @Before
    void setupCredentials() {
        DrafterTests.setUpCredentials()
    }

    @Before
    void configureGlobalGitLibraries() {
        RuleBootstrapper.setup(rule)
    }

    @Test(timeout=0l) // Let jUnit know not to apply timeouts here.
    @LocalData // Find the associated data in test/resources/uk/org/floop/jenkins_pmd/PipelineTests/FamilyPipeline
    @WithTimeout(10000) // Override Jenkins test harness timeout. 180 seconds is not long enough.
    void "FamilyPipeline"() {
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
          familyTransformPipeline {}
        '''.stripIndent())
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')

        final variablesNodeProperty = new EnvironmentVariablesNodeProperty()
        final envVars = variablesNodeProperty.getEnvVars()
        envVars.put("JOB_BASE_NAME", "TestJob")

        rule.jenkins
                .getGlobalNodeProperties()
                .add(variablesNodeProperty);

        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
    }

}
