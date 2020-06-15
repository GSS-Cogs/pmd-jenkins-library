import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.PMD
import uk.org.floop.jenkins_pmd.PMDConfig

def call(String configId) {
    configFileProvider([configFile(fileId: configId, variable: 'configfile')]) {
        PMDConfig config = new PMDConfig(readJSON(text: readFile(file: configfile)))
        withCredentials([usernamePassword(credentialsId: config.credentials, usernameVariable: 'clientID', passwordVariable: 'clientSecret')]) {
            return new PMD(config, clientID, clientSecret)
        }
    }
}
