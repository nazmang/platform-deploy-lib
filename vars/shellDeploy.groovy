def call(Map cfg) {

    def singleCmd = cfg.command
    def multiCmd  = cfg.commands

    if (singleCmd != null && multiCmd != null) {
        error "shell config: use either 'command' or 'commands', not both"
    }
    if (singleCmd == null && multiCmd == null) {
        error "shell config: provide 'command' (string) or 'commands' (list of strings)"
    }

    if (singleCmd != null) {
        if (!(singleCmd instanceof String)) {
            error "shell config: 'command' must be a string"
        }
        echo "Running shell: ${singleCmd}"
        sh singleCmd
        return
    }

    // commands: list of strings
    if (!(multiCmd instanceof List)) {
        error "shell config: 'commands' must be a list of strings"
    }
    for (int i = 0; i < multiCmd.size(); i++) {
        def cmd = multiCmd[i]
        if (!(cmd instanceof String)) {
            error "shell config: 'commands[${i}]' must be a string"
        }
        echo "Running shell (${i + 1}/${multiCmd.size()}): ${cmd}"
        sh cmd
    }
}
