/* groovylint-disable Indentation, NestedBlockDepth */
node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER node: $env.NODE_NAME python: $params.PythonVersion"

    stage('Clean') {
        stage_log('CLEAN')
        clean_workspace()
    }

    try {
        stage_log('STARTING CONTAINER')
        docker.image('notebooks_ubuntu2204:test').inside(' -v /etc/localtime:/etc/localtime:ro') {
            stage('Clone') {
                stage_log('CLONE')
                sh 'git clone https://github.com/openvinotoolkit/openvino_notebooks.git'
            }
            stage('Copy CT files') {
                stage_log('COPY CT FILES')
                dir("$env.WORKSPACE/openvino_notebooks") {
                    sh 'curl -O https://storage.openvinotoolkit.org/data/test_data\
                    /openvino_notebooks/kits19/case_00030.zip'
                    sh 'mkdir notebooks/110-ct-segmentation-quantize/kits19'
                    sh 'mkdir notebooks/110-ct-segmentation-quantize/kits19/kits19_frames'
                    sh 'unzip case_00030.zip'
                    sh 'cp -r case_00030 case_00001'
                    sh 'mv case_00030 notebooks/110-ct-segmentation-quantize/kits19/kits19_frames'
                    sh 'mv case_00001 notebooks/110-ct-segmentation-quantize/kits19/kits19_frames'
                }
            }
            stage('Download long loading models') {
                stage_log('DOWNLOAD LONG LOADING MODELS')
                dir("$env.WORKSPACE/openvino_notebooks") {
                    sh 'mkdir notebooks/203-meter-reader/model'
                    sh "curl -o notebooks/203-meter-reader/model/meter_det_model.tar.gz \
                'https://bj.bcebos.com/paddlex/examples2/meter_reader/meter_det_model.tar.gz'"
                    sh "curl -o notebooks/203-meter-reader/model/meter_seg_model.tar.gz \
                'https://bj.bcebos.com/paddlex/examples2/meter_reader/meter_seg_model.tar.gz'"
                    sh 'mkdir notebooks/405-paddle-ocr-webcam/model'
                    sh "curl -o notebooks/405-paddle-ocr-webcam/model/ch_PP-OCRv3_det_infer.tar \
                'https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_det_infer.tar'"
                    sh "curl -o notebooks/405-paddle-ocr-webcam/model/ch_PP-OCRv3_rec_infer.tar \
                'https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_rec_infer.tar'"
                }
            }
            stage('Create venv') {
                stage_log('CREATE VENV')
                dir("$env.WORKSPACE/openvino_notebooks") {
                    sh "python${params.PythonVersion} -m venv openvino_env"
                }
            }
            stage('Install dependencies') {
                stage_log('INSTALL DEPENDENCIES')
                dir("$env.WORKSPACE/openvino_notebooks") {
                    sh label: 'Upgrade pip', script: '. openvino_env/bin/activate && \
                    python -m pip install --upgrade pip'
                    sh label: 'Install requirements', script: '. openvino_env/bin/activate && \
                    python -m pip install -r .ci/dev-requirements.txt --cache-dir pipcache'
                    sh label: 'Install ipykernel', script: '. openvino_env/bin/activate && \
                    python -m ipykernel install --user --name openvino_env'
                }
            }
            stage('Patch notebooks') {
                stage_log('PATCH NOTEBOOKS')
                dir("$env.WORKSPACE/openvino_notebooks") {
                    sh '. openvino_env/bin/activate && python .ci/patch_notebooks.py notebooks/'
                }
            }
            stage('Check install') {
                stage_log('CHECK INSTALL')
                dir("$env.WORKSPACE/openvino_notebooks") {
                    sh '. openvino_env/bin/activate && python check_install.py'
                    sh '. openvino_env/bin/activate && jupyter lab notebooks --help'
                }
            }
            stage('Test') {
                stage_log('TEST')
                try {
                    dir("$env.WORKSPACE/openvino_notebooks") {
                        sh ". openvino_env/bin/activate && python .ci/validate_notebooks.py \
                        --test_list 001-hello-world \
                        --report_dir test_report/ubuntu-22.04-${params.PythonVersion}"
                    }
            } catch (Exception ex) {
                    unstable("Test stage exited with exception $ex")
            } finally {
                stage('Create report') {
                    stage_log('REPORT')
                    publishHTML(target: [
                        allowMissing: true,
                        alwaysLinkToLastBuild: false,
                        keepAll: false,
                        reportDir: 'openvino_notebooks/test_report',
                        reportFiles: "${dir('openvino_notebooks/test_report') 
                        { findFiles(glob: '**/*.html').join(',') ?: 'Not found' }}",
                        reportName: 'Pytest Report'
                        ])
                    }
                }
            }
        }
    } catch (Exception e) {
        error "Exception message: $e"
    } finally {
        stage('Clean') {
            stage_log('CLEAN')
            clean_workspace()
        }
        }
    }

def clean_workspace() {
    try {
        sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
        sh label: 'Clean workspace', script: "sudo rm -rf $env.WORKSPACE/*"
        sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
    } catch (Exception e) {
        error "Stage failed with exception $e"
    }
}

def stage_log(stage) {
    println("============================================ ${stage} STAGE ============================================")
}
