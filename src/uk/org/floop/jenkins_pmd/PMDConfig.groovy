package uk.org.floop.jenkins_pmd

class PMDConfig implements Serializable {
    static final String UA = "uk.org.floop.jenkins_pmd/0.2"
    String pmd_api
    String oauth_token_url
    String oauth_audience
    String credentials
    String default_mapping
    String base_uri
    Boolean mock_drafter = false
}
