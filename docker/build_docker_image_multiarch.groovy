node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER node: $env.NODE_NAME"
    def image
    stage('Clean') {
        println("============================================== CLEAN STAGE ==============================================")
        cleanWs()
    }
    stage('Login') {
        println("============================================-= LOGIN STAGE ==============================================")
        try {
            withCredentials([usernamePassword(credentialsId: "$params.CredentialsUser", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                sh "echo $PASSWORD | docker login -u $USERNAME --password-stdin"
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
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
                sh "docker buildx build --platform linux/amd64,linux/arm/v7 ."
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Push image') {
        println("============================================== PUSH STAGE ===============================================")
        try {
            dir("$env.WORKSPACE/Dockerfiles/$params.DockerfileDir") {
                sh "docker buildx build --platform linux/amd64,linux/arm/v7 . --push -t $params.ImageName:latest"
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Remove image') {
        println("============================================ REMOVE STAGE ===============================================")
        try {
            sh "echo y | docker buildx prune"
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Clean') {
        println("============================================== CLEAN STAGE ==============================================")
        cleanWs()
    }
}