def call(Map cfg) {

    def kind = cfg.kind ?: error("wait config requires 'kind'")
    def name = cfg.name ?: error("wait config requires 'name'")
    def timeout = cfg.timeout ?: 120

    echo "Waiting for ${kind}/${name} (timeout: ${timeout}s)"

    if (kind == "CustomResourceDefinition") {
        sh "kubectl wait --for=condition=established crd/${name} --timeout=${timeout}s"
    } else {
        def nsArg = cfg.namespace ? " -n ${cfg.namespace}" : ""
        sh "kubectl wait --for=condition=available ${kind.toLowerCase()}/${name} --timeout=${timeout}s${nsArg}"
    }
}
