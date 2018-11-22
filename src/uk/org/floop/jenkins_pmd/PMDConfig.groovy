package uk.org.floop.jenkins_pmd

class PMDConfig implements Serializable {
    static final String UA = "uk.org.floop.jenkins_pmd/0.1"
    String pmd_api
    String credentials
    String pipeline_api
    String default_mapping
    String base_uri

}
