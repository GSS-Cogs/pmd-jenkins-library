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
    boolean DEBUG_PYTHON = pipelineParams['debugPython'] ?: false

    pipeline {
        agent {
            label 'master'
        }
        environment {
            DATASET_DIR = "${pwd()}/datasets/${JOB_BASE_NAME}"
            JOB_ID = util.getJobID()
            SPARQL_URL = "${pmdConfig("pmd").pmdPublicSparqlEndPoint}"
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

                                        echo "Sparql endpoint: ${env.SPARQL_URL}"
                                        def nbConvertCommand = "jupyter-nbconvert --to html --output-dir='out' --ExecutePreprocessor.timeout=None --execute 'main.ipynb'"
                                        if (DEBUG_PYTHON) {
                                            nbConvertCommand += " --debug"
                                        }
                                        sh nbConvertCommand
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
                                def isAccretiveUpload = util.isAccretiveUpload()

                                dir(DATASET_DIR) {
                                    def datasets = []

                                    for (def csv : findFiles(glob: "out/*.csv")) {
                                        if (fileExists("out/${csv.name}-metadata.json")) {
                                            String baseName = csv.name.take(csv.name.lastIndexOf('.'))
                                            datasets.add([
                                                    "csv"             : "out/${csv.name}",
                                                    "catalogMetadata" : "out/${csv.name}-metadata.trig",
                                                    "csvw"            : "out/${csv.name}-metadata.json",
                                                    "output"          : "out/${baseName}"
                                            ])
                                        }
                                    }

                                    writeFile file: "metadata-graph.sparql", text: """
                                        SELECT ?metadataGraphUri
                                        { 
                                            GRAPH ?metadataGraphUri 
                                            { 
                                                [] <http://publishmydata.com/pmdcat#graph> [] 
                                            } }"""
                                    writeFile file: "dataset-graph.sparql", text: """
                                        PREFIX sd: <http://www.w3.org/ns/sparql-service-description#>
                                        
                                        SELECT ?datasetGraphUri
                                        WHERE {
                                          ?datasetGraphUri a sd:NamedGraph.
                                        }
                                    """
                                    writeFile file: "dataset-uri.sparql", text: """
                                        PREFIX qb: <http://purl.org/linked-data/cube#>
                                        SELECT ?datasetUri { 
                                            [] 
                                                a qb:Observation;  
                                                qb:dataSet ?datasetUri. 
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


                                        buildActionQueue.add([
                                                "file": dataSetTtlOut,
                                                "opType"   : "SPARQL Query",
                                                "arguments": ["dataset-graph.sparql",
                                                              "${dataset.output}-dataset-graph.json"]
                                        ])

                                        buildActionQueue.add([
                                                "file": dataSetTtlOut,
                                                "opType": "SPARQL Query",
                                                "arguments": [ "dataset-uri.sparql",
                                                               "${dataset.output}-dataset-uri.json" ]
                                        ])

                                        if (fileExists(dataset.catalogMetadata)) {
                                            // .trig file not generated or desired in accretive Upload/multi-graph
                                            // datasets to avoid duplication of metadata.
                                            buildActionQueue.add([
                                                    "file"     : dataset.catalogMetadata,
                                                    "opType"   : "SPARQL Query",
                                                    "arguments": [ "metadata-graph.sparql",
                                                                   "${dataset.output}-metadata-graph.json" ]
                                            ])
                                        }
                                    }

                                    writeFile file: "buildActionQueue.json", text: JsonOutput.toJson(buildActionQueue)
                                    sh "gss-jvm-build-tools -c buildActionQueue.json --verbose"

                                    for (def dataset: datasets) {
                                        def dataSetTtlOut = "${dataset.output}.ttl"

                                        sh "cat '${dataSetTtlOut}' | pigz > '${dataset.output}.ttl.gz'"

                                        if (isAccretiveUpload) {
                                            sh "rm '${dataSetTtlOut}'"
                                        } // Else, we need to keep the raw ttl file to infer the datetimes codelists in the next stage.
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

                                    def mapDataSetUriToExpectedGraphs = [:]
                                    def outputFiles = findFiles(glob: "out/*.ttl.gz")
                                    if (outputFiles.length == 0) {
                                        error(message: "No output RDF files found")
                                    } else {
                                        for (def observations : outputFiles) {
                                            String baseName = observations.name.substring(0, observations.name.lastIndexOf('.ttl.gz'))

                                            def datasetGraphUriJson = readJSON(text: readFile(file: "out/${baseName}-dataset-graph.json"))
                                            def datasetGraph = datasetGraphUriJson.results.bindings[0].datasetGraphUri.value
                                            def datasetUriJson = readJSON(text: readFile(file: "out/${baseName}-dataset-uri.json"))
                                            def datasetUri = datasetUriJson.results.bindings[0].datasetUri.value

                                            def expectedTrigFilePath = "${DATASET_DIR}/out/${baseName}.csv-metadata.trig"
                                            if (fileExists(expectedTrigFilePath)) {
                                                // .trig file not generated or desired in accretive Upload/multi-graph
                                                // datasets to avoid duplication of metadata.
                                                echo "Adding ${observations.name} metadata."
                                                def metadataGraphUriJson = readJSON(text: readFile(file: "out/${baseName}-metadata-graph.json"))
                                                String metadataGraph = metadataGraphUriJson.results.bindings[0].metadataGraphUri.value

                                                drafter.addData(
                                                        id,
                                                        expectedTrigFilePath,
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

                                            if (!mapDataSetUriToExpectedGraphs.containsKey(datasetUri)) {
                                                mapDataSetUriToExpectedGraphs[datasetUri] = []
                                            }
                                            List<String> expectedGraphsForDataset = mapDataSetUriToExpectedGraphs[datasetUri]
                                            if (!expectedGraphsForDataset.contains(datasetGraph)){
                                                expectedGraphsForDataset.add(datasetGraph)
                                            }
                                        }
                                    }

                                    for (String dataSetUri : mapDataSetUriToExpectedGraphs.keySet()){
                                        List<String> expectedGraphUris = mapDataSetUriToExpectedGraphs[dataSetUri]
                                        echo "Linking expected graphs ${expectedGraphUris.join(", ")} to dataset ${dataSetUri}"

                                        def catalogueEntryGraphUri = util.getCatalogGraphForDataSet(id, dataSetUri)
                                        def newCatalogEntryTriples =
                                                util.getCatalogEntryTriplesToAdd draftId: id,
                                                        dataSetUri: dataSetUri,
                                                        expectedGraphs: expectedGraphUris
                                        if (newCatalogEntryTriples.length() > 0) {
                                            def fileSafeName = dataSetUri.replaceAll("[^A-Za-z0-9]+", "-")
                                            def catalogEntriesFile = "${DATASET_DIR}/out/${fileSafeName}-catalog-entry-graphs.ttl"
                                            writeFile(file: catalogEntriesFile, text: newCatalogEntryTriples)
                                            drafter.addData(
                                                    id,
                                                    catalogEntriesFile.toString(),
                                                    "text/turtle",
                                                    "UTF-8",
                                                    catalogueEntryGraphUri
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