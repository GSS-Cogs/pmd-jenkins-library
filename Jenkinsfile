pipeline {
    agent any

    stages {
        stage('Integration tests') {
            steps {
                sh './gradlew integrationTest'
            }
        }
    }

    post {
        always {
            script {
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