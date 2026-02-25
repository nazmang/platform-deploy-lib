def call(Map cfg) {

    echo "Deploying Helm chart: ${cfg.chartName}"

    helm(
        repoName: cfg.repoName,
        repoUrl: cfg.repoUrl,
        chartName: cfg.chartName,
        releaseName: cfg.releaseName,
        namespace: cfg.namespace,
        version: cfg.version,
        values: cfg.values
    )
}
