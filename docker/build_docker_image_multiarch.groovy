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
            withCredentials([usernamePassword(credentialsId: "$params.CredentialsUser", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                sh "echo $PASSWORD | docker login -u $USERNAME --password-stdin"
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
    stage('Build') {
        println("============================================== BUILD STAGE ==============================================")
        try {
            dir("$env.WORKSPACE/Dockerfiles/$params.DockerfileDir") {
                sh "docker buildx build --platform linux/amd64,linux/arm/v7 ."
            }
        } catch (Exception e) {
            println("Exception $e")
            error "Stage failed!"
        }
    }
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
//    stage('Remove image') {
//        println("============================================ REMOVE STAGE ===============================================")
//        try {
//            sh "docker buildx prune"
//        } catch (Exception e) {
//            println("Exception $e")
//            error "Stage failed!"
//        }
//    }
}