import groovy.json.JsonOutput
import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.SparqlQuery

def call(body, forceReplacementUpload = false) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    String FAILED_STAGE
    String DATABAKER = pipelineParams['databaker'] ?: 'gsscogs/databaker'
    String CSVLINT = pipelineParams['csvlint'] ?: 'gsscogs/csvlint'
    String CSV2RDF = pipelineParams['csv2rdf'] ?: 'gsscogs/csv2rdf'
    String GSS_JVM_BUILD_TOOLS = pipelineParams['gssjvmbuildtools'] ?: 'gsscogs/gss-jvm-build-tools'
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
                        sh "rm -rf reports"

                        def infoJsonPath = "${DATASET_DIR}/info.json"
                        def accretiveUpload = false
                        def info = readJSON(text: readFile(file: infoJsonPath))
                        if (info.containsKey('load') && info['load'].containsKey('accretiveUpload')) {
                            accretiveUpload = info['load']['accretiveUpload']
                        }

                        if (forceReplacementUpload && accretiveUpload) {
                            info['load']['accretiveUpload'] = false
                            echo "Forcing replacement upload instead of accretive upload."
                            writeJSON(file: infoJsonPath, json: info, pretty: 4)
                        }
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
                                        for (def schema : findFiles(glob: "codelists/*.csv-metadata.json") +
                                                        findFiles(glob: "out/*-metadata.json")) {
                                            schemas.add("${schema.path}")
                                        }
                                        for (String schema : schemas) {
                                            sh "csvlint --no-verbose -s ${schema}"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    stage('Data Cube') {
                        agent {
                            docker {
                                image GSS_JVM_BUILD_TOOLS
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
                                    writeFile file: "dataset-accretive.sparql", text: """
                                        PREFIX qb: <http://purl.org/linked-data/cube#>
                                        SELECT ?ds { 
                                            [] 
                                                a qb:Observation;  
                                                qb:dataSet ?ds. 
                                        }
                                        LIMIT 1
                                    """
                                    def buildActionQueue = []

                                    for (def dataset : datasets) {
                                        def dataSetTtlOut = "${dataset.output}.ttl"
                                        buildActionQueue.add([
                                                "file": dataset.csvw,
                                                "opType": "CSV2RDF",
                                                "arguments": [ dataSetTtlOut ]
                                        ])

                                        if (util.isAccretiveUpload()) {
                                            buildActionQueue.add([
                                                    "file": dataSetTtlOut,
                                                    "opType": "SPARQL Query",
                                                    "arguments": [ "dataset-accretive.sparql", "${dataset.output}-dataset-uri.json" ]
                                            ])
                                        } else {
                                            // .trig file not generated or desired in accretive Upload
                                            // to avoid duplication of metadata.
                                            buildActionQueue.add([
                                                    "file": dataset.metadata,
                                                    "opType": "SPARQL Query",
                                                    "arguments": [ "graphs.sparql", "${dataset.output}-graphs.json" ]
                                            ])
                                        }
                                    }

                                    writeFile file: "buildActionQueue.json", text: JsonOutput.toJson(buildActionQueue)
                                    sh "gss-jvm-build-tools -c buildActionQueue.json --verbose"

                                    for (def dataset: datasets) {
                                        def dataSetTtlOut = "${dataset.output}.ttl"

                                        sh "cat '${dataSetTtlOut}' | pigz > '${dataset.output}.ttl.gz'"
                                        // Need to keep the raw ttl file to infer the datetimes codelists in the next stage.
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
                        when {
                            expression {
                                // codelists not generated in accretive Upload to avoid duplication of metadata.
                                return !util.isAccretiveUpload()
                            }
                        }
                        steps {
                            script {
                                FAILED_STAGE = env.STAGE_NAME
                                dir(DATASET_DIR) {
                                    writeFile file: "skosNarrowerAugmentation.sparql", text: util.getSparqlQuery(SparqlQuery.SkosNarrowerAugmentation)
                                    writeFile file: "skosTopConceptAugmentation.sparql", text: util.getSparqlQuery(SparqlQuery.SkosTopConceptAugmentation)
                                    writeFile file: "graphs.sparql", text: """SELECT ?graph { [] <http://publishmydata.com/pmdcat#graph> ?graph  }"""

                                    sh "mkdir -p out/codelists"

                                    def dataSetTtlFiles = findFiles(glob: "out/*.ttl")
                                    datetimeCodeLists.generate ttlFiles: dataSetTtlFiles, outFolder: "out/codelists"

                                    for (def dataSetTtl : dataSetTtlFiles) {
                                        // We no longer need the ttl file - the data is gzipped up.
                                        sh "rm '${dataSetTtl}'"
                                    }

                                    def csvWMetadataFiles = findFiles(glob: "codelists/*.csv-metadata.json") +
                                            findFiles(glob: "out/codelists/*.csv-metadata.json")

                                    codeLists.convertCsvWsToRdf files: csvWMetadataFiles
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

                                    if (util.isAccretiveUpload()) {
                                        echo "Accretive Upload - Not deleting graphs associated with this job. " +
                                                "New data will be appended."
                                    } else {
                                        echo "Replacement Upload - Deleting graphs associated with this job."
                                        for (graph in util.jobGraphs(pmd, id).unique()) {
                                            echo "Removing own graph ${graph}"
                                            drafter.deleteGraph(id, graph)
                                        }
                                    }

                                    def outputFiles = findFiles(glob: "out/*.ttl.gz")
                                    if (outputFiles.length == 0) {
                                        error(message: "No output RDF files found")
                                    } else {
                                        for (def observations : outputFiles) {
                                            String baseName = observations.name.substring(0, observations.name.lastIndexOf('.ttl.gz'))
                                            def isAccretiveUpload = util.isAccretiveUpload()

                                            String datasetGraph
                                            if (isAccretiveUpload) {
                                                // We don't know what the graph URI should be.
                                                // Let's find out by querying the data already on live.
                                                def datasetUri =
                                                    readJSON(text: readFile(file: "out/${baseName}-dataset-uri.json"))
                                                        .results.bindings[0].ds.value

                                                datasetGraph = util.getGraphForDataSet(id, datasetUri, true)
                                                // We don't upload metadataGraph info with accretative uploads.
                                            } else {
                                                def graphs = readJSON(text: readFile(file: "out/${baseName}-graphs.json"))
                                                datasetGraph = graphs.results.bindings[0].ds.value
                                                String metadataGraph = graphs.results.bindings[0].md.value

                                                // .trig file not generated or desired in accretive Upload
                                                // to avoid duplication of metadata.
                                                echo "Adding ${observations.name} metadata."
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
                                sparqlTests.test ignoreErrors: true
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
                    util.outputJUnitResults()
                    publishHTML([
                            allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                            reportDir   : "${relativeDatasetDir}/out", reportFiles: 'main.html',
                            reportName  : 'Transform'])
                }
            }
        }
    }
}