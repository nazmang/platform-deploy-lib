/**
 * Reject commands that are empty or are a bare YAML block-scalar indicator.
 * deploy.yaml is read by YamlParser, which has no block scalars: a folded
 * `command: >-` arrives as the literal string ">-", `sh` runs it as an empty
 * script and exits 0 — a green build that did nothing.
 */
def checkCommand(String cmd, String where) {
    def c = cmd.trim()
    if (!c) {
        error "shell config: ${where} is empty"
    }
    if (c ==~ /[|>][-+]?/) {
        error "shell config: ${where} is '${c}' — block scalars (| and >) are not supported in deploy.yaml; write the command on one line or use 'commands'"
    }
}

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
        checkCommand(singleCmd, "'command'")
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
        checkCommand(cmd, "'commands[${i}]'")
        echo "Running shell (${i + 1}/${multiCmd.size()}): ${cmd}"
        sh cmd
    }
}
