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
            DATASET_DIR = "${pwd()}/datasets/${JOB_BASE_NAME}"
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
                                    dir(DATASET_DIR) {
                                        if (fileExists("main.py")) {
                                            sh "jupytext --to notebook '*.py'"
                                        }
                                        sh "jupyter-nbconvert --to html --output-dir='out' --ExecutePreprocessor.timeout=None --execute 'main.ipynb'"
                                    }
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
                                    def schemas = []
                                    dir("${DATASET_DIR}") {
                                        dir("out") {
                                            for (def schema : findFiles(glob: "*-metadata.json")) {
                                                schemas.add("${schema.name}")
                                            }
                                            for (String schema : schemas) {
                                                sh "csvlint --no-verbose -s ${schema}"
                                            }
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
                                dir(DATASET_DIR) {
                                    def datasets = []

                                    // Match all files that end .csv or .csv.gz
                                    def csvList = findFiles(glob: "out/*.csv")
                                    def csvGzList = findFiles(glob: "out/*.csv.gz")

                                    for (def csv : csvList + csvGzList) {
                                        if (fileExists("out/${csv.name}-metadata.json")) {
                                            String baseName = csv.name.take(csv.name.lastIndexOf('.csv'))
                                            datasets.add([
                                                    "csv"     : "out/${csv.name}",
                                                    "metadata": "out/${csv.name}-metadata.trig",
                                                    "csvw"    : "out/${csv.name}-metadata.json",
                                                    "output"  : "out/${baseName}"
                                            ])
                                        }
                                    }
                                    writeFile file: "graphs.sparql", text: """SELECT ?md ?ds { GRAPH ?md { [] <http://publishmydata.com/pmdcat#graph> ?ds } }"""
                                    for (def dataset : datasets) {
                                        sh "csv2rdf -t '${dataset.csv}' -u '${dataset.csvw}' -m annotated | pigz > '${dataset.output}.ttl.gz'"
                                        sh "sparql --data='${dataset.metadata}' --query=graphs.sparql --results=JSON > '${dataset.output}-graphs.json'"
                                    }
                                }
                            }
                        }
                    }
                    stage('Local Codelists') {
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
                                dir(DATASET_DIR) {
                                    def codelists = []
                                    for (def metadata : findFiles(glob: "codelists/*.csv-metadata.json") +
                                            findFiles(glob: "out/codelists/*.csv-metadata.json")) {
                                        String baseName = metadata.name.substring(0, metadata.name.lastIndexOf('.csv-metadata.json'))
                                        String dirName = metadata.path.take(metadata.path.lastIndexOf('/'))
                                        codelists.add([
                                                "csv"   : "${dirName}/${baseName}.csv",
                                                "csvw"  : "${dirName}/${baseName}.csv-metadata.json",
                                                "output": "out/codelists/${baseName}"
                                        ])
                                    }
                                    sh "mkdir -p out/codelists"
                                    for (def codelist : codelists) {
                                        sh "csv2rdf -t '${codelist.csv}' -u '${codelist.csvw}' -m annotated | pigz > '${codelist.output}.ttl.gz'"
                                    }
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
                                dir(DATASET_DIR) {
                                    def pmd = pmdConfig("pmd")
                                    def drafter = pmd.drafter
                                    drafter.listDraftsets(Drafter.Include.OWNED)
                                        .findAll { it['display-name'] == env.JOB_NAME }
                                        .each {
                                            drafter.deleteDraftset(it.id)
                                        }
                                    String id = drafter.createDraftset(env.JOB_NAME).id
                                    for (graph in util.jobGraphs(pmd, id).unique()) {
                                        echo "Removing own graph ${graph}"
                                        drafter.deleteGraph(id, graph)
                                    }
                                    def outputFiles = findFiles(glob: "out/*.ttl.gz")
                                    if (outputFiles.length == 0) {
                                        sh "ls out"
                                        error(message: "No output RDF files found")
                                    } else {
                                        for (def observations : outputFiles) {
                                            String baseName = observations.name.substring(0, observations.name.lastIndexOf('.ttl.gz'))
                                            def graphs = readJSON(text: readFile(file: "out/${baseName}-graphs.json"))
                                            String datasetGraph = graphs.results.bindings[0].ds.value
                                            String metadataGraph = graphs.results.bindings[0].md.value
                                            echo "Adding ${observations.name}"
                                            drafter.addData(
                                                    id,
                                                    "out/${observations.name}",
                                                    "text/turtle",
                                                    "UTF-8",
                                                    datasetGraph
                                            )
                                            writeFile(file: "out/${baseName}-ds-prov.ttl", text: util.jobPROV(datasetGraph))
                                            drafter.addData(
                                                    id,
                                                    "out/${baseName}-ds-prov.ttl",
                                                    "text/turtle",
                                                    "UTF-8",
                                                    datasetGraph
                                            )
                                            echo "Adding metadata."
                                            drafter.addData(
                                                    id,
                                                    "out/${baseName}.csv-metadata.trig",
                                                    "application/trig",
                                                    "UTF-8",
                                                    metadataGraph
                                            )
                                            writeFile(file: "out/${baseName}-md-prov.ttl", text: util.jobPROV(metadataGraph))
                                            drafter.addData(
                                                    id,
                                                    "out/${baseName}-md-prov.ttl",
                                                    "text/turtle",
                                                    "UTF-8",
                                                    metadataGraph
                                            )
                                        }
                                    }
                                    for (def codelist : findFiles(glob: "out/codelists/*.ttl.gz")) {
                                        String baseName = codelist.name.substring(0, codelist.name.lastIndexOf('.ttl.gz'))
                                        String codelistGraph = "${pmd.config.base_uri}/graph/${util.slugise(env.JOB_NAME)}/${baseName}"
                                        echo "Adding local codelist ${baseName}"
                                        drafter.addData(
                                                id,
                                                "out/codelists/${codelist.name}",
                                                "text/turtle",
                                                "UTF-8",
                                                codelistGraph
                                        )
                                        writeFile(file: "out/codelists/${baseName}-prov.ttl", text: util.jobPROV(codelistGraph))
                                        drafter.addData(
                                                id,
                                                "out/codelists/${baseName}-prov.ttl",
                                                "text/turtle",
                                                "UTF-8",
                                                codelistGraph
                                        )
                                    }
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
                                def pmd = pmdConfig("pmd")
                                def drafter = pmd.drafter
                                String draftId = drafter.findDraftset(env.JOB_NAME, Drafter.Include.OWNED).id
                                String endpoint = drafter.getDraftsetEndpoint(draftId)
                                String dspath = util.slugise(env.JOB_NAME)
                                def fromGraphs = util.jobGraphs(pmd, draftId) + util.referencedGraphs(pmd, draftId)
                                String fromArgs = fromGraphs.unique().collect { '-f ' + it }.join(' ')
                                String TOKEN = drafter.getToken()
                                wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: TOKEN, var: 'TOKEN']]]) {
                                    sh "sparql-test-runner -i -t /usr/local/tests/qb -s '${endpoint}?union-with-live=true&timeout=180' -l 10 -k '${TOKEN}' ${fromArgs} -r 'reports/TESTS-${dspath}-qb.xml'"
                                    sh "sparql-test-runner -i -t /usr/local/tests/pmd/pmd4 -s '${endpoint}?union-with-live=true&timeout=180' -l 10 -k '${TOKEN}' ${fromArgs} -r 'reports/TESTS-${dspath}-pmd.xml'"
                                    sh "sparql-test-runner -i -t /usr/local/tests/skos -s '${endpoint}?union-with-live=true&timeout=180' -l 10 -k '${TOKEN}' ${fromArgs} -r 'reports/TESTS-${dspath}-skos.xml'"
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
                                        def pmd = pmdConfig("pmd")
                                        def drafter = pmd.drafter
                                        String draftId = drafter.findDraftset(env.JOB_NAME, Drafter.Include.OWNED).id
                                        drafter.submitDraftsetTo(draftId, Drafter.Role.EDITOR, null)
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
                                        def pmd = pmdConfig("pmd")
                                        def drafter = pmd.drafter
                                        String draftId = drafter.findDraftset(env.JOB_NAME, Drafter.Include.OWNED).id
                                        drafter.publishDraftset(draftId)
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
                    def relativeDatasetDir = "datasets/${JOB_BASE_NAME}"
                    archiveArtifacts artifacts: "${relativeDatasetDir}/out/*", excludes: "${relativeDatasetDir}/out/**/*.html"
                    if (!Boolean.parseBoolean(SUPPRESS_JUNIT)) {
                        junit allowEmptyResults: true, testResults: 'reports/**/*.xml'
                    }
                    publishHTML([
                            allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                            reportDir   : "${relativeDatasetDir}/out", reportFiles: 'main.html',
                            reportName  : 'Transform'])
                }
            }
        }
    }
}