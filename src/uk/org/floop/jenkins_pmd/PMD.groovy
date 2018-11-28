package uk.org.floop.jenkins_pmd

class PMD implements Serializable {
    PMDConfig config
    Drafter drafter
    Pipelines pipelines

    PMD(PMDConfig config, String user, String pass, String cacheUser, String cachePass) {
        this.config = config
        this.drafter = new Drafter(this, user, pass, cacheUser, cachePass)
        this.pipelines = new Pipelines(this, user, pass)
    }
}
