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
                        string(name: 'TestOptions', value: "${params.TestOptions}")],
            wait: true
        }
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
                    String changedFiles = sh(returnStdout: true, label: "Get changed files", script: """wget -qO- \
                    http://api.github.com/repos/openvinotoolkit/openvino_notebooks/commits/$commit \
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
}
