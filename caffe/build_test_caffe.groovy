node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER node: $env.NODE_NAME"
    stage('Clean') {
        println("============================================== CLEAN STAGE ==============================================")
        try {
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
            sh label: 'Clean workspace', script: "sudo rm -rf $env.WORKSPACE/*"
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    println("========================================== STARTING CONTAINER ===========================================")
    docker.image('wkobiela/caffe_build_base:latest').inside(" -v /etc/localtime:/etc/localtime:ro") {
        stage('Clone') {
            println("========================================== CLONE STAGE ==========================================")
            try {
                sh 'git clone https://github.com/BVLC/caffe.git'
            } catch (Exception e) {
                error "Stage failed with exception $e"
            }
        }
        stage('Prepare') {
            println("============================================= PREPARE STAGE ==============================================")
            try {
                dir ("$env.WORKSPACE/caffe/build") {
                    sh label: 'Execute cmake command', script: 'cmake .. -DBUILD_python=OFF -DBUILD_docs=OFF -DUSE_OPENCV=OFF'
                }
            } catch (Exception e) {
                error "Stage failed with exception $e"
            }
        }
        stage('Build') {
            println("============================================= BUILD STAGE ==============================================")
            try {
                dir ("$env.WORKSPACE/caffe/build") {
                    sh label: 'Execute make all command', script: 'make all'
                    sh label: 'Execute make install command', script: 'make install'
                }
            } catch (Exception e) {
                error "Stage failed with exception $e"
            }
        }
        stage('Test') {
            println("============================================== TEST STAGE ==============================================")
            try {
                dir("$env.WORKSPACE/caffe/build") {
                    sh label: 'Run tests', script: 'make runtest'
                }
            }
            catch (Exception e) {
                unstable("Test stage exited with exception $e")
            }
        }
    }
    stage('Clean') {
        println("============================================== CLEAN STAGE ==============================================")
        try {
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
            sh label: 'Clean workspace', script: "sudo rm -rf $env.WORKSPACE/*"
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
}

