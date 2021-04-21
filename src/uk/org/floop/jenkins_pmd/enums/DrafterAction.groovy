package uk.org.floop.jenkins_pmd.enums

enum DrafterAction {
    Query,
    Update

    static String toActionPath(DrafterAction action) {
        switch(action){
            case Query:
                return "query"
            case Update:
                return "update"
            default:
                throw new IllegalArgumentException("Unmatched drafter action ${action}")
        }
    }
}