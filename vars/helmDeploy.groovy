def call(Map cfg) {

    echo "Deploying Helm chart: ${cfg.chartName}"

    // Plugin (latest) expects: chartPath, repositories, releaseName, valuesFile (optional), additionalArgs, helmInstallation (optional)
    def chartPath = cfg.chartName?.contains('/') ? cfg.chartName : "${cfg.repoName}/${cfg.chartName}"
    def repos = [[name: cfg.repoName, url: cfg.repoUrl]]
    def args = ["--namespace", cfg.namespace, "--create-namespace"]
    if (cfg.version) {
        args.addAll(['--version', cfg.version])
    }
    // Resolve Helm values files relative to the current pipeline directory (pwd()),
    // then pass them via `-f` with quoting. This avoids "no such file" when nested
    // deploys change the effective working directory inside the Helm plugin/container,
    // and it also handles workspace paths that contain spaces.
    if (cfg.values) {
        def baseDir = pwd()
        cfg.values.each { v ->
            if (!v) return
            def p = v.toString().trim()
            if (!p) return
            // If not absolute, resolve relative to the current working directory.
            // If that file doesn't exist, fall back to repo root (${WORKSPACE})
            // since some projects keep shared values under top-level `environments/...`.
            if (!p.startsWith('/')) {
                def resolved = new File(baseDir, p).path
                if (!fileExists(resolved) && env.WORKSPACE) {
                    def workspaceResolved = new File(env.WORKSPACE, p).path
                    if (fileExists(workspaceResolved)) {
                        resolved = workspaceResolved
                    }
                }
                p = resolved
            }
            // Shell-quote for safe inclusion in the plugin's constructed command line.
            def escaped = p.replace('"', '\\"')
            args.addAll(['-f', "\"${escaped}\""])
        }
    }
    def additionalArgs = args.join(' ')

    def stepArgs = [
        releaseName: cfg.releaseName,
        chartPath: chartPath,
        repositories: repos,
        additionalArgs: additionalArgs
    ]
    if (cfg.helmInstallation?.trim()) {
        stepArgs.helmInstallation = cfg.helmInstallation.trim()
    }
    helm(stepArgs)
}
