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

                            if (cfg.critical) {
                                steps.error("Critical cluster ${c} failed.")
                            } else {
                                steps.echo("Non-critical cluster ${c} failed. Skipping.")
                            }
                        }
                    }
                }
            }]
        }
    }
}
