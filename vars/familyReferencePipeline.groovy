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
                            writeFile file: "insertAllGraphsProv.sparql", text: util.getSparqlInsertAllGraphsProv()
                            for (def fileName : referenceFiles) {
                                if (fileExists("${fileName}.csv")) {
                                    def triplesOutFilePath = "../out/ontologies/${fileName}.ttl"
                                    def quadsOutFilePath = "../out/ontologies/${fileName}.nq"
                                    buildActionQueue.add([
                                        "file": "${fileName}.csv-metadata.json",
                                        "opType": "CSV2RDF",
                                        "arguments": [ triplesOutFilePath ]
                                    ])

                                    // Convert from triples to quads to enable us to make one upload to PMD at the end.
                                    buildActionQueue.add([
                                            "file": triplesOutFilePath,
                                            "opType": "SPARQL to Quads",
                                            "arguments": [ "ontgraph.sparql", quadsOutFilePath ]
                                    ])
                                    // Add prov info to newly created graph.
                                    buildActionQueue.add([
                                            "file": quadsOutFilePath,
                                            "opType": "SPARQL Update",
                                            "arguments": [ "insertAllGraphsProv.sparql" ]
                                    ])
                                }
                            }

                            writeFile file: "skosNarrowerAugmentation.sparql", text: util.getSparqlQuery(SparqlQuery.SkosNarrowerAugmentation, true)
                            writeFile file: "skosTopConceptAugmentation.sparql", text: util.getSparqlQuery(SparqlQuery.SkosTopConceptAugmentation, true)
                            writeFile file: "csgraph.sparql", text: """SELECT ?graph { ?graph a <http://www.w3.org/2004/02/skos/core#ConceptScheme> }"""

                            dir("codelists") {
                                for (def metadata : findFiles(glob: "*.csv-metadata.json")) {
                                    String baseName = metadata.name.substring(0, metadata.name.lastIndexOf('.csv-metadata.json'))
                                    String triplesOutFilePath = "../out/concept-schemes/${baseName}.ttl"
                                    String quadsOutFilePath = "../out/concept-schemes/${baseName}.nq"
                                    buildActionQueue.add([
                                            "file": "codelists/${metadata.name}",
                                            "opType": "CSV2RDF",
                                            "arguments": [ triplesOutFilePath ]
                                    ])
                                    // Augment the CodeList hierarchy with skos:Narrower and skos:hasTopConcept
                                    // annotations. Add the resulting triples on to the end of the .ttl file.
                                    // These annotations are required to help the PMD 'Reference Data' section
                                    // function correctly.
                                    buildActionQueue.add([
                                            "file": triplesOutFilePath,
                                            "opType": "SPARQL Update",
                                            "arguments": [ "skosNarrowerAugmentation.sparql" ]
                                    ])
                                    buildActionQueue.add([
                                            "file": triplesOutFilePath,
                                            "opType": "SPARQL Update",
                                            "arguments": [ "skosTopConceptAugmentation.sparql" ]
                                    ])
                                    // Convert from triples to quads to enable us to make one upload to PMD at the end.
                                    buildActionQueue.add([
                                            "file": triplesOutFilePath,
                                            "opType": "SPARQL to Quads",
                                            "arguments": [ "csgraph.sparql", quadsOutFilePath ]
                                    ])
                                    // Add prov info to newly created graph.
                                    buildActionQueue.add([
                                            "file": quadsOutFilePath,
                                            "opType": "SPARQL Update",
                                            "arguments": [ "insertAllGraphsProv.sparql" ]
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

                                def bulkUploadQuadsPath = "out/bulk-upload.nq"
                                sh "echo \"\" > ${bulkUploadQuadsPath}"
                                for (def ontology : findFiles(glob: 'out/ontologies/*.nq')) {
                                    echo "Bundling ${ontology.path} into ${bulkUploadQuadsPath}"
                                    sh "cat \"${ontology.path}\" >> ${bulkUploadQuadsPath}"
                                }
                                for (def cs : findFiles(glob: 'out/concept-schemes/*.nq')) {
                                    echo "Bundling ${cs.path} into ${bulkUploadQuadsPath}"
                                    sh "cat \"${cs.path}\" >> ${bulkUploadQuadsPath}"
                                }
                                pmd.drafter.addData(id, "${WORKSPACE}/${bulkUploadQuadsPath}", "application/n-quads", "UTF-8")
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