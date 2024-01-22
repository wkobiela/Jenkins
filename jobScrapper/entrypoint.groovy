/* groovylint-disable DuplicateStringLiteral, MethodReturnTypeRequired, NestedBlockDepth, NoDef */
import hudson.EnvVars
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl
import hudson.model.Cause

// Map parallelStages = [:]
pythonsArray = ['3.9', '3.10', '3.11', '3.12']
runAndTestStage = 'jobScrapperCI/build_run_test'
banditStage = 'jobScrapperCI/run_bandit'
def upstreamEnv = new EnvVars()

def generateStage(String job, String url, String commit, String changeid, String python) {
    String stageName = job.replace('jobScrapperCI/', '')
    if (python != 'None') {
        stageName = "${stageName}_python${python}"
    }
    return {
        stage("Stage: ${stageName}") {
            build job: "${job}",
            parameters: [string(name: 'Repo_url', value: "${url}"),
                        string(name: 'Commit', value: "${commit}"),
                        string(name: 'Change_ID', value: "${changeid}"),
                        string(name: 'Python', value: "${python}"),
                        booleanParam(name: 'propagateStatus', value: true)
                        ],
            wait: true
        }
    }
}

pipeline {
    agent none
    stages {
        stage('Get upstream vars') {
            steps {
                script {
                    def upstreamCause = currentBuild.rawBuild.getCause(Cause$UpstreamCause)
                    if (upstreamCause) {
                        def upstreamJobName = upstreamCause.properties.upstreamProject
                        println(upstreamJobName)
                        def upstreamBuild = Jenkins.instance
                                                .getItemByFullName(upstreamJobName)
                                                .getLastBuild()
                        println(upstreamBuild)
                        upstreamEnv = upstreamBuild.getAction(EnvActionImpl).getEnvironment()
                    }
                }
                echo upstreamEnv.BUILD_USER_ID
            }
        }
        stage('Get changeset') {
            steps {
                echo 'INFORMATION FROM SCM:\n' +
                "URL: ${upstreamEnv.GIT_URL} \n" +
                "Commit: ${upstreamEnv.GIT_COMMIT} \n" +
                "Change ID: ${upstreamEnv.CHANGE_ID} \n" +
                "Build user ID: ${upstreamEnv.BUILD_USER_ID} \n" +
                "Change author: ${upstreamEnv.CHANGE_AUTHOR}"
                script {
                    currentBuild.description =
                    "URL: <a href='${upstreamEnv.GIT_URL}'>${upstreamEnv.GIT_URL}</a><br>" +
                    "Commit: <b>${upstreamEnv.GIT_COMMIT}</b><br>" +
                    "Change ID: <b>${upstreamEnv.CHANGE_ID}</b><br>" +
                    "Build user ID: <b>${upstreamEnv.BUILD_USER_ID}</b><br>" +
                    "Change author: <b>${upstreamEnv.CHANGE_AUTHOR}</b>"

                    // pythonsArray.each { py ->
                    //     parallelStages.put("${runAndTestStage}_python${py}",
                    //                   generateStage(runAndTestStage, env.GIT_URL, env.GIT_COMMIT, env.CHANGE_ID, py))
                    // }
                    // parallelStages.put("${banditStage}",
                    //     generateStage(banditStage, env.GIT_URL, env.GIT_COMMIT, env.CHANGE_ID, 'None'))
                }
            }
        }
        stage('Run CI') {
            agent none
            steps {
                script {
                    echo 'Started CI'
                    // parallel parallelStages
                    }
            }
        }
    }
}