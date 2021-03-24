package uk.org.floop.jenkins_pmd

class SparqlQueries {
    private static String getRawSparqlQuery(SparqlQuery queryType) {
        switch (queryType) {
            case SparqlQuery.SkosNarrowerAugmentation:
                return skosNarrowerAugmentationQuery
            case SparqlQuery.SkosTopConceptAugmentation:
                return skosTopConceptAugmentationQuery
            case SparqlQuery.DateTimeCodeListGeneration:
                return dateTimeCodeListGenerationQuery
            default:
                throw new IllegalArgumentException("Unmatched SparqlQuery type '${queryType}'")
        }
    }

    static String getSparqlQuery(SparqlQuery queryType, boolean insertsRequired) {
        def rawSparqlQuery = getRawSparqlQuery(queryType)

        if (insertsRequired) {
            return rawSparqlQuery.replaceAll("\\s+CONSTRUCT\\s+\\{", "\n INSERT {")
        }

        return rawSparqlQuery
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


    /**
     * Used to put time resources into a ConceptScheme.
     */
    private static String dateTimeCodeListGenerationQuery = """
PREFIX qb: <http://purl.org/linked-data/cube#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
CONSTRUCT {
    ?codelist a skos:ConceptScheme ;
        skos:hasTopConcept ?time.
        
    ?time skos:inScheme ?codelist .
}
WHERE {
    {
        SELECT DISTINCT ?codelist ?time
        WHERE {
            ?dimension a qb:DimensionProperty ;
                rdfs:subPropertyOf <http://purl.org/linked-data/sdmx/2009/dimension#refPeriod> ;
                qb:codeList ?codelist ;
                .
            ?observation ?dimension ?time .
        }
    }
}
        """

}