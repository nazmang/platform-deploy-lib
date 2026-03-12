package com.nazmang.platform

class ClusterManager {

    def steps

    ClusterManager(steps) {
        this.steps = steps
    }

    def executeOnTargets(Closure body) {

        def clusterMap = [
            cloud : [ credential: "kubeconfig-hetzner", critical: true ],
            onprem: [ credential: "kubeconfig-onprem",  critical: false ]
        ]

        def targets = steps.params.DEPLOY_TARGET == "all" ?
                      clusterMap.keySet() :
                      [steps.params.CLUSTER]

        steps.parallel targets.collectEntries { c ->

            ["Deploy to ${c}": {

                def cfg = clusterMap[c]

                steps.container('deploy') {

                    steps.withCredentials([
                        steps.file(credentialsId: cfg.credential, variable: 'KUBECONFIG')
                    ]) {

                        // Copy kubeconfig to workspace so it's visible inside the container and to
                        // any child process (e.g. Helm Tool plugin); then force KUBECONFIG to that path.
                        // Otherwise the container may use the in-cluster ServiceAccount (e.g. system:serviceaccount:jenkins:default).
                        def kubeconfigInWorkspace = ".kubeconfig-deploy-${c}"
                        steps.sh "cp \"\${KUBECONFIG}\" \"\${WORKSPACE}/${kubeconfigInWorkspace}\""
                        steps.withEnv(["KUBECONFIG=${steps.env.WORKSPACE}/${kubeconfigInWorkspace}"]) {

                            try {

                                steps.sh "kubectl cluster-info"

                                body(c)

                            } catch (err) {

                            def reason = err.message ?: err.toString()
                            if (cfg.critical) {
                                steps.error("Deploy to ${c} failed: ${reason}")
                            } else {
                                steps.echo("Deploy to ${c} failed (non-critical): ${reason}. Skipping.")
                            }
                        }
                        }
                    }
                }
            }]
        }
    }
}
