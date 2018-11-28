import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.PMD
import uk.org.floop.jenkins_pmd.PMDConfig

def call(String configId) {
    configFileProvider([configFile(fileId: configId, variable: 'configfile')]) {
        PMDConfig config = new PMDConfig(readJSON(text: readFile(file: configfile)))
        withCredentials([usernamePassword(credentialsId: config.credentials, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
            if (config.cache_credentials) {
                withCredentials([usernamePassword(credentialsId: config.cache_credentials, usernameVariable: 'CACHE_USER', passwordVariable: 'CACHE_PASS')]) {
                    return new PMD(config, USER as String, PASS as String, CACHE_USER as String, CACHE_PASS as String)
                }
            } else {
                return new PMD(config, USER as String, PASS as String, null, null)
            }
        }
    }
}
