def call(Map config) {

    def projectLoader = new com.nazmang.platform.ProjectLoader(this)
    def spec = projectLoader.load(config.project)

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

                default:
                    error "Unknown step type: ${step.type}"
            }
        }
    }
}
