package uk.org.floop.jenkins_pmd.helpers

import java.util.regex.Pattern

class JobHelpers {
    /**
     * Returns (label, url) describing the info.json used in this build.
     * Used to describe the providence of data.
     * @param gitRemoteUrl
     * @param gitCommitHash
     * @param dataSetDir
     * @return
     */
    public static String[] getMaybeInfoJsonLabelAndUrl(String gitRemoteUrl, String gitCommitHash, String datasetDir) {
        if (gitRemoteUrl == null || gitCommitHash == null || datasetDir == null) {
            println("ERROR: null argument passed to getMaybeInfoJsonLabelAndUrl ${gitRemoteUrl}/${gitCommitHash}/${datasetDir}.".toString())
            return [null, null]
        }

        // Expecting a gitRemoteUrl of the form:
        // "git@github.com:GSS-Cogs/pmd-jenkins-library.git"
        // OR "https://github.com/GSS-Cogs/family-covid-19"
        def regex = Pattern.compile("^.*?GSS-Cogs/(.+?)(.git)?\$")
        def matches = regex.matcher(gitRemoteUrl)

        if (!matches.matches()) {
            println("ERROR: Could not comprehend git remote URL '${gitRemoteUrl}'.".toString())
            return [null, null]
        }

        String repoName = matches.group(1)
        String repoBaseUrl = "https://github.com/GSS-Cogs/${repoName}"
        String infoJsonAtCommitUrl = "${repoBaseUrl}/tree/${gitCommitHash}/${datasetDir}/info.json"
        String infoJsonCommitLabel = "info.json inside ${repoName}/${datasetDir}"

        println("InfoJsonCommitUrl: ${infoJsonAtCommitUrl}")
        return [infoJsonCommitLabel, infoJsonAtCommitUrl]
    }
}
