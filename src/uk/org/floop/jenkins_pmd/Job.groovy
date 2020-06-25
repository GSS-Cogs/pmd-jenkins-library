package uk.org.floop.jenkins_pmd

import org.jenkinsci.plugins.uniqueid.IdStore

class Job {
    static String getID(build) {
        def job = build.rawBuild.getParent()
        String id = IdStore.getId(job)
        if (id == null) {
            IdStore.makeId(job)
            id = IdStore.getId(job)
        }
        return id
    }
}
