package uk.org.floop.jenkins_pmd

import groovy.text.SimpleTemplateEngine

class ConceptScheme implements Serializable {

    String csvFile, baseURI, id, label

    String metadata() {
        return """\
{
    "@context": "http://www.w3.org/ns/csvw",
    "@id": "${baseURI}#schema",
    "url": "${csvFile}",
    "tableSchema": {
        "columns": [
            {
                "titles": "Label",
                "name": "label",
                "datatype": "string",
                "required": true,
                "propertyUrl": "rdfs:label"
            },
            {
                "titles": "Notation",
                "name": "notation",
                "datatype": {
                    "base": "string",
                    "format": "^-?[\\\\w\\\\.\\\\/]+(-[\\\\w\\\\.\\\\/]+)*\$"
                },
                "required": true,
                "propertyUrl": "skos:notation"
            },
            {
                "titles": "Parent Notation",
                "name": "parent_notation",
                "datatype": {
                    "base": "string",
                    "format": "^(-?[\\\\w\\\\.\\\\/]+(-[\\\\w\\\\.\\\\/]+)*|)\$"
                },
                "required": false,
                "propertyUrl": "skos:broader",
                "valueUrl": "${baseURI}#concept/${id}/{parent_notation}"
            },
            {
                "titles": "Sort Priority",
                "name": "sort",
                "datatype": "integer",
                "required": false,
                "propertyUrl": "http://www.w3.org/ns/ui#sortPriority"
            },
            {
                "titles": "Description",
                "name": "description",
                "datatype": "string",
                "required": false,
                "propertyUrl": "rdfs:comment"
            },
            {
                "virtual": true,
                "propertyUrl": "rdf:type",
                "valueUrl": "skos:Concept"
            },
            {
                "virtual": true,
                "propertyUrl": "skos:inScheme",
                "valueUrl": "${baseURI}#scheme/${id}"
            }
        ],
        "primaryKey": "notation",
        "aboutUrl": "${baseURI}#concept/${id}/{notation}"
    },
    "prov:hadDerivation": {
        "@id": "${baseURI}#scheme/${id}",
        "@type": "skos:ConceptScheme",
        "rdfs:label": "${label}"
    }
}"""
    }

}
