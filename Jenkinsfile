pipeline {
    agent any

    stages {
        stage('Integration tests') {
            steps {
                sh './gradlew clean'
                sh './gradlew integrationTest --tests UtilityTests --tests DrafterTests'
            }
        }
    }

    post {
        always {
            script {
                junit allowEmptyResults: true, testResults: 'build/test-results/**/*.xml'
                publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir   : "build/reports/tests/integrationTest/",
                        reportFiles : 'index.html',
                        reportName  : 'Integration Tests'])
            }
        }
    }
}