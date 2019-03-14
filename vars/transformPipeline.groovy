def call(body) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent {
            label 'master'
        }
        triggers {
            upstream(upstreamProjects: "../Reference/${pipelineParams.refFamily}",
                    threshold: hudson.model.Result.SUCCESS)
        }
        stages {
            stage('Clean') {
                steps {
                    sh 'rm -rf out'
                }
            }
            stage('Transform') {
                agent {
                    docker {
                        image 'cloudfluff/databaker'
                        reuseNode true
                    }
                }
                steps {
                    script {
                        ansiColor('xterm') {
                            sh "jupyter-nbconvert --output-dir=out --ExecutePreprocessor.timeout=None --execute 'main.ipynb'"
                        }
                    }
                }
            }
            stage('Test') {
                agent {
                    docker {
                        image 'cloudfluff/csvlint'
                        reuseNode true
                    }
                }
                steps {
                    script {
                        ansiColor('xterm') {
                            sh "csvlint -s schema.json"
                        }
                    }
                }
            }
            stage('Upload draftset') {
                steps {
                    script {
                        jobDraft.replace()
                        uploadTidy(['out/observations.csv'],
                                "https://ons-opendata.github.io/${pipelineParams.refFamily}/columns.csv")
                    }
                }
            }
            stage('Publish') {
                steps {
                    script {
                        jobDraft.publish()
                    }
                }
            }
        }
        post {
            always {
                script {
                    archiveArtifacts artifacts: 'out/*', excludes: 'out/*.html'
                    publishHTML([
                            allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                            reportDir: 'out', reportFiles: 'main.html',
                            reportName: 'Transform'])
                    if (pipelineParams.containsKey('trelloCard')) {
                        updateCard pipelineParams.trelloCard
                    }
                }
            }
            success {
                build job: '../GDP-tests', wait: false
            }
        }

    }
}