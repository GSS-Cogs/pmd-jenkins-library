import uk.org.floop.jenkins_pmd.Drafter

def call(body) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    def FAILED_STAGE

    pipeline {
        agent {
            label 'master'
        }
        environment {
            DATASET_DIR = "datasets/${JOB_BASE_NAME}"
            JOB_ID = util.getJobID()
        }
        stages {
            stage('Clean') {
                steps {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                        sh "rm -rf ${DATASET_DIR}/out"
                    }
                }
            }
            stage('Transform') {
                stages {
                    stage('Tidy CSV') {
                        agent {
                            docker {
                                image 'gsscogs/databaker'
                                reuseNode true
                                alwaysPull true
                            }
                        }
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                ansiColor('xterm') {
                                    if (fileExists("${DATASET_DIR}/main.py")) {
                                        sh "jupytext --to notebook ${DATASET_DIR}/*.py"
                                    }
                                    sh "jupyter-nbconvert --output-dir=${DATASET_DIR}/out --ExecutePreprocessor.timeout=None --execute '${DATASET_DIR}/main.ipynb'"
                                }
                            }
                        }
                    }
                    stage('Validate CSV') {
                        agent {
                            docker {
                                image 'gsscogs/csvlint'
                                reuseNode true
                                alwaysPull true
                            }
                        }
                        when {
                            expression {
                                def info = readJSON(text: readFile(file: "${DATASET_DIR}/info.json"))
                                if (info.containsKey('transform') && info['transform'].containsKey('validate')) {
                                    return info['transform']['validate']
                                } else {
                                    return true
                                }
                            }
                        }
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                ansiColor('xterm') {
                                    if (fileExists("${DATASET_DIR}/schema.json")) {
                                        sh "csvlint --no-verbose -s ${DATASET_DIR}/schema.json"
                                    } else {
                                        def schemas = []
                                        for (def schema : findFiles(glob: "${DATASET_DIR}/out/*-schema.json")) {
                                            schemas.add("${DATASET_DIR}/out/${schema.name}")
                                        }
                                        for (String schema : schemas) {
                                            sh "csvlint --no-verbose -s '${schema}'"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    stage('Data Cube') {
                        agent {
                            docker {
                                image 'gsscogs/csv2rdf'
                                reuseNode true
                                alwaysPull true
                            }
                        }
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                def datasets = []
                                for (def csv : findFiles(glob: "${DATASET_DIR}/out/*.csv")) {
                                    if (fileExists("${DATASET_DIR}/out/${csv.name}-metadata.json")) {
                                        String baseName = csv.name.take(csv.name.lastIndexOf('.'))
                                        datasets.add([
                                                "csv": "${DATASET_DIR}/out/${csv.name}",
                                                "metadata": "${DATASET_DIR}/out/${csv.name}-metadata.json",
                                                "output": "${DATASET_DIR}/out/${baseName}"
                                        ])
                                    }
                                }
                                for (def dataset : datasets) {
                                    sh "csv2rdf -t '${dataset.csv}' -u '${dataset.metadata}' -m annotated | pigz > '${dataset.output}.ttl.gz'"
                                }
                            }
                        }
                    }
                }
            }
            stage('Load') {
                when {
                    expression {
                        def info = readJSON(text: readFile(file: "${DATASET_DIR}/info.json"))
                        if (info.containsKey('load') && info['load'].containsKey('skip')) {
                            return !info['load']['skip']
                        } else {
                            return true
                        }
                    }
                }
                stages {
                    stage('Upload Cube') {
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                def pmd = pmdConfig("pmd")
                                pmd.drafter
                                        .listDraftsets()
                                        .findAll { it['display-name'] == env.JOB_NAME }
                                        .each {
                                            pmd.drafter.deleteDraftset(it.id)
                                        }
                                String id = pmd.drafter.createDraftset(env.JOB_NAME).id
                                def info = readJSON(text: readFile(file: "${DATASET_DIR}/info.json"))
                                def datasets = []
                                String dspath = util.slugise(env.JOB_NAME)
                                String datasetGraph = "${pmd.config.base_uri}/graph/${dspath}"
                                String metadataGraph = "${pmd.config.base_uri}/graph/${dspath}-metadata"
                                def toDelete = [datasetGraph, metadataGraph]
                                toDelete.addAll(util.jobGraphs(pmd, id))
                                for (graph in toDelete.unique()) {
                                    echo "Removing own graph ${graph}"
                                    pmd.drafter.deleteGraph(id, graph)
                                }
                                def outputFiles = findFiles(glob: "${DATASET_DIR}/out/*.ttl.gz")
                                if (outputFiles.length == 0) {
                                    error(message: "No output RDF files found")
                                } else {
                                    for (def observations : outputFiles) {
                                        echo "Adding ${observations.name}"
                                        pmd.drafter.addData(
                                                id,
                                                "${WORKSPACE}/${DATASET_DIR}/out/${observations.name}",
                                                "text/turtle",
                                                "UTF-8",
                                                datasetGraph
                                        )
                                    }
                                    writeFile(file: "${DATASET_DIR}/out/datasetPROV.ttl", text: util.jobPROV(datasetGraph))
                                    pmd.drafter.addData(
                                            id,
                                            "${WORKSPACE}/${DATASET_DIR}/out/datasetPROV.ttl",
                                            "text/turtle",
                                            "UTF-8",
                                            datasetGraph
                                    )
                                }
                                if (fileExists("${DATASET_DIR}/out/observations.csv-metadata.trig")) {
                                    echo "Adding metadata."
                                    pmd.drafter.addData(
                                            id,
                                            "${WORKSPACE}/${DATASET_DIR}/out/observations.csv-metadata.trig",
                                            "application/trig",
                                            "UTF-8",
                                            metadataGraph
                                    )
                                    writeFile(file: "${DATASET_DIR}/out/metadataPROV.ttl", text: util.jobPROV(metadataGraph))
                                    pmd.drafter.addData(
                                            id,
                                            "${WORKSPACE}/${DATASET_DIR}/out/metadataPROV.ttl",
                                            "text/turtle",
                                            "UTF-8",
                                            metadataGraph
                                    )
                                }
                            }
                        }
                    }
                }
            }
            stage('Draft') {
                stages {
                    stage('Test draft dataset') {
                        agent {
                            docker {
                                image 'gsscogs/gdp-sparql-tests'
                                reuseNode true
                                alwaysPull true
                            }
                        }
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                pmd = pmdConfig("pmd")
                                String draftId = pmd.drafter.findDraftset(env.JOB_NAME).id
                                String endpoint = pmd.drafter.getDraftsetEndpoint(draftId)
                                String dspath = util.slugise(env.JOB_NAME)
                                String datasetGraph = "${pmd.config.base_uri}/graph/${dspath}"
                                String metadataGraph = "${pmd.config.base_uri}/graph/${dspath}-metadata"
                                String TOKEN = pmd.drafter.getToken()
                                wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: TOKEN, var: 'TOKEN']]]) {
                                    sh "sparql-test-runner -i -t /usr/local/tests/qb -s '${endpoint}?union-with-live=true&timeout=180' -k '${TOKEN}' -p \"dsgraph=<${datasetGraph}>\" -r 'reports/TESTS-${dspath}-qb.xml'"
                                    sh "sparql-test-runner -i -t /usr/local/tests/pmd/pmd4 -s '${endpoint}?union-with-live=true&timeout=180' -k '${TOKEN}' -p \"dsgraph=<${datasetGraph}>,mdgraph=<${metadataGraph}>\" -r 'reports/TESTS-${dspath}-pmd.xml'"
                                }
                            }
                        }
                    }
                    stage('Draftset') {
                        parallel {
                            stage('Submit for review') {
                                when {
                                    expression {
                                        def info = readJSON(text: readFile(file: "${DATASET_DIR}/info.json"))
                                        if (info.containsKey('load') && info['load'].containsKey('publish')) {
                                            return !info['load']['publish']
                                        } else {
                                            return true
                                        }
                                    }
                                }
                                steps {
                                    script {
                                        FAILED_STAGE = env.STAGE_NAME
                                        pmd = pmdConfig("pmd")
                                        String draftId = pmd.drafter.findDraftset(env.JOB_NAME).id
                                        pmd.drafter.submitDraftsetTo(draftId, Drafter.Role.EDITOR, null)
                                    }
                                }
                            }
                            stage('Publish') {
                                when {
                                    expression {
                                        def info = readJSON(text: readFile(file: "${DATASET_DIR}/info.json"))
                                        if (info.containsKey('load') && info['load'].containsKey('publish')) {
                                            return info['load']['publish']
                                        } else {
                                            return false
                                        }
                                    }
                                }
                                steps {
                                    script {
                                        FAILED_STAGE = env.STAGE_NAME
                                        pmd = pmdConfig("pmd")
                                        String draftId = pmd.drafter.findDraftset(env.JOB_NAME).id
                                        pmd.drafter.publishDraftset(draftId)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    archiveArtifacts artifacts: "${DATASET_DIR}/out/*", excludes: "${DATASET_DIR}/out/*.html"
                    junit allowEmptyResults: true, testResults: 'reports/**/*.xml'
                    publishHTML([
                            allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                            reportDir   : "${DATASET_DIR}/out", reportFiles: 'main.html',
                            reportName  : 'Transform'])
                }
            }
        }
    }
}