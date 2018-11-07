package uk.org.floop.jenkins_pmd

import com.github.tomakehurst.wiremock.junit.WireMockRule
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
    }

}
