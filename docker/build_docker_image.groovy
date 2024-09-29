node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER node: $env.NODE_NAME"

    if (!params.ImageName) {
        error "Error: ImageName parameter is not set."
    }
    String imageTag = params.ImageTag.replaceAll(/\s+/, '').trim() ?: 'latest'

    stage('Clean') {
        println('============================================ CLEAN STAGE ============================================')
        cleanWs()
    }
    stage('Prune build cache') {
        if (params.PruneBuildCache) {
            sh 'docker builder prune --all --force'
        }
        else {
            echo 'Stage was not executed. Build will use available docker build cache.'
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
                image = docker.build(params.ImageName)
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Test') {
        println('============================================ TEST STAGE =============================================')
        try {
            image.inside {
                sh 'echo "Tests passed"'
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Push image') {
        println('============================================ PUSH STAGE =============================================')
        def tagsList = imageTag.tokenize(',')
        echo "Tags list: ${tagsList}"
        // pushing to DockerHub
        try {
            docker.withRegistry('', 'dockerhub') {
                tagsList.each { tag ->
                    echo "Pushing image with tag: ${tag}"
                    image.push(tag)
                }
            }
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Remove image') {
        println('========================================== REMOVE STAGE =============================================')
        try {
            sh "docker rmi \$(docker images -q $params.ImageName)"
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    stage('Clean') {
        println('============================================ CLEAN STAGE ============================================')
        cleanWs()
    }
}
