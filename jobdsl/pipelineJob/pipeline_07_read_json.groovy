def folderPath = "${FOLDER_PATH}"
def jobName = "${FOLDER_PATH}" + "/" + "07_read_json"
def jobDescription = "07_read_json"

pipelineJob(jobName) {
	description(jobDescription)
	logRotator {
        numToKeep(5)
        artifactNumToKeep(5)
    }
	keepDependencies(false)
	definition {
		cpsScm {
			scm {
				git {
					remote {
						github("${GIT_HUB_OWNER_AND_PROJECT}", "${GIT_HUB_PROTOCOL}")
					}
					branch("${GIT_HUB_BRANCH}")
				}
			}
			scriptPath("pipeline/decrative-pipeline/pipeline_07_read_json.groovy")
		}
	}
	disabled(false)
}