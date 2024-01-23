/* groovylint-disable , CompileStatic, DuplicateStringLiteral, MethodReturnTypeRequired, NestedBlockDepth */
import hudson.EnvVars
import hudson.model.Cause$UpstreamCause

Map parallelStages = [:]
pythonsArray = ['3.9', '3.10', '3.11', '3.12']
runAndTestStage = 'jobScrapperCI/build_run_test'
banditStage = 'jobScrapperCI/run_bandit'
def upstreamEnv = new EnvVars()
List whitelist = ['wkobiela']

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

void addComment(String comment, String number) {
    try {
        withCredentials([string(credentialsId: 'github_token', variable: 'TOKEN')]) {
            cmd = """curl "https://api.github.com/repos/wkobiela/jobScrapper/issues/${number}/comments" \
            -H "Content-Type: application/json" \
            -H "Authorization: token """ + TOKEN + """\" \
            -X POST \
            -d "{\\"body\\": \\"${comment}\\"}\""""
            sh label: 'Add comment to Github PR', script: cmd
        }
    } catch (Exception ex) {
        echo "Cannot add comment. \n $ex"
    }
}

def getUpstreamVars() {
    try {
        def upstreamCause = currentBuild.rawBuild.getCause(Cause$UpstreamCause)
        if (upstreamCause) {
            def upstreamJobName = upstreamCause.upstreamProject
            def upstreamBuild = Jenkins.instance.getItemByFullName(upstreamJobName)?.getLastBuild()
            if (upstreamBuild) {
                upstreamEnv = upstreamBuild.getEnvironment()
            } else {
                error 'Upstream build not found.'
            }
        } else {
            currentBuild.result = 'UNSTABLE'
            error 'Not triggered by an upstream cause. Finishing.'
        }
    } catch (Exception ex) {
        error "Exception while getting upstream vars: \n $ex"
    }

    return upstreamEnv
}

pipeline {
    agent none

    options {
        skipDefaultCheckout(true)
    }

    stages {
        stage('Get upstream vars') {
            steps {
                script {
                    upstreamEnv = getUpstreamVars()
                }
            }
        }
        stage('Check changeset') {
            agent any
            steps {
                echo 'INFORMATION FROM SCM:\n' +
                    "URL: ${params.GIT_URL} \n" +
                    "Commit: ${params.GIT_COMMIT} \n" +
                    "Change ID: ${upstreamEnv.CHANGE_ID} \n" +
                    "Build user ID: ${upstreamEnv.BUILD_USER_ID} \n" +
                    "Change author: ${upstreamEnv.CHANGE_AUTHOR}"
                script {
                    currentBuild.description =
                    "URL: <a href='${params.GIT_URL}'>${params.GIT_URL}</a><br>" +
                    "Commit: <b>${params.GIT_COMMIT}</b><br>" +
                    "Change ID: <b>${upstreamEnv.CHANGE_ID}</b><br>" +
                    "Build user ID: <b>${upstreamEnv.BUILD_USER_ID}</b><br>" +
                    "Change author: <b>${upstreamEnv.CHANGE_AUTHOR}</b>"

                    if (whitelist.contains(upstreamEnv.CHANGE_AUTHOR) || 
                    whitelist.contains(upstreamEnv.BUILD_USER_ID)) {
                            echo 'Author of commit not whiltelisted or build started by scheduler.'
                            comment = 'Jenkins checks need to be started by whitelisted user, and will appear' +
                            ' as failed.\\nPlease wait for repo owner to start checks manually.'
                            addComment(comment, upstreamEnv.CHANGE_ID)
                            error 'User not whitelisted.'
                    } else {
                            echo 'No need to add comment. User whitelisted.'
                    }
                }
            }
        }
        stage('Generate jobs') {
            steps {
                script {
                    pythonsArray.each { py ->
                        parallelStages.put("${runAndTestStage}_python${py}",
                        generateStage(runAndTestStage, params.GIT_URL, params.GIT_COMMIT, upstreamEnv.CHANGE_ID, py))
                    }
                    parallelStages.put("${banditStage}",
                        generateStage(banditStage, params.GIT_URL, params.GIT_COMMIT, upstreamEnv.CHANGE_ID, 'None'))
                }
            }
        }
        stage('Run CI') {
            steps {
                script {
                    echo 'Started CI'
                    // parallel parallelStages
                    }
            }
        }
    }
}
