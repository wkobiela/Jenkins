/* groovylint-disable CompileStatic, Indentation, NestedBlockDepth, NoJavaUtilDate */
def pythons = Eval.me(params.PythonsArray)
def os = Eval.me(params.OsArray)
parallelStagesMap = [:]
commit = params.Commit ?: 'main'
runTests = false

pythons.each { p ->
    os.each { o ->
        parallelStagesMap.put("${o}, python${p}", generateStage(p, o))
    }
}

def generateStage(python, os) {
    return {
        stage("Stage: ${os} Python${python}") {
            build job: "${os}",
            parameters: [string(name: 'Commit', value: "${params.Commit}"),
                        string(name: 'PythonVersion', value: "${python}"),
                        string(name: 'TestOptions', value: "${params.TestOptions}"),
                        booleanParam(name: 'propagateStatus', value: Boolean.valueOf(params.propagateStatus))
                        ],
            wait: true
        }
    }
}

def statusUpdate(status) {
    if (params.propagateStatus) {
        withCredentials([string(credentialsId: 'github_openvino_notebooks_token', variable: 'TOKEN')]) {
            cmd = """curl "https://api.github.com/repos/wkobiela/openvino_notebooks/statuses/${params.Commit}" \
            -H "Content-Type: application/json" \
            -H "Authorization: token """ + TOKEN + """\" \
            -X POST \
            -d "{\\"state\\": \\"${status}\\",\\"context\\": \\"${env.JOB_BASE_NAME}\\", \
            \\"description\\": \\"Jenkins\\", \\"target_url\\": \\"${env.BUILD_URL}\\"}\""""
            sh label: 'Update Github actions status', script: cmd
        }
    } else {
        println('Propagate status is disabled.')
    }
}

pipeline {
    agent none
    stages {
        stage('Get changeset') {
            agent {
                label 'linux'
            }
            steps {
                script {
                    statusUpdate('pending')
                    String changedFiles = sh(returnStdout: true, label: 'Get changed files', script: """wget -qO- \
                    http://api.github.com/repos/wkobiela/openvino_notebooks/commits/$commit \
                    | jq -r '.files | .[] | .filename'""")
                    if (changedFiles.contains('ipynb')) {
                        println("Files changed: ${changedFiles}")
                        runTests = true
                    }
                    else if (changedFiles.contains('requirements.txt')) {
                        println("Files changed: ${changedFiles}")
                        runTests = true
                    } else {
                        println("Files changed: ${changedFiles}No need to run any tests.")
                        currentBuild.result = 'SUCCESS'
                        return
                    }
                }
            }
        }
        stage('Schedule tests') {
            agent none
                when {
                    expression {
                        return runTests
                    }
                }
            steps {
                script {
                    parallel parallelStagesMap
                }
            }
        }
    }
    post {
        success {
            node('linux') {
                statusUpdate('success')
            }
        }
        failure {
            node('linux') {
                statusUpdate('failure')
            }
        }
    }
}