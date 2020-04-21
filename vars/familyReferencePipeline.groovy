def call(body) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent {
            label 'master'
        }
        stages {
            stage('Test') {
                agent {
                    docker {
                        image 'cloudfluff/csvlint'
                        reuseNode true
                        alwaysPull true
                    }
                }
                steps {
                    script {
                        ansiColor('xterm') {
                            sh "csvlint -s reference/codelists-metadata.json"
                            sh "csvlint -s reference/columns.csv-metadata.json"
                            sh "csvlint -s reference/components.csv-metadata.json"
                        }
                    }
                }
            }
            stage('Upload') {
                steps {
                    script {
                        def pmd = pmdConfig("pmd")
                        pmd.drafter
                                .listDraftsets()
                                .findAll { it['display-name'] == env.JOB_NAME }
                                .each {
                                    pmd.drafter.deleteDraftset(it.id)
                                }
                        String id = pmd.drafter.createDraftset(env.JOB_NAME).id
                        def codelists = readJSON(file: 'reference/codelists-metadata.json')
                        for (def table : codelists['tables']) {
                            String codelistFilename = table['url']
                            String label = table['rdfs:label']
                            pmd.drafter.deleteGraph(id, "${pmd.config.base_uri}/graph/${util.slugise(label)}")
                            pmd.pipelines.codelist(id, "${WORKSPACE}/reference/${codelistFilename}", label)
                        }
                        if (fileExists('reference/components.csv')) {
                            pmd.pipelines.components(id, "${WORKSPACE}/reference/components.csv")
                        }
                        if (fileExists('reference/components.trig')) {
                            String trig = readFile('reference/components.trig')
                            def graphMatch = trig =~ /(?ms)<([^>]+)>\s+\{/
                            graphMatch.find()
                            String componentsGraph = graphMatch.group(1)
                            pmd.drafter.deleteGraph(id, componentsGraph)
                            pmd.drafter.addData(
                                    id,
                                    "${WORKSPACE}/reference/components.trig",
                                    "application/trig",
                                    "UTF-8"
                            )
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
    }
}