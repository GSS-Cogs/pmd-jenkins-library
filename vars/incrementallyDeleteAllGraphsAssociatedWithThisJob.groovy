def call(body) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent {
            label 'master'
        }
        stages {
            stage('Incrementally delete & publish each graph associated with Job') {
                steps {
                    script {
                        util.incrementallyDeleteAndPublishAllGraphsForJob()
                    }
                }
            }
        }
    }
}