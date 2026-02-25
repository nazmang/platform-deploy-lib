def call(Map config = [:]) {

    def clusterManager = new com.nazmang.platform.ClusterManager(this)
    def changeDetector = new com.nazmang.platform.ChangeDetector(this)
    def projectLoader  = new com.nazmang.platform.ProjectLoader(this)

    def projects = []

    if (params.PROJECT?.trim()) {
        projects = [params.PROJECT]
    } else if (params.FORCE_DEPLOY) {
        projects = projectLoader.findAllProjects()
    } else {
        projects = changeDetector.detectChangedProjects()
    }

    if (projects.isEmpty()) {
        echo "No projects to deploy."
        return
    }

    clusterManager.executeOnTargets { String clusterName ->

        for (p in projects) {
            deployProject(project: p, cluster: clusterName)
        }
    }
}
