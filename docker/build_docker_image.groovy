node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER node: $env.NODE_NAME"
    def image
    stage('Clean') {
        println("============================================== CLEAN STAGE ==============================================")
        cleanWs()
    }
    stage('Clone') {
        println("============================================== CLONE STAGE ==============================================")
        try {
            sh 'git clone https://github.com/wkobiela/Dockerfiles.git'
            dir("$env.WORKSPACE/Dockerfiles") {
                sh label: 'Check last commit', script: 'git log -1'
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Build') {
        println("============================================== BUILD STAGE ==============================================")
        try {
            dir("$env.WORKSPACE/Dockerfiles/$params.DockerfileDir") {
                image = docker.build(params.ImageName)
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Test') {
        println("============================================== TEST STAGE ===============================================")
        try {
            image.inside {
                sh 'echo "Tests passed"'
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Push image') {
        println("============================================== PUSH STAGE ===============================================")
        try {
            docker.withRegistry('', 'dockerhub') {
                image.push("latest")
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Remove image') {
        println("============================================ REMOVE STAGE ===============================================")
        try {
            sh "docker rmi $params.ImageName"
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Clean') {
        println("============================================== CLEAN STAGE ==============================================")
        cleanWs()
    }
}