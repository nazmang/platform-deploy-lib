def call(Map config) {

    def projectLoader = new com.nazmang.platform.ProjectLoader(this)
    def spec = projectLoader.load(config.project)

    // Optional dependency tracking: deploy dependencies first, avoid duplicates and cycles
    def state = config.state
    if (state == null) {
        state = [deployed: [] as Set, deploying: [] as Set]
    }
    def project = config.project
    def depends = spec.depends ?: []

    if (state.deploying.contains(project)) {
        error "Circular dependency detected involving project: ${project}"
    }
    state.deploying.add(project)

    for (dep in depends) {
        dep = dep?.trim()
        if (!dep) continue
        if (state.deployed.contains(dep)) continue
        if (!fileExists("${dep}/deploy.yaml")) {
            error "Dependency '${dep}' has no deploy.yaml in this repository."
        }
        echo "Deploying dependency: ${dep} (required by ${project})"
        deployProject(project: dep, cluster: config.cluster, state: state)
    }

    state.deploying.remove(project)

    def envName = config.cluster ?: params.CLUSTER
    if (!envName) {
        error "Cluster name not set. Pass cluster when using DEPLOY_TARGET=all, or set CLUSTER parameter."
    }

    dir(config.project) {

        for (step in spec.steps) {

            // Skip step if onlyEnvs is set and current env is not in the list
            if (step.onlyEnvs && !step.onlyEnvs.contains(envName)) {
                continue
            }

            def stepConfig = step.environments ?
                             step.environments[envName]?.config :
                             step.config

            if (!stepConfig) {
                error "No config for environment ${envName}"
            }

            switch (step.type) {

                case 'helm':
                    helmDeploy(stepConfig)
                    break

                case 'kustomize':
                    kustomizeDeploy(stepConfig)
                    break

                case 'manifest':
                    manifestDeploy(stepConfig)
                    break

                case 'wait':
                    waitDeploy(stepConfig)
                    break

                case 'shell':
                    shellDeploy(stepConfig)
                    break

                default:
                    error "Unknown step type: ${step.type}"
            }
        }
    }

    state.deployed.add(project)
}
