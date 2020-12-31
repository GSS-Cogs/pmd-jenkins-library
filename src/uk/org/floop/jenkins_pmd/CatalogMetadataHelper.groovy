package uk.org.floop.jenkins_pmd

import uk.org.floop.jenkins_pmd.models.CatalogMetadata

import java.time.Instant

class CatalogMetadataHelper {
    static String getCatalogMetadata(CatalogMetadata metadata) {
        StringBuilder sb = new StringBuilder("""
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX dcat: <http://www.w3.org/ns/dcat#>
            PREFIX dc: <http://purl.org/dc/terms/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX markdown: <https://www.w3.org/ns/iana/media-types/text/markdown#>
            PREFIX pmdcat: <http://publishmydata.com/pmdcat#>
       """)

        String basePathWithoutTrailingSlash = metadata.newIdentifierBasePath.replaceAll("/+\$", "")
        String catalogMetaRecordsBasePath = "${basePathWithoutTrailingSlash}/${pathify(metadata.identifier)}"

        String datasetUri = "${catalogMetaRecordsBasePath}/dataset"
        String catalogRecordUri = "${catalogMetaRecordsBasePath}/catalog-record"

        String dtNow = Instant.now().toString()

        sb.append("""
            <${metadata.catalogSchemeUri}> rdf:type pmdcat:ConceptScheme.

            <${datasetUri}> 
                rdf:type pmdcat:Dataset, dcat:Dataset;
                pmdcat:datasetContents <${metadata.catalogSchemeUri}>;
                dc:title "${metadata.label}";
                rdfs:label "${metadata.label};
                rdfs:comment "${metadata.comment}.

            <${catalogRecordUri}> 
                rdf:type dcat:CatalogRecord;
                dc:issued: "${dtNow}"^^<xsd:dateTime>;
                dc:modified: "${dtNow}"^^<xsd:dateTime>;
                dc:title: "${metadata.label} Catalog Record";
                rdfs:label "${metadata.label} Catalog Record";
                foaf:primaryTopic <${datasetUri}>.


            <${metadata.catalogUri}> dcat:record <${catalogRecordUri}>.
        """)


        String[] optionalDatasetMetadata = buildOptionalMetadata(metadata)

        if (optionalDatasetMetadata.any()) {
            sb.append("<${datasetUri}> ${optionalDatasetMetadata.join("; ")}.")
        }

        sb.toString()
    }

    private static String[] buildOptionalMetadata(CatalogMetadata metadata) {
        def optionalDatasetMetadata = []
        if (metadata.licenseUri != null) {
            optionalDatasetMetadata.add("dc:license <${metadata.licenseUri}>")
        }

        if (metadata.creatorUri != null) {
            optionalDatasetMetadata.add("dc:creator <${metadata.creatorUri}>")
        }

        if (metadata.publisherUri != null) {
            optionalDatasetMetadata.add("dc:publisher <${metadata.publisherUri}>")
        }

        if (metadata.contactPointUri != null) {
            optionalDatasetMetadata.add("dcat:contactPoint <${metadata.contactPointUri}>")
        }

        if (metadata.landingPageUri != null) {
            optionalDatasetMetadata.add("dcat:landingPage <${metadata.landingPageUri}>")
        }

        if (metadata.dtIssued != null) {
            optionalDatasetMetadata.add("dc:issued \"${metadata.dtIssued}\"^^<xsd:dateTime>")
        }

        if (metadata.dtModified != null) {
            optionalDatasetMetadata.add("dc:modified \"${metadata.dtModified}\"^^<xsd:dateTime>")
        }

        if (metadata.markdownDescription != null) {
            optionalDatasetMetadata.add("pmdcat:markdownDescription \"${metadata.markdownDescription}\"^^<https://www.w3.org/ns/iana/media-types/text/markdown#Resource>")
        }
        optionalDatasetMetadata
    }


    private static String pathify(String input) {
        input.toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^\\w-]", "")
    }
}
