def call(Map cfg) {

    echo "Applying kustomize overlay: ${cfg.overlay}"

    sh "kubectl apply -k ${cfg.overlay}"
}
