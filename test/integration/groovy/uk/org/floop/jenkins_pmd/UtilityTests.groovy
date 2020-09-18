package uk.org.floop.jenkins_pmd

import groovy.json.JsonSlurper
import hudson.FilePath
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

class UtilityTests {

    @Rule
    public JenkinsRule rule = new JenkinsRule()

    @Before
    void setupConfigFile() {
        GlobalConfigFiles globalConfigFiles = rule.jenkins
                .getExtensionList(ConfigFileStore.class)
                .get(GlobalConfigFiles.class)
        CustomConfig.CustomConfigProvider configProvider = rule.jenkins
                .getExtensionList(ConfigProvider.class)
                .get(CustomConfig.CustomConfigProvider.class)
    }

    @Before
    void configureGlobalGitLibraries() {
        RuleBootstrapper.setup(rule)
    }

    @Test
    void "CSV-W for concept schemes"() {
        final CpsFlowDefinition flow = new CpsFlowDefinition('''
        node {
            String csMetadata = util.conceptSchemeMetadata('somecodes.csv', 'http://example.com/def/scheme/something',
                'somecodes', 'Some Codes')
            dir('out') {
              writeFile file:'somecodes.csv-metadata.json', text:csMetadata
            }
        }'''.stripIndent(), true)
        final WorkflowJob workflowJob = rule.createProject(WorkflowJob, 'project')
        workflowJob.definition = flow

        final WorkflowRun firstResult = rule.buildAndAssertSuccess(workflowJob)
        FilePath metadata = rule.jenkins.getWorkspaceFor(workflowJob)
                .child("out")
                .child("somecodes.csv-metadata.json")
        assert metadata != null
        def json = new JsonSlurper().parseText(metadata.readToString())
        assert json.tableSchema.aboutUrl == "http://example.com/def/scheme/something#concept/somecodes/{notation}"
        assert json.tableSchema.columns[1].datatype.format == '^-?[\\w\\.\\/]+(-[\\w\\.\\/]+)*$'
    }

}
