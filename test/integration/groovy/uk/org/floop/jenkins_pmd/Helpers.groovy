package uk.org.floop.jenkins_pmd

import org.jenkinsci.lib.configprovider.ConfigProvider
import org.jenkinsci.plugins.configfiles.ConfigFileStore
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles
import org.jenkinsci.plugins.configfiles.custom.CustomConfig
import org.jvnet.hudson.test.JenkinsRule

class Helpers {
    static void setUpConfigFile(
            JenkinsRule jenkinsRule,
            String configJson
    ) {
        GlobalConfigFiles globalConfigFiles = jenkinsRule.jenkins
                .getExtensionList(ConfigFileStore.class)
                .get(GlobalConfigFiles.class)
        CustomConfig.CustomConfigProvider configProvider = jenkinsRule.jenkins
                .getExtensionList(ConfigProvider.class)
                .get(CustomConfig.CustomConfigProvider.class)
        globalConfigFiles.save(
                new CustomConfig("pmd", "config.json", "Details of endpoint URLs and credentials",
                        configJson, configProvider.getProviderId()))
    }
}
