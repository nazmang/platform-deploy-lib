def call(Map config = [:]) {

    def clusterManager = new com.nazmang.platform.ClusterManager(this)
    def changeDetector = new com.nazmang.platform.ChangeDetector(this)
    def projectLoader  = new com.nazmang.platform.ProjectLoader(this)

    def projects = []
    // Allow override from runtime input (env.PROJECT_SELECTED) when set
    def projectOverride = env.PROJECT_SELECTED?.trim() ?: params.PROJECT?.trim()

    if (projectOverride) {
        projects = [projectOverride]
    } else if (params.FORCE_DEPLOY) {
        projects = projectLoader.findAllProjects()
    } else {
        projects = changeDetector.detectChangedProjects()
    }

    // Only deploy directories that contain deploy.yaml (skip top-level files like Jenkinsfile)
    def filtered = []
    for (p in projects) {
        if (fileExists("${p}/deploy.yaml")) {
            filtered.add(p)
        }
    }
    projects = filtered

    if (projects.isEmpty()) {
        echo "No projects to deploy."
        return
    }

    currentBuild.description = "Deploying: ${projects.join(', ')}"

    clusterManager.executeOnTargets { clusterName ->

        echo "Deploying to cluster: ${clusterName}"

        for (p in projects) {

            echo "---------------------------------------"
            echo "Deploying project: ${p}"
            echo "---------------------------------------"

            deployProject(project: p, cluster: clusterName)
        }
    }
}