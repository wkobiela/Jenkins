node(params.NodeSelector) {
    def image
    stage('Clean') {
        println("============================================== CLEAN STAGE ==============================================")
        cleanWs()
    }
    stage('Clone') {
        println("============================================== CLONE STAGE ==============================================")
        try {
            sh 'git clone https://github.com/wkobiela/Dockerfiles.git'
        } catch (Exception e) {
            println("Exception $e")
            error "Stage failed!"
        }
    }
    stage('Build') {
        println("============================================== BUILD STAGE ==============================================")
        try {
            dir("$env.WORKSPACE/Dockerfiles/$params.DockerfileDir") {
                image = docker.build(params.ImageName)
            }
        } catch (Exception e) {
            println("Exception $e")
            error "Stage failed!"
        }
    }
    stage('Test') {
        println("============================================== TEST STAGE ===============================================")
        try {
            image.inside {
                sh 'echo "Tests passed"'
            }
        } catch (Exception e) {
            println("Exception $e")
            error "Stage failed!"
        }
    }
    stage('Push image') {
        println("============================================== PUSH STAGE ===============================================")
        try {
            docker.withRegistry('', 'dockerhub') {
                image.push("latest")
            }
        } catch (Exception e) {
            println("Exception $e")
            error "Stage failed!"
        }
    }
    stage('Remove image') {
        println("============================================ REMOVE STAGE ===============================================")
        try {
            sh "docker rmi $params.ImageName"
        } catch (Exception e) {
            println("Exception $e")
            error "Stage failed!"
        }
    }
}