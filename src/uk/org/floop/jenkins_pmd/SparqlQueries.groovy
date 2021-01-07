package uk.org.floop.jenkins_pmd

class SparqlQueries {
    static String getSparqlQuery(SparqlQuery queryType){
        switch(queryType){
            case SparqlQuery.SkosNarrowerAugmentation:
                return skosNarrowerAugmentationQuery
            case SparqlQuery.SkosTopConceptAugmentation:
                return skosTopConceptAugmentationQuery
            default:
                throw new IllegalArgumentException("Unmatched SparqlQuery type '${queryType}'")
        }
    }

    /**
     * Used to add skos:narrower to appropriate Concepts within a ConceptScheme.
     * Used skos:broader to infer skos:narrower.
     */
    private static String skosNarrowerAugmentationQuery = """
CONSTRUCT {
    ?broaderConcept <http://www.w3.org/2004/02/skos/core#narrower> ?concept.
}
WHERE {
    ?conceptScheme 
        <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2004/02/skos/core#ConceptScheme>.
    ?concept 
        <http://www.w3.org/2004/02/skos/core#inScheme> ?conceptScheme;
        <http://www.w3.org/2004/02/skos/core#broader> ?broaderConcept.
        FILTER NOT EXISTS {
            ?broaderConcept <http://www.w3.org/2004/02/skos/core#narrower> ?concept.
        }
}
        """

    /**
     * Used to add skos:hasTopConcept to top-level Concepts within a ConceptScheme.
     */
    private static String skosTopConceptAugmentationQuery = """
CONSTRUCT {
    ?conceptScheme <http://www.w3.org/2004/02/skos/core#hasTopConcept> ?concept.
}
WHERE {
    ?conceptScheme <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2004/02/skos/core#ConceptScheme>.

    ?concept <http://www.w3.org/2004/02/skos/core#inScheme> ?conceptScheme.

        FILTER NOT EXISTS {
            # Find concepts which don't have anything broader, they are by definition topConcepts.
            ?concept <http://www.w3.org/2004/02/skos/core#broader> ?broaderConcept.
        }
        FILTER NOT EXISTS {
            # Ensure we don't add topConcept where it is already set.
            ?conceptScheme <http://www.w3.org/2004/02/skos/core#hasTopConcept> ?concept.
        }
}
        """
}