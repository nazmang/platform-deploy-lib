/**
 * Returns a list of project directory names that contain deploy.yaml.
 * Use after checkout to populate a choice parameter (e.g. input step).
 * Example: def choices = [''] + platformProjectChoices()
 */
def call() {
    def projectLoader = new com.nazmang.platform.ProjectLoader(this)
    return projectLoader.findProjectDirsWithDeployYaml()
}
