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
            }]
        }
    }
}
