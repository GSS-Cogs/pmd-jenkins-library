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
            stage('Validate CSV') {
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
            stage('Upload Tidy Data') {
                steps {
                    script {
                        jobDraft.replace()
                        uploadTidy(['out/observations.csv'],
                                "https://ons-opendata.github.io/${pipelineParams.refFamily}/columns.csv")
                    }
                }
            }
            stage('Test draft dataset') {
                agent {
                    docker {
                        image 'cloudfluff/gdp-sparql-tests'
                        reuseNode true
                    }
                }
                steps {
                    script {
                        pmd = pmdConfig("pmd")
                        String draftId = pmd.drafter.findDraftset(env.JOB_NAME).id
                        String endpoint = pmd.drafter.getDraftsetEndpoint(draftId)
                        String dspath = util.slugise(env.JOB_NAME)
                        String dsgraph = "${pmd.config.base_uri}/graph/${dspath}"
                        withCredentials([usernamePassword(credentialsId: pmd.config.credentials, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                            sh "sparql-test-runner -t /usr/local/tests -s ${endpoint}?union-with-live=true -a '${USER}:${PASS}' -p \"dsgraph=<${dsgraph}>\""
                        }
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
                    junit 'reports/**/*.xml'
                    publishHTML([
                            allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                            reportDir: 'out', reportFiles: 'main.html',
                            reportName: 'Transform'])
                    if (pipelineParams.containsKey('trelloCard')) {
                        updateCard pipelineParams.trelloCard
                    }
                }
            }
        }

    }
}
