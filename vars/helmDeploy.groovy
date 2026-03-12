def call(Map cfg) {

    echo "Deploying Helm chart: ${cfg.chartName}"

    // Plugin (latest) expects: chartPath, repositories, releaseName, valuesFile (optional), additionalArgs, helmInstallation (optional)
    def chartPath = cfg.chartName?.contains('/') ? cfg.chartName : "${cfg.repoName}/${cfg.chartName}"
    def repos = [[name: cfg.repoName, url: cfg.repoUrl]]
    def args = ["--namespace", cfg.namespace]
    if (cfg.version) {
        args.addAll(['--version', cfg.version])
    }
    // valuesFile: single file (plugin default 'values.yaml'); for multiple files use -f in additionalArgs
    def valuesFile = null
    if (cfg.values) {
        if (cfg.values.size() == 1) {
            valuesFile = cfg.values[0]
        } else {
            cfg.values.each { v -> args.addAll(['-f', v]) }
        }
    }
    def additionalArgs = args.join(' ')

    def stepArgs = [
        releaseName: cfg.releaseName,
        chartPath: chartPath,
        repositories: repos,
        additionalArgs: additionalArgs
    ]
    if (valuesFile != null) {
        stepArgs.valuesFile = valuesFile
    }
    if (cfg.helmInstallation?.trim()) {
        stepArgs.helmInstallation = cfg.helmInstallation.trim()
    }
    helm(stepArgs)
}
