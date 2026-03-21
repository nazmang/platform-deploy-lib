def call(Map cfg) {

    def files = cfg.files
    if (files == null) {
        error "decrypt-sops config: 'files' is required (list of paths relative to project directory)"
    }
    if (!(files instanceof List)) {
        error "decrypt-sops config: 'files' must be a list"
    }
    if (files.isEmpty()) {
        error "decrypt-sops config: 'files' must not be empty"
    }

    def extraArgs = cfg.extraArgs
    if (extraArgs == null) {
        extraArgs = ""
    } else if (extraArgs instanceof List) {
        extraArgs = extraArgs.collect { it.toString() }.join(" ")
    } else if (!(extraArgs instanceof String)) {
        error "decrypt-sops config: 'extraArgs' must be a string or list of strings"
    }

    for (int i = 0; i < files.size(); i++) {
        def f = files[i]
        if (!(f instanceof String)) {
            error "decrypt-sops config: 'files[${i}]' must be a string"
        }
        if (!fileExists(f)) {
            error "decrypt-sops: file not found: ${f}"
        }
        echo "SOPS decrypt (in-place): ${f}"
        def cmd = "sops --decrypt --in-place"
        if (extraArgs) {
            cmd += " ${extraArgs}"
        }
        cmd += " ${f}"
        sh cmd
    }
}
