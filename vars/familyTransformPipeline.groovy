import uk.org.floop.jenkins_pmd.PMD

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
                        when {
                            expression {
                                def info = readJSON(text: readFile(file: "${DATASET_DIR}/info.json"))
                                return info.containsKey('transform') && info['transform'].containsKey('to_rdf')
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
                                    sh "csv2rdf -t '${dataset.csv}' -u '${dataset.metadata}' -m annotated | rapper -i turtle -o ntriples - http://gss-data.org.uk | split -d -l 100000 --filter='pigz > \$FILE.nt.gz' - ${dataset.output}-"
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
                                if (info.containsKey('transform') && info['transform'].containsKey('to_rdf')) {
                                    def datasets = []
                                    String dspath = util.slugise(env.JOB_NAME)
                                    String datasetGraph = "${pmd.config.base_uri}/graph/${dspath}"
                                    pmd.drafter.deleteGraph(id, datasetGraph)
                                    def outputFiles = findFiles(glob: "${DATASET_DIR}/out/*.nt.gz")
                                    if (outputFiles.length == 0) {
                                        error(message: "No output RDF files found")
                                    } else {
                                        for (def observations : outputFiles) {
                                            pmd.drafter.addData(
                                                    id,
                                                    "${WORKSPACE}/${DATASET_DIR}/out/${observations.name}",
                                                    "application/n-triples",
                                                    "UTF-8",
                                                    datasetGraph
                                            )
                                        }
                                    }
                                } else {
                                    jobDraft.replace()
                                    def datasets = []
                                    String dspath = util.slugise(env.JOB_NAME)
                                    def outputFiles = findFiles(glob: "${DATASET_DIR}/out/*.csv")
                                    if (outputFiles.length == 0) {
                                        error(message: "No output CSV files found")
                                    } else {
                                        for (def observations : outputFiles) {
                                            String thisPath = (outputFiles.length == 1) ? dspath : "${dspath}/${observations.name.take(observations.name.lastIndexOf('.'))}"
                                            def dataset = [
                                                    "csv"     : "${DATASET_DIR}/out/${observations.name}",
                                                    "metadata": "${DATASET_DIR}/out/${observations.name}-metadata.trig",
                                                    "path"    : thisPath
                                            ]
                                            datasets.add(dataset)
                                        }
                                    }
                                    for (def dataset : datasets) {
                                        uploadTidy([dataset.csv],
                                                "reference/columns.csv",
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
                                def dsgraphs = []
                                if (fileExists("${DATASET_DIR}/out/observations.csv")) {
                                    dsgraphs.add("${pmd.config.base_uri}/graph/${dspath}")
                                } else {
                                    for (def observations : findFiles(glob: "${DATASET_DIR}/out/*.csv")) {
                                        String basename = observations.name.take(observations.name.lastIndexOf('.'))
                                        dsgraphs.add("${pmd.config.base_uri}/graph/${dspath}/${basename}")
                                    }
                                }
                                withCredentials([usernamePassword(credentialsId: pmd.config.credentials, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                                    for (String dsgraph : dsgraphs) {
                                        sh "sparql-test-runner -t /usr/local/tests -s ${endpoint}?union-with-live=true -a '${USER}:${PASS}' -p \"dsgraph=<${dsgraph}>\" -r 'reports/TESTS-${dsgraph.substring(dsgraph.lastIndexOf('/') + 1)}.xml'"
                                    }
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
                                        if (info.containsKey('transform') && info['transform'].containsKey('review')) {
                                            return !info['transform']['review']
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
                                        pmd.drafter.submitDraftsetTo(draftId, uk.org.floop.jenkins_pmd.Drafter.Role.EDITOR, null)
                                    }
                                }
                            }
                            stage('Publish') {
                                when {
                                    expression {
                                        def info = readJSON(text: readFile(file: "${DATASET_DIR}/info.json"))
                                        if (info.containsKey('transform') && info['transform'].containsKey('review')) {
                                            return info['transform']['review']
                                        } else {
                                            return true
                                        }
                                    }
                                }
                                steps {
                                    script {
                                        FAILED_STAGE = env.STAGE_NAME
                                        jobDraft.publish()
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