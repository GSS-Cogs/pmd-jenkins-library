import uk.org.floop.jenkins_pmd.SparqlQuery
import uk.org.floop.jenkins_pmd.models.CatalogMetadata

def generate(Map config) {
    String[] dataSetTtlFilePaths = config.get("ttlFiles")
    String outFolderPath = config.get("outFolder")

    writeFile file: "date-time-code-list-gen.sparql", text: util.getSparqlQuery(SparqlQuery.DateTimeCodeListConceptGeneration)

    writeFile file: "concept-scheme-uris.sparql", text: """
        PREFIX skos:    <http://www.w3.org/2004/02/skos/core#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

        SELECT DISTINCT ?conceptScheme ?label { 
            ?conceptScheme 
                a skos:ConceptScheme;
                rdfs:label ?label. 
        }"""

    sh "echo '' > 'datetime-codes.ttl'"
    for (def dataSetTtl : dataSetTtlFilePaths) {
        // Automatically generate concepts and basic metadata for all datetime values we have in each dataset.
        sh "sparql --query 'date-time-code-list-gen.sparql' --data '${dataSetTtl}' >> 'datetime-codes.ttl'"
        // One codelist may be spread across multiple files so we need to bring all the concepts together before
        // splitting them out into one file per ConceptScheme.
    }

    sh "sparql --data 'datetime-codes.ttl' --query 'concept-scheme-uris.sparql' --results JSON > 'concept-scheme-uris.json'"
    def conceptSchemesQueryResult = readJSON(text: readFile(file: "concept-scheme-uris.json"))
    def generatedConceptSchemes = conceptSchemesQueryResult.results.bindings.toList().collect {
        [
            uri: it.conceptScheme.value,
            label: it.label.value
        ]
    }

    for (def conceptScheme : generatedConceptSchemes) {
        def conceptSchemeFileNameBase = conceptScheme.uri.replaceAll("[^A-Za-z0-9]+", "-")
        def outFileBase = "${outFolderPath}/${conceptSchemeFileNameBase}"
        def ttlOutFile= "${outFileBase}.ttl"

        // Pull in any datetime Concepts & metadata belonging to this ConceptScheme we have generated.
        writeFile file: "extract-datetime-concept-scheme.sparql", text: """
            PREFIX skos:    <http://www.w3.org/2004/02/skos/core#>
            CONSTRUCT {
                ?conceptScheme ?csp ?cso.
                ?concept ?cp ?co.
            } 
            WHERE {
                {
                    SELECT DISTINCT ?conceptScheme ?csp ?cso
                    WHERE {
                        ?conceptScheme a skos:ConceptScheme.

                        # Get all triples related to the concept scheme.
                        ?conceptScheme ?csp ?cso. 
                    }
                } UNION {
                    SELECT DISTINCT ?concept ?cp ?co
                    WHERE {
                        ?concept skos:inScheme <${conceptScheme.uri}>.
                        
                        # Get all triples related to the concept in this scheme.
                        ?concept ?cp ?co.
                    }
                }
            }
        """
        sh "sparql --data 'datetime-codes.ttl' --query 'extract-datetime-concept-scheme.sparql' >> '${ttlOutFile}'"

        // Need to generate catalogue metadata
        def ttlContents = readFile(file: ttlOutFile)
        def catalogueMetadataSparql = util.getCatalogMetadata(
                conceptScheme.uri,
                new CatalogMetadata([
                    identifier: conceptScheme.uri,
                    catalogSchemeUri: conceptScheme.uri,
                    catalogUri: "http://gss-data.org.uk/catalog/vocabularies",
                    newIdentifierBasePath: "http://gss-data.org.uk/data/gss_data/time/",
                    label: conceptScheme.label,
                    dtIssued: "2000-01-01T00:00:00Z",
                    dtModified: "2000-01-01T00:00:00Z"
                ])
        )

        writeFile(file: ttlOutFile, text: "${ttlContents} \n ${catalogueMetadataSparql}")

        sh "sparql --data='${ttlOutFile}' --query=graphs.sparql --results=JSON > '${outFileBase}-graphs.json'"

        codeLists.augment(ttlOutFile)
        codeLists.zip(ttlOutFile)
    }



    // Don't want anything later in the pipeline to try and upload this data.
    sh "rm 'datetime-codes.ttl'"
}