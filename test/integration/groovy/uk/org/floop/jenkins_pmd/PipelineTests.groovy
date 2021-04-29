package uk.org.floop.jenkins_pmd

import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import hudson.slaves.EnvironmentVariablesNodeProperty
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

    static {
        // Setting `jenkins.test.noSpaceInTmpDirs` to `true` means that the build directory isn't situated inside a
        // directory path where any of the directories contain a space. The CSVLint portion of the build process
        // currently contains a bug where it is unable to validate files where the path contains a space.
        System.setProperty("jenkins.test.noSpaceInTmpDirs", "true")
    }

    @Rule
    public JenkinsRule rule = new JenkinsRule()

    /**
     * `"mock_drafter": true` ensures that we use the `MockDrafter` instance of `AbstractDrafter` so we don't make any
     * HTTP request at all.
     */
    @Before
    void setupConfigFile() {
        Helpers.setUpConfigFile(rule, """{
          "pmd_api": "http://none",
          "oauth_token_url": "http://none/oauth/token",
          "oauth_audience": "jenkins",
          "credentials": "onspmd4",
          "default_mapping": "https://github.com/ONS-OpenData/ref_trade/raw/master/columns.csv",
          "base_uri": "http://gss-data.org.uk",
          "mock_drafter": true,
          "pmd_public_sparql_endpoint": "https://staging.gss-data.org.uk/sparql"
        }""")
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
        assertPipelineSucceeds('familyTransformPipeline {}', "TestJob")
    }

    @Test(timeout=0l) // Let jUnit know not to apply timeouts here.
    @LocalData // Find the associated data in test/resources/uk/org/floop/jenkins_pmd/PipelineTests/FamilyPipelineAccretiveUpload
    @WithTimeout(10000) // Override Jenkins test harness timeout. 180 seconds is not long enough.
    void "FamilyPipelineAccretiveUpload"() {
        assertPipelineSucceeds('familyTransformPipeline {}', "TestJob")
    }

    @Test(timeout=0l) // Let jUnit know not to apply timeouts here.
    @LocalData // Find the associated data in test/resources/uk/org/floop/jenkins_pmd/PipelineTests/FamilyPipelineFixedDatabaker
    @WithTimeout(10000) // Override Jenkins test harness timeout. 180 seconds is not long enough.
    void "FamilyPipelineFixedDatabaker"() {
        assertPipelineSucceeds('''
            familyTransformPipeline {
                databaker = 'gsscogs/databaker:_1.6.11'          
            }''', "TestJob")
    }

    private void assertPipelineSucceeds(String jenkinsFileDefinition, String jobName) {
        final CpsFlowDefinition flow = new CpsFlowDefinition(jenkinsFileDefinition)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')

        final variablesNodeProperty = new EnvironmentVariablesNodeProperty()
        final envVars = variablesNodeProperty.getEnvVars()
        // JOB_BASE_NAME must match test data folder structure
        envVars.put("JOB_BASE_NAME", jobName)
        // jUnit has a nasty habit of setting the build status to 'UNSTABLE' when we need 'SUCCESS'.
        // Let's just stop running that bit of code until we get a sufficient level of unit tests in this test.
        envVars.put("SUPPRESS_JUNIT", "true")

        rule.jenkins
                .getGlobalNodeProperties()
                .add(variablesNodeProperty);

        workflowJob.definition = flow

        final WorkflowRun buildResult = rule.buildAndAssertSuccess(workflowJob)
        assert buildResult.artifacts.any()
    }
}
