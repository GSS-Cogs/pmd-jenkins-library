package uk.org.floop.jenkins_pmd

class PMD implements Serializable {
    PMDConfig config
    AbstractDrafter drafter
    String clientID, clientSecret
    String pmdPublicSparqlEndPoint

    PMD(PMDConfig config, clientID, clientSecret) {
        this.config = config
        this.clientID = clientID
        this.clientSecret = clientSecret
        this.drafter = config.mock_drafter
                ? new MockDrafter(this)
                : new Drafter(this)
        this.pmdPublicSparqlEndPoint = config.pmd_public_sparql_endpoint
    }
}
