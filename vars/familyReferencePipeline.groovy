import uk.org.floop.jenkins_pmd.Drafter
import uk.org.floop.jenkins_pmd.SparqlQueries

def call(body) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

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
                    }
                }
            }
            stage('Test') {
                agent {
                    docker {
                        image 'gsscogs/csvlint'
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
                        image 'gsscogs/csv2rdf'
                        reuseNode true
                        alwaysPull true
                    }
                }
                steps {
                    script {
                        sh "mkdir -p out/ontologies out/concept-schemes"

                        dir("reference") {
                            for (def fileName : referenceFiles) {
                                if (fileExists("${fileName}.csv")) {
                                    sh "csv2rdf -t '${fileName}.csv' -u '${fileName}.csv-metadata.json' -m annotated -o ../out/ontologies/${fileName}.ttl"
                                }
                            }
                            dir("codelists") {
                                for (def metadata : findFiles(glob: "*.csv-metadata.json")) {
                                    String baseName = metadata.name.substring(0, metadata.name.lastIndexOf('.csv-metadata.json'))
                                    sh "csv2rdf -t '${baseName}.csv' -u '${metadata.name}' -m annotated > '../../out/concept-schemes/${baseName}.ttl'"
                                }
                            }
                        }
                    }
                }
            }
            stage('Upload') {
                agent {
                    docker {
                        image 'gsscogs/csv2rdf'
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
                        writeFile file: "ontgraph.sparql", text: """SELECT ?graph { ?graph a <http://www.w3.org/2002/07/owl#Ontology> }"""
                        for (def ontology : findFiles(glob: 'out/ontologies/*')) {
                            sh "sparql --data='${ontology.path}' --query=ontgraph.sparql --results=JSON > 'graph.json'"
                            uploads.add([
                                    "path"  : ontology.path,
                                    "format": "text/turtle",
                                    "graph" : readJSON(text: readFile(file: "graph.json")).results.bindings[0].graph.value
                            ])
                        }
                        writeFile file: "csgraph.sparql", text: """SELECT ?graph { ?graph a <http://www.w3.org/2004/02/skos/core#ConceptScheme> }"""
                        writeFile file: "skosNarrowerAugmentation.sparql", text: util.getSparqlQuery(SparqlQueries.SparqlQuery.SkosNarrowerAugmentation)
                        writeFile file: "skosTopConceptAugmentation.sparql", text: util.getSparqlQuery(SparqlQueries.SparqlQuery.SkosTopConceptAugmentation)
                        for (def cs : findFiles(glob: 'out/concept-schemes/*')) {
                            sh "sparql --data='${cs.path}' --query=csgraph.sparql --results=JSON > 'graph.json'"
                            // Augment the CodeList hierarchy with skos:Narrower and skos:hasTopConcept annotations.
                            // Add the resulting triples on to the end of the .ttl file.
                            sh "sparql --data='${cs.path}' --query=skosNarrowerAugmentation.sparql >> '${cs.path}'"
                            sh "sparql --data='${cs.path}' --query=skosTopConceptAugmentation.sparql >> '${cs.path}'"
                            uploads.add([
                                    "path"  : cs.path,
                                    "format": "text/turtle",
                                    "graph" : readJSON(text: readFile(file: "graph.json")).results.bindings[0].graph.value
                            ])
                        }
                        for (def upload : uploads) {
                            pmd.drafter.addData(id, "${WORKSPACE}/${upload.path}", upload.format, "UTF-8", upload.graph)
                            writeFile(file: "${upload.path}-prov.ttl", text: util.jobPROV(upload.graph))
                            pmd.drafter.addData(id, "${WORKSPACE}/${upload.path}-prov.ttl", "text/turtle", "UTF-8", upload.graph)
                        }
                        pmd.drafter.publishDraftset(id)
                    }
                }
            }
        }
        post {
            always {
                script {
                    archiveArtifacts artifacts: "out/"
                }
            }
        }
    }
}