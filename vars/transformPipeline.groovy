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
                            if (fileExists('main.py')) {
                                sh "jupytext --to notebook *.py"
                            }
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
                            if (fileExists('schema.json')) {
                                sh "csvlint -s schema.json"
                            } else {
                                def schemas = []
                                for (def schema : findFiles(glob: 'out/*-schema.json')) {
                                    schemas.add("out/${schema.name}")
                                }
                                for (String schema : schemas) {
                                    sh "csvlint -s ${schema}"
                                }
                            }
                        }
                    }
                }
            }
            stage('Upload Tidy Data') {
                steps {
                    script {
                        jobDraft.replace()
                        if (fileExists('out/observations.csv')) {
                            uploadTidy(['out/observations.csv'],
                                    "https://gss-cogs.github.io/${pipelineParams.refFamily}/columns.csv")
                        } else {
                            def datasets = []
                            String dspath = util.slugise(env.JOB_NAME)
                            for (def observations : findFiles(glob: 'out/*.csv')) {
                                def dataset = [
                                        "csv": "out/${observations.name}",
                                        "metadata": "out/${observations.name}-metadata.trig",
                                        "path": "${dspath}/${observations.name.take(observations.name.lastIndexOf('.'))}"
                                ]
                                datasets.add(dataset)
                            }
                            for (def dataset : datasets) {
                                uploadTidy([dataset.csv],
                                        "https://gss-cogs.github.io/${pipelineParams.refFamily}/columns.csv",
                                        dataset.path,
                                        dataset.metadata)
                            }
                        }
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
                        def dsgraphs = []
                        if (fileExists('out/observations.csv')) {
                            dsgraphs.add("${pmd.config.base_uri}/graph/${dspath}")
                        } else {
                            for (def observations : findFiles(glob: 'out/*.csv')) {
                                String basename = observations.name.take(observations.name.lastIndexOf('.'))
                                dsgraphs.add("${pmd.config.base_uri}/graph/${dspath}/${basename}")
                            }
                        }
                        withCredentials([usernamePassword(credentialsId: pmd.config.credentials, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                            for (String dsgraph : dsgraphs) {
                                sh "sparql-test-runner -t /usr/local/tests -s ${endpoint}?union-with-live=true -a '${USER}:${PASS}' -p \"dsgraph=<${dsgraph}>\" -r reports/TESTS-${dsgraph.substring(dsgraph.lastIndexOf('/')+1)}.xml"
                            }
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
                    step([$class: 'GitHubIssueNotifier',
                          issueAppend: true,
                          issueLabel: '',
                          issueTitle: '$JOB_NAME $BUILD_DISPLAY_NAME failed'])
                    archiveArtifacts artifacts: 'out/*', excludes: 'out/*.html'
                    junit allowEmptyResults: true, testResults: 'reports/**/*.xml'
                    publishHTML([
                            allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                            reportDir: 'out', reportFiles: 'main.html',
                            reportName: 'Transform'])
                }
            }
        }

    }
}
