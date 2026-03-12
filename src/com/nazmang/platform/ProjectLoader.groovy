package com.nazmang.platform

class ProjectLoader {

    def steps

    ProjectLoader(steps) {
        this.steps = steps
    }

    def load(String project) {

        def yamlText = steps.readFile file: "${project}/deploy.yaml", encoding: 'UTF-8'
        return com.nazmang.platform.YamlParser.parse(yamlText)
    }

    def findAllProjects() {

        def dirs = steps.sh(
            script: "ls -d */ | sed 's#/##'",
            returnStdout: true
        ).trim().split("\n")

        return dirs
    }

    /** Returns only directory names that contain deploy.yaml (valid project dirs). */
    def findProjectDirsWithDeployYaml() {
        def dirs = findAllProjects()
        return dirs.findAll { it?.trim() && steps.fileExists("${it}/deploy.yaml") }
    }
}
