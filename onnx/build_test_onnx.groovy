node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER node: $env.NODE_NAME"
    stage('Clean') {
        println("============================================== CLEAN STAGE ==============================================")
        cleanWs()
    }
    println("========================================== STARTING CONTAINER ===========================================")
    docker.image('wkobiela/onnx_build_base:latest').inside(" -v /etc/localtime:/etc/localtime:ro") {
        stage('Info') {
            println("============================================== INFO STAGE ===============================================")
            println("Node selector choosen: $params.NodeSelector")
            sh label: "Update path", script: 'export PATH="/home/jenkins/.local/bin:$PATH"'
            println("Working dir $env.WORKSPACE")
        }
        stage('Install protobuf') {
            println("========================================= INSTALL PROTOBUF STAGE =========================================")
            try {
               sh 'git clone https://github.com/protocolbuffers/protobuf.git'
                dir ("$env.WORKSPACE/protobuf") {
                    sh label: 'Checkout code', script: 'git checkout v3.20.2'
                    sh label: 'Update submodule', script: 'git submodule update --init --recursive'
                }
                dir ("$env.WORKSPACE/protobuf/build_source") {
                    sh label: "Cmake protobuf", script: "cmake ../cmake -Dprotobuf_BUILD_SHARED_LIBS=OFF -DCMAKE_INSTALL_PREFIX=/usr -DCMAKE_INSTALL_SYSCONFDIR=/etc -DCMAKE_POSITION_INDEPENDENT_CODE=ON -Dprotobuf_BUILD_TESTS=OFF -DCMAKE_BUILD_TYPE=Release"
                    sh label: 'Make protobuf', script: 'make -j$(nproc)'
                    sh label: 'Install protobuf', script: 'sudo make install'
                    sh label: 'Export cmake_args', script: 'export CMAKE_ARGS="-DONNX_USE_PROTOBUF_SHARED_LIBS=OFF"'
                }
            } catch (Exception e) {
                println("Exception $e")
                error "Stage failed!"
            }
        }
        stage('Clone') {
            println("============================================== CLONE STAGE ==============================================")
            try {
                sh label: 'Cloning ONNX repository', script: 'git clone https://github.com/onnx/onnx.git'
            } catch (Exception e) {
                println("Exception $e")
                error "Stage failed!"
            }
        }
        stage('Build ONNX') {
            println("============================================== BUILD STAGE ==============================================")
            try {
                dir("$env.WORKSPACE/onnx") {
                    sh label: 'Update submodules', script: 'git submodule update --init --recursive'
                    sh label: 'Build build_test_onnx', script: 'sudo pip install -e .'
                }
            }
            catch (Exception e) {
                println("Exception $e")
                error "Stage failed!"
            }
        }
        stage('Test ONNX') {
            println("============================================== TEST STAGE ==============================================")
            try {
                dir("$env.WORKSPACE/onnx") {
                    sh label: 'Install pytest', script: 'sudo pip install pytest pytest-html nbval'
                    sh label: 'Run tests', script: 'pytest --html=report.html'
                }
            }
            catch (Exception e) {
                println("Exception $e")
                error "Stage failed!"
            }
        }
        stage("Create report") {
            println("============================================ REPORT STAGE ==============================================")
            try {
                publishHTML (target: [
                            allowMissing: false,
                            alwaysLinkToLastBuild: false,
                            keepAll: true,
                            reportDir: "$env.WORKDIR/onnx",
                            reportFiles: 'index.html',
                            reportName: "Pytest Report"
                ])
            }
            catch (Exception e) {
                println("Exception $e")
                error "Stage failed!"
            }
        }
    }
}
