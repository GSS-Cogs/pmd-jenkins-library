package uk.org.floop.jenkins_pmd.models

class CatalogMetadata {
    String identifier
    String catalogSchemeUri
    String catalogUri
    String newIdentifierBasePath
    String label
    String comment
    String dtIssued
    String dtModified
    String licenseUri
    String creatorUri
    String publisherUri
    String contactPointUri
    String landingPageUri
    String markdownDescription
    
    CatalogMetadata(Object jsonObj) {
        def requiredProperties = ["identifier", "catalogSchemeUri", "catalogUri", "newIdentifierBasePath", "label"]
        def optionalProperties = ["comment", "dtIssued", "dtModified", "licenseUri", "creatorUri", "publisherUri",
                                  "contactPointUri", "landingPageUri", "markdownDescription"]

        for (def prop : requiredProperties) {
            if (jsonObj[prop] == null) {
                throw new IllegalArgumentException("JSON is missing required property '${prop}'.")
            }
            this.setProperty(prop, jsonObj[prop])
        }

        for (def prop : optionalProperties) {
            if (jsonObj[prop] != null) {
                this.setProperty(prop, jsonObj[prop])
            }
        }
    }
}
