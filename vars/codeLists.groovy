import org.jenkinsci.plugins.pipeline.utility.steps.fs.FileWrapper

def augment(String ttlFilePath) {
    // Augment the CodeList hierarchy with skos:Narrower and skos:hasTopConcept
    // annotations. Add the resulting triples on to the end of the .ttl file.
    // These annotations are required to help the PMD 'Reference Data' section
    // function correctly.
    sh "sparql --data='${ttlFilePath}' --query=skosNarrowerAugmentation.sparql >> '${ttlFilePath}'"
    sh "sparql --data='${ttlFilePath}' --query=skosTopConceptAugmentation.sparql >> '${ttlFilePath}'"
}

def zip(String ttlFilePath) {
    sh "cat '${ttlFilePath}' | pigz > '${ttlFilePath}.gz'"
    sh "rm '${ttlFilePath}'"
}

def convertCsvWsToRdf(Map config) {
    FileWrapper[] metadataFiles = config.get("files")
    def codelists = []
    for (def metadata : metadataFiles) {
        String baseName = metadata.name.substring(0, metadata.name.lastIndexOf('.csv-metadata.json'))
        String dirName = metadata.path.take(metadata.path.lastIndexOf('/'))
        codelists.add([
                "csv"   : "${dirName}/${baseName}.csv",
                "csvw"  : "${dirName}/${baseName}.csv-metadata.json",
                "output": "out/codelists/${baseName}"
        ])
    }

    for (def codelist : codelists) {
        String outFilePath = "${codelist.output}.ttl"
        sh "csv2rdf -t '${codelist.csv}' -u '${codelist.csvw}' -m annotated > '${outFilePath}'"
        sh "sparql --data='${outFilePath}' --query=graphs.sparql --results=JSON > '${codelist.output}-graphs.json'"

        augment(outFilePath)
        zip(outFilePath)
    }
}