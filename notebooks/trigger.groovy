def pythons = Eval.me(params.PythonsArray)
def os = Eval.me(params.OsArray)
parallelStagesMap = [:]

pythons.each { p ->
    os.each { o ->
        parallelStagesMap.put("${o}, python${p}", generateStage(p, o))
    }
}

def generateStage(python, os) {
    return {
        stage("Stage: ${os} Python${python}") {
            build job: "${os}",
            parameters: [string(name: 'PythonVersion', value: "${python}"),
                        string(name: 'TestOptions', value: "${params.TestOptions}")],
            wait: true
        }
    }
}

pipeline {
    agent none
    stages {
        stage('Notebook tests') {
            steps {
                script {
                    parallel parallelStagesMap
                }
            }
        }
    }
}
