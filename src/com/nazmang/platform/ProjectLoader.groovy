package com.nazmang.platform

class ProjectLoader {

    def steps

    ProjectLoader(steps) {
        this.steps = steps
    }

    def load(String project) {

        def yaml = steps.readYaml file: "${project}/deploy.yaml"
        return yaml
    }

    def findAllProjects() {

        def dirs = steps.sh(
            script: "ls -d */ | sed 's#/##'",
            returnStdout: true
        ).trim().split("\n")

        return dirs
    }
}
