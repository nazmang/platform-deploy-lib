def call(Map cfg) {

    def rawFile = cfg.file
    def rawFiles = cfg.files
    if (rawFile != null && rawFiles != null) {
        error "manifest config: use only one of 'file' or 'files'"
    }

    def paths = []
    if (rawFiles != null) {
        if (!(rawFiles instanceof List)) {
            error "manifest config: 'files' must be a list of strings"
        }
        if (rawFiles.isEmpty()) {
            error "manifest config: 'files' must not be empty"
        }
        for (int i = 0; i < rawFiles.size(); i++) {
            def p = rawFiles[i]
            if (!(p instanceof String) || !p.toString().trim()) {
                error "manifest config: 'files[${i}]' must be a non-empty string"
            }
            paths.add(p.toString().trim())
        }
    } else if (rawFile != null) {
        if (rawFile instanceof List) {
            for (int i = 0; i < rawFile.size(); i++) {
                def p = rawFile[i]
                if (!(p instanceof String) || !p.toString().trim()) {
                    error "manifest config: 'file[${i}]' must be a non-empty string"
                }
                paths.add(p.toString().trim())
            }
            if (paths.isEmpty()) {
                error "manifest config: 'file' list must not be empty"
            }
        } else if (rawFile instanceof String) {
            if (!rawFile.trim()) {
                error "manifest config: 'file' must not be empty"
            }
            paths.add(rawFile.trim())
        } else {
            error "manifest config: 'file' must be a string or list of strings (got ${rawFile.getClass().getSimpleName()})"
        }
    } else {
        error "manifest config: provide 'file' or 'files'"
    }

    echo "Applying manifest file(s): ${paths.join(', ')}"

    def nsArg = cfg.namespace ? " -n ${cfg.namespace}" : ""
    def fileArgs = paths.collect { p -> "-f '" + p.replace("'", "'\\''") + "'" }.join(' ')
    sh "kubectl apply ${fileArgs}${nsArg}"
}
