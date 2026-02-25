package com.nazmang.platform

class ChangeDetector {

    def steps

    ChangeDetector(steps) {
        this.steps = steps
    }

    def detectChangedProjects() {

        def changed = []

        steps.currentBuild.changeSets.each { cs ->
            cs.items.each { item ->
                item.affectedFiles.each { f ->
                    def topDir = f.path.tokenize('/')[0]
                    if (!changed.contains(topDir)) {
                        changed << topDir
                    }
                }
            }
        }

        return changed
    }
}
