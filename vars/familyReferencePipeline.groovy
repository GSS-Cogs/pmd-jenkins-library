import groovy.json.JsonOutput
import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.SparqlQuery
import uk.org.floop.jenkins_pmd.enums.SparqlTestGroup

def call(body) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    String CSVLINT = pipelineParams['csvlint'] ?: 'gsscogs/csvlint'
    String CSV2RDF = pipelineParams['csv2rdf'] ?: 'gsscogs/csv2rdf'
    String GSS_JVM_BUILD_TOOLS = pipelineParams['gssjvmbuildtools'] ?: 'gsscogs/gss-jvm-build-tools'
    String SPARQL_TESTS = pipelineParams['sparqltests'] ?: 'gsscogs/gdp-sparql-tests'

    def referenceFiles = ["measures", "properties"]

    // Designed to work with both family reference CSVs as well as the ref_common reference CSVs.
    pipeline {
        agent {
            label 'master'
        }
        stages {
            stage('Clean') {
                steps {
                    script {
                        sh "rm -rf out"
                        sh "rm -rf reports"
                    }
                }
            }
            stage('Test') {
                agent {
                    docker {
                        image CSVLINT
                        reuseNode true
                        alwaysPull true
                    }
                }
                steps {
                    script {
                        ansiColor('xterm') {
                            dir("reference") {
                                for (def fileName : referenceFiles) {
                                    if (fileExists("${fileName}.csv")) {
                                        sh "csvlint -s ${fileName}.csv-metadata.json"
                                    }
                                }
                                dir("codelists") {
                                    for (def metadata : findFiles(glob: "*.csv-metadata.json")) {
                                        sh "csvlint -s ${metadata.name}"
                                    }
                                }
                            }
                        }
                    }
                }
            }
            stage('Convert to RDF') {
                agent {
                    docker {
                        image GSS_JVM_BUILD_TOOLS
                        reuseNode true
                        alwaysPull true
                    }
                }
                steps {
                    script {
                        sh "mkdir -p out/ontologies out/concept-schemes"

                        dir("reference") {
                            def buildActionQueue = []

                            writeFile file: "ontgraph.sparql", text: """SELECT ?graph { ?graph a <http://www.w3.org/2002/07/owl#Ontology> }"""
                            for (def fileName : referenceFiles) {
                                if (fileExists("${fileName}.csv")) {
                                    def outputOntoFilePath = "../out/ontologies/${fileName}.ttl"
                                    buildActionQueue.add([
                                        "file": "${fileName}.csv-metadata.json",
                                        "opType": "CSV2RDF",
                                        "arguments": [ outputOntoFilePath ]
                                    ])
                                    // Output the name of the RDF graph we want this to be inserted into as JSON.
                                    buildActionQueue.add([
                                            "file": outputOntoFilePath,
                                            "opType": "SPARQL Query",
                                            "arguments": [ "ontgraph.sparql", "${outputOntoFilePath}.graph.json" ]
                                    ])
                                }
                            }

                            writeFile file: "skosNarrowerAugmentation.sparql", text: util.getSparqlQuery(SparqlQuery.SkosNarrowerAugmentation, true)
                            writeFile file: "skosTopConceptAugmentation.sparql", text: util.getSparqlQuery(SparqlQuery.SkosTopConceptAugmentation, true)
                            writeFile file: "csgraph.sparql", text: """SELECT ?graph { ?graph a <http://www.w3.org/2004/02/skos/core#ConceptScheme> }"""

                            dir("codelists") {
                                for (def metadata : findFiles(glob: "*.csv-metadata.json")) {
                                    String baseName = metadata.name.substring(0, metadata.name.lastIndexOf('.csv-metadata.json'))
                                    String outFilePath = "../out/concept-schemes/${baseName}.ttl"
                                    buildActionQueue.add([
                                            "file": "codelists/${metadata.name}",
                                            "opType": "CSV2RDF",
                                            "arguments": [ outFilePath ]
                                    ])
                                    // Output the name of the RDF graph we want this to be inserted into as JSON.
                                    buildActionQueue.add([
                                            "file": outFilePath,
                                            "opType": "SPARQL Query",
                                            "arguments": [ "csgraph.sparql", "${outFilePath}.graph.json" ]
                                    ])

                                    // Augment the CodeList hierarchy with skos:Narrower and skos:hasTopConcept
                                    // annotations. Add the resulting triples on to the end of the .ttl file.
                                    // These annotations are required to help the PMD 'Reference Data' section
                                    // function correctly.
                                    buildActionQueue.add([
                                            "file": outFilePath,
                                            "opType": "SPARQL Update",
                                            "arguments": [ "skosNarrowerAugmentation.sparql" ]
                                    ])
                                    buildActionQueue.add([
                                            "file": outFilePath,
                                            "opType": "SPARQL Update",
                                            "arguments": [ "skosTopConceptAugmentation.sparql" ]
                                    ])
                                }
                            }
                            writeFile file: "buildActionQueue.json", text: JsonOutput.toJson(buildActionQueue)
                            sh "gss-jvm-build-tools -c buildActionQueue.json --verbose"
                        }
                    }
                }
            }
            stage('Draft') {
                stages {
                    stage('Upload Draft') {
                        agent {
                            docker {
                                image CSV2RDF
                                reuseNode true
                                alwaysPull true
                            }
                        }
                        steps {
                            script {
                                def pmd = pmdConfig('pmd')
                                for (myDraft in pmd.drafter
                                        .listDraftsets(Drafter.Include.OWNED)
                                        .findAll { it['display-name'] == env.JOB_NAME }) {
                                    pmd.drafter.deleteDraftset(myDraft.id)
                                }
                                def id = pmd.drafter.createDraftset(env.JOB_NAME).id
                                for (graph in util.jobGraphs(pmd, id)) {
                                    pmd.drafter.deleteGraph(id, graph)
                                    echo "Removing own graph ${graph}"
                                }
                                def uploads = []

                                for (def ontology : findFiles(glob: 'out/ontologies/*.ttl')) {
                                    uploads.add([
                                            "path"  : ontology.path,
                                            "format": "text/turtle",
                                            "graph" : readJSON(text: readFile(file: "${ontology.path}.graph.json")).results.bindings[0].graph.value
                                    ])
                                }
                                for (def cs : findFiles(glob: 'out/concept-schemes/*.ttl')) {
                                    uploads.add([
                                            "path"  : cs.path,
                                            "format": "text/turtle",
                                            "graph" : readJSON(text: readFile(file: "${cs.path}.graph.json")).results.bindings[0].graph.value
                                    ])
                                }
                                for (def upload : uploads) {
                                    pmd.drafter.addData(id, "${WORKSPACE}/${upload.path}", upload.format, "UTF-8", upload.graph)
                                    writeFile(file: "${upload.path}-prov.ttl", text: util.jobPROV(upload.graph))
                                    pmd.drafter.addData(id, "${WORKSPACE}/${upload.path}-prov.ttl", "text/turtle", "UTF-8", upload.graph)
                                }
                            }
                        }
                    }
                    stage('Run SPARQL Tests on Draftset') {
                        agent {
                            docker {
                                image SPARQL_TESTS
                                reuseNode true
                                alwaysPull true
                            }
                        }
                        steps {
                            script {
                                sparqlTests.test includeGraphsReferencedByDataset: false,
                                        testGroups: [SparqlTestGroup.PMD, SparqlTestGroup.SKOS]
                            }
                        }
                    }
                    stage('Publish Draftset') {
                        steps {
                            script {
                                util.publishDraftset()
                            }
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    archiveArtifacts artifacts: "out/**/*"
                    util.outputJUnitResults()
                }
            }
        }
    }
}