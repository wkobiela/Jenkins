def pythons = ['3.7', '3.8', '3.9', '3.10']
def os = ['Ubuntu22']
def parallelStagesMap = [:]

pythons.each { p ->
    os.each { o ->
        parallelStagesMap.put("${o}, python${p}", generateStage(p, o))
    }
}

def generateStage(python, os) {
    return {
        stage("Stage: ${os} Python${python}") {
            build job: "${os}",
            parameters: [string(name: 'NodeSelector', value: 'amd64'),
                        string(name: 'PythonVersion', value: "${python}")],
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

