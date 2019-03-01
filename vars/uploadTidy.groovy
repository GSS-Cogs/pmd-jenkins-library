import uk.org.floop.jenkins_pmd.PMD

def call(csvs, String mapping=null, String datasetPath=null, String metadata=null) {
    if (!datasetPath) {
        datasetPath = util.slugise(env.JOB_NAME)
    }
    PMD pmd = pmdConfig("pmd")
    if (!mapping) {
        if (fileExists('metadata/columns.csv')) {
            mapping = 'metadata/columns.csv'
        } else {
            mapping = config['default_mapping']
        }
    }
    if (!mapping.startsWith('http')) {
        mapping = "${WORKSPACE}/${mapping}"
    }

    dataset.delete(datasetPath)

    def draft = jobDraft.find()

    if (metadata) {
        pmd.drafter.addData(draft.id as String,"${WORKSPACE}/out/${metadata}","application/trig","UTF-8")
    } else {
        pmd.drafter.addData(draft.id as String,"${WORKSPACE}/out/dataset.trig","application/trig","UTF-8")
    }

    csvs.each { csv ->
        echo "Uploading ${csv}"
        pmd.pipelines.dataCube(draft.id as String, "${WORKSPACE}/${csv}", '', datasetPath, mapping)
    }
}
