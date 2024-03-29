node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER node: $env.NODE_NAME"
    stage('Clean') {
        println('============================================== CLEAN STAGE ==========================================')
        try {
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
            sh label: 'Clean workspace', script: "sudo rm -rf $env.WORKSPACE/*"
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Prepare') {
        println('========================================== PREPARE STAGE ============================================')
        try {
            sh label: 'Quemu image', script: 'docker run --rm --privileged multiarch/qemu-user-static --reset -p yes'
        } catch (Exception e) {
            println "Something went wrong, but trying anyways. MSG: $e"
        }
        try {
            sh label: 'Remove old builder if it exists', script: 'docker buildx rm builder'
        } catch (Exception e) {
            println "Something went wrong, but trying anyways. MSG: $e"
        }
        try {
            sh label: 'New builder', script: 'docker buildx create --name builder --driver docker-container --use'
        } catch (Exception e) {
            println "Something went wrong, but trying anyways. MSG: $e"
        }
        try {
            sh label: 'Run bootstrap to check available architectures', script: 'docker buildx inspect --bootstrap'
        } catch (Exception e) {
            println "Something went wrong, but trying anyways. MSG: $e"
        }
    }
    stage('Login') {
        println('============================================ LOGIN STAGE ============================================')
        try {
            withCredentials([usernamePassword
            (credentialsId: "$params.CredentialsUser", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                sh "echo $PASSWORD | docker login -u $USERNAME --password-stdin"
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Clone') {
        println('============================================ CLONE STAGE ============================================')
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
        println('============================================ BUILD STAGE ============================================')
        try {
            dir("$env.WORKSPACE/Dockerfiles/$params.DockerfileDir") {
                sh "docker buildx build --platform $params.Architectures ."
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Push image') {
        println('============================================ PUSH STAGE =============================================')
        try {
            dir("$env.WORKSPACE/Dockerfiles/$params.DockerfileDir") {
                sh "docker buildx build --platform linux/amd64,linux/arm/v7 . --push -t $params.ImageName:latest"
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Remove image') {
        println('========================================== REMOVE STAGE =============================================')
        try {
            sh 'echo y | docker buildx prune'
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Clean') {
        println('============================================ CLEAN STAGE ============================================')
        try {
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
            sh label: 'Clean workspace', script: "sudo rm -rf $env.WORKSPACE/*"
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
}
