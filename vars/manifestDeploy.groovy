def call(Map cfg) {

    echo "Applying manifest file: ${cfg.file}"

    def nsArg = cfg.namespace ? " -n ${cfg.namespace}" : ""
    sh "kubectl apply -f ${cfg.file}${nsArg}"
}
