def parallelStages = [:]
jobsArray = ['Cargo_build', 'Cargo_fmt', 'Cargo_clippy_(wasm)', 'Cargo_clippy', 'Cargo_tests', 'Cargo_tests_(wasm)', 
            'Test_scripts']

def generateStage(job) {
    return {
        stage("Stage: ${job}") {
            build job: "${job}",
            wait: true
        }
    }
}

jobsArray.each { job ->
    parallelStages.put("${job}", generateStage(job))
}

pipeline {
    agent none
    stages {
        stage('Run CI') {
            steps {
                script {
                    parallel parallelStages
                }
            }
        }
    }
}
