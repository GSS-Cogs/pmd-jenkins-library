package uk.org.floop.jenkins_pmd

class PMD implements Serializable {
    PMDConfig config
    Drafter drafter
    String clientID, clientSecret

    PMD(PMDConfig config, clientID, clientSecret) {
        this.config = config
        this.clientID = clientID
        this.clientSecret = clientSecret
        this.drafter = new Drafter(this)
    }
}
