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

    def keyCred = cfg.keyCredentialId
    def keyParam = cfg.keyParameter
    if (keyCred && keyParam) {
        error "decrypt-sops config: use only one of 'keyCredentialId' or 'keyParameter'"
    }

    def extraArgs = cfg.extraArgs
    if (extraArgs == null) {
        extraArgs = ""
    } else if (extraArgs instanceof List) {
        extraArgs = extraArgs.collect { it.toString() }.join(" ")
    } else if (!(extraArgs instanceof String)) {
        error "decrypt-sops config: 'extraArgs' must be a string or list of strings"
    }

    def keyCheckCfg = cfg.keyCheck
    def runDecryptFiles = {
        if (keyCheckCfg != null) {
            shellDeploy(keyCheckCfg)
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

    if (!keyCred && !keyParam) {
        runDecryptFiles()
        return
    }

    if (keyParam != null) {
        if (!(keyParam instanceof String) || !keyParam.trim()) {
            error "decrypt-sops config: 'keyParameter' must be a non-empty string (pipeline parameter name)"
        }
        def pv = params[keyParam]
        if (pv == null || pv.toString().trim().isEmpty()) {
            error "decrypt-sops: pipeline parameter '${keyParam}' is missing or empty"
        }
        def envVarName = cfg.keyEnvVar ?: 'SOPS_AGE_KEY'
        withEnv(["${envVarName}=${pv}"]) {
            runDecryptFiles()
        }
        return
    }

    def keyIsFile = cfg.keyCredentialIsFile == true
    def envVarName = cfg.keyEnvVar ?: (keyIsFile ? 'SOPS_AGE_KEY_FILE' : 'SOPS_AGE_KEY')

    if (keyIsFile) {
        withCredentials([file(credentialsId: keyCred, variable: 'SOPS_KEY_FILE_PATH')]) {
            def keyPath = sh(script: 'echo -n "$SOPS_KEY_FILE_PATH"', returnStdout: true).trim()
            if (!keyPath) {
                error "decrypt-sops: file credential '${keyCred}' did not resolve to a path"
            }
            withEnv(["${envVarName}=${keyPath}"]) {
                runDecryptFiles()
            }
        }
    } else {
        withCredentials([string(credentialsId: keyCred, variable: envVarName)]) {
            runDecryptFiles()
        }
    }
}
