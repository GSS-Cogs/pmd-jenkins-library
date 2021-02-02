import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.SparqlQuery

def call(body) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    String FAILED_STAGE
    String DATABAKER = pipelineParams['databaker'] ?: 'gsscogs/databaker'
    String CSVLINT = pipelineParams['csvlint'] ?: 'gsscogs/csvlint'
    String CSV2RDF = pipelineParams['csv2rdf'] ?: 'gsscogs/csv2rdf'
    String SPARQL_TESTS = pipelineParams['sparqltests'] ?: 'gsscogs/gdp-sparql-tests'

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
                                image DATABAKER
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
                                image CSVLINT
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
                                image CSV2RDF
                                reuseNode true
                                alwaysPull true
                            }
                        }
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                dir(DATASET_DIR) {
                                    def datasets = []

                                    for (def csv : findFiles(glob: "out/*.csv")) {
                                        if (fileExists("out/${csv.name}-metadata.json")) {
                                            String baseName = csv.name.take(csv.name.lastIndexOf('.'))
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
                                image CSV2RDF
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

                                    writeFile file: "skosNarrowerAugmentation.sparql", text: util.getSparqlQuery(SparqlQuery.SkosNarrowerAugmentation)
                                    writeFile file: "skosTopConceptAugmentation.sparql", text: util.getSparqlQuery(SparqlQuery.SkosTopConceptAugmentation)

                                    writeFile file: "graphs.sparql", text: """SELECT ?graph { [] <http://publishmydata.com/pmdcat#graph> ?graph  }"""
                                    for (def codelist : codelists) {
                                        String outFilePath = "${codelist.output}.ttl"
                                        sh "csv2rdf -t '${codelist.csv}' -u '${codelist.csvw}' -m annotated > '${outFilePath}'"

                                        // Augment the CodeList hierarchy with skos:Narrower and skos:hasTopConcept
                                        // annotations. Add the resulting triples on to the end of the .ttl file.
                                        // These annotations are required to help the PMD 'Reference Data' section
                                        // function correctly.
                                        sh "sparql --data='${outFilePath}' --query=skosNarrowerAugmentation.sparql >> '${outFilePath}'"
                                        sh "sparql --data='${outFilePath}' --query=skosTopConceptAugmentation.sparql >> '${outFilePath}'"

                                        sh "sparql --data='${outFilePath}' --query=graphs.sparql --results=JSON > '${codelist.output}-graphs.json'"

                                        sh "cat '${outFilePath}' | pigz > '${codelist.output}.ttl.gz'"
                                        sh "rm '${outFilePath}'"
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
                                    drafter.listDraftsets(Drafter.Include.CLAIMABLE)
                                            .findAll { it['display-name'] == env.JOB_NAME }
                                            .each {
                                                drafter.claimDraftset(it.id)
                                            }
                                    drafter.listDraftsets(Drafter.Include.OWNED)
                                        .findAll { it['display-name'] == env.JOB_NAME }
                                        .each {
                                            drafter.deleteDraftset(it.id)
                                        }
                                    String id = drafter.createDraftset(env.JOB_NAME).id

                                    echo "New draftset created at ${env.PMD_DRAFTSET_URI_BASE + id}"
                                    for (graph in util.jobGraphs(pmd, id).unique()) {
                                        echo "Removing own graph ${graph}"
                                        drafter.deleteGraph(id, graph)
                                    }
                                    def outputFiles = findFiles(glob: "out/*.ttl.gz")
                                    if (outputFiles.length == 0) {
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
                                                    "${DATASET_DIR}/out/${observations.name}",
                                                    "text/turtle",
                                                    "UTF-8",
                                                    datasetGraph
                                            )
                                            writeFile(file: "out/${baseName}-ds-prov.ttl", text: util.jobPROV(datasetGraph))
                                            drafter.addData(
                                                    id,
                                                    "${DATASET_DIR}/out/${baseName}-ds-prov.ttl",
                                                    "text/turtle",
                                                    "UTF-8",
                                                    datasetGraph
                                            )
                                            echo "Adding metadata."
                                            drafter.addData(
                                                    id,
                                                    "${DATASET_DIR}/out/${baseName}.csv-metadata.trig",
                                                    "application/trig",
                                                    "UTF-8",
                                                    metadataGraph
                                            )
                                            writeFile(file: "out/${baseName}-md-prov.ttl", text: util.jobPROV(metadataGraph))
                                            drafter.addData(
                                                    id,
                                                    "${DATASET_DIR}/out/${baseName}-md-prov.ttl",
                                                    "text/turtle",
                                                    "UTF-8",
                                                    metadataGraph
                                            )
                                        }
                                    }
                                    for (def codelist : findFiles(glob: "out/codelists/*.ttl.gz")) {
                                        String baseName = codelist.name.substring(0, codelist.name.lastIndexOf('.ttl.gz'))

                                        def graphs = readJSON(text: readFile(file: "out/codelists/${baseName}-graphs.json"))
                                        String codelistGraph = graphs.results.bindings != null && graphs.results.bindings.any()
                                                ? graphs.results.bindings[0].graph.value
                                                // Backwards compatibility for code lists without DCAT/PMDCAT metadata.
                                                : "${pmd.config.base_uri}/graph/${util.slugise(env.JOB_NAME)}/${baseName}"

                                        echo "Adding local codelist ${baseName}"
                                        drafter.addData(
                                                id,
                                                "${DATASET_DIR}/out/codelists/${codelist.name}",
                                                "text/turtle",
                                                "UTF-8",
                                                codelistGraph
                                        )
                                        writeFile(file: "out/codelists/${baseName}-prov.ttl", text: util.jobPROV(codelistGraph))
                                        drafter.addData(
                                                id,
                                                "${DATASET_DIR}/out/codelists/${baseName}-prov.ttl",
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
                    stage("Run SPARQL Tests on Draftset") {
                        agent {
                            docker {
                                image SPARQL_TESTS
                                reuseNode true
                                alwaysPull true
                            }
                        }
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                sparqlTests.run()
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
                                        util.publishDraftset()
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
                    archiveArtifacts artifacts: "${relativeDatasetDir}/out/**/*", excludes: "${relativeDatasetDir}/out/**/*.html"
                    outputJUnitResults()
                    publishHTML([
                            allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                            reportDir   : "${relativeDatasetDir}/out", reportFiles: 'main.html',
                            reportName  : 'Transform'])
                }
            }
        }
    }
}