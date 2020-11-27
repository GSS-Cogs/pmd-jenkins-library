pipeline {
    agent any

    stages {
        stage('Integration tests') {
            steps {
                sh './gradlew integrationTest'
            }
        }
    }
}