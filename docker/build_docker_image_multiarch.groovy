node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER node: $env.NODE_NAME"
    def image
    stage('Clean') {
        println("============================================== CLEAN STAGE ==============================================")
        cleanWs()
    }
    stage('Login') {
        println("============================================== TEST STAGE ===============================================")
        try {
            withCredentials([usernameColonPassword(credentialsId: "$params.CredentialsUser", variable: 'DOCKERPASS')]) {
                sh "docker login -u $params.CredentialsUser -p $DOCKERPASS"
                println("Password to $params.CredentialsUser $DOCKERPASS")
            }
        } catch (Exception e) {
            println("Exception $e")
            error "Stage failed!"
        }
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
//    stage('Build') {
//        println("============================================== BUILD STAGE ==============================================")
//        try {
//            dir("$env.WORKSPACE/Dockerfiles/$params.DockerfileDir") {
//                image = docker.build(params.ImageName)
//            }
//        } catch (Exception e) {
//            println("Exception $e")
//            error "Stage failed!"
//        }
//    }
//    stage('Push image') {
//        println("============================================== PUSH STAGE ===============================================")
//        try {
//            docker.withRegistry('', 'dockerhub') {
//                image.push("latest")
//            }
//        } catch (Exception e) {
//            println("Exception $e")
//            error "Stage failed!"
//        }
//    }
    stage('Remove image') {
        println("============================================ REMOVE STAGE ===============================================")
        try {
            sh "docker buildx prune"
        } catch (Exception e) {
            println("Exception $e")
            error "Stage failed!"
        }
    }
}