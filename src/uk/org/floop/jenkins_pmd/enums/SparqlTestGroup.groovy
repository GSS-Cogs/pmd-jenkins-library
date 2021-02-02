package uk.org.floop.jenkins_pmd.enums

enum SparqlTestGroup {
    PMD,
    QB,
    SKOS

    static String toDirectoryName(SparqlTestGroup testGroup) {
        switch(testGroup) {
            case SparqlTestGroup.PMD:
                return "pmd"
            case SparqlTestGroup.QB:
                return "qb"
            case SparqlTestGroup.SKOS:
                return "skos"
            default:
                throw new IllegalArgumentException("Unmatched SparqlTestGroup ${testGroup}")
        }
    }
}
