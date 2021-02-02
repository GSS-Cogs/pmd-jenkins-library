def call() {
    if (!Boolean.parseBoolean(env.SUPPRESS_JUNIT)) {
        junit allowEmptyResults: true, testResults: 'reports/**/*.xml'
    }
}