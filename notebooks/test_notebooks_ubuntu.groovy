/* groovylint-disable CompileStatic, Indentation, NestedBlockDepth, NoJavaUtilDate */
commit = params.Commit ?: 'main'

node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER $env.JOB_BASE_NAME python: $params.PythonVersion"
    statusName = "$env.JOB_BASE_NAME python: $params.PythonVersion"

    stage('Clean_start') {
        statusUpdate('pending')
        stage_log('CLEAN_START')
        clean_workspace()
    }

    try {
        stage_log('STARTING CONTAINER')
        docker.image("$params.DockerImage").inside("-v /etc/localtime:/etc/localtime:ro \
                                                    -v /home/jenkins/workspace/cache:$WORKSPACE/cache") {
            stage('Clone') {
                stage_log('CLONE')
                sh 'git clone https://github.com/wkobiela/openvino_notebooks.git'
                dir("$WORKSPACE/openvino_notebooks") {
                    sh "git checkout $commit"
                }
            }
            stage('Get changed files') {
                stage_log('GET CHANGED FILES')
                dir("$WORKSPACE/openvino_notebooks") {
                    sh 'git diff --name-only HEAD~1 HEAD > test_notebooks.txt'
                    sh label: 'Show changed files', script: 'cat test_notebooks.txt'
                }
            }
            stage('Check cache') {
                stage_log('CHECK CACHE')
                dir("$WORKSPACE/cache") {
                    updateCache()
                }
            }
            stage('Setup files') {
                stage_log('SETUP FILES')
                dir("$WORKSPACE/openvino_notebooks/notebooks") {
                    reuseCachedFiles()
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
                    sh label: 'Install requirements', script: ". openvino_env/bin/activate && \
                    python -m pip install -r .ci/dev-requirements.txt --cache-dir $WORKSPACE/cache"
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
                        ${params.TestOptions} --report_dir test_report/${JOB_BASE_NAME}_${params.PythonVersion}"
                    }
            } catch (Exception ex) {
                    unstable("Test stage exited with exception $ex")
            } finally {
                stage('Create report') {
                    stage_log('REPORT')
                    sh "ls -ll openvino_notebooks/test_report/${JOB_BASE_NAME}_${params.PythonVersion}"
                    publishHTML(target: [
                        allowMissing: true,
                        alwaysLinkToLastBuild: false,
                        keepAll: false,
                        reportDir: "openvino_notebooks/test_report/${JOB_BASE_NAME}_${params.PythonVersion}",
                        reportFiles: '**/*.html',
                        reportName: 'Pytest Report'
                        ])
                    }
                stage('Archive artifacts') {
                    stage_log('ARCHIVE')
                    archiveArtifacts(allowEmptyArchive: true, artifacts: 'openvino_notebooks/test_report/**/*.csv')
                    }
                }
            }
        }
    } catch (Exception e) {
        error "Exception message: $e"
    } finally {
        stage('Clean_fin') {
            stage_log('CLEAN_FIN')
            clean_workspace()
        }
        if (currentBuild.result == 'FAILURE' || currentBuild.result == 'UNSTABLE' || currentBuild.result == 'ABORTED') {
            statusUpdate('failure')
        } else {
            statusUpdate('success')
        }
    }
}

void clean_workspace() {
    try {
        sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
        sh label: 'Clean workspace', script: "sudo rm -rf $env.WORKSPACE/*"
        sh label: 'Check workspace size - postclean', script: "du -sh $env.WORKSPACE"
    } catch (Exception e) {
        error "Stage failed with exception $e"
    }
}

void stage_log(String stage) {
    println("============================================= $stage STAGE =============================================")
}

void updateCache() {
    files_map = [
        'case_00030.zip':'https://storage.openvinotoolkit.org/data/test_data/openvino_notebooks/kits19/case_00030.zip',
        'meter_det_model.tar.gz':'https://bj.bcebos.com/paddlex/examples2/meter_reader/meter_det_model.tar.gz',
        'meter_seg_model.tar.gz':'https://bj.bcebos.com/paddlex/examples2/meter_reader/meter_seg_model.tar.gz',
        'ch_PP-OCRv3_det_infer.tar':'https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_det_infer.tar',
        'ch_PP-OCRv3_rec_infer.tar':'https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_rec_infer.tar'
    ]

    files_map.each { f ->
        int status = sh(script: "ls | grep $f.key", returnStatus: true)
        if (status == 0) {
            println("File $f.key exist.")
        } else {
            println("File $f.key do not exist. Download")
            sh "curl -o $f.key '$f.value'"
        }
    }
}

void reuseCachedFiles() {
    sh 'mkdir -p 110-ct-segmentation-quantize/kits19/kits19_frames 203-meter-reader/model 405-paddle-ocr-webcam/model'
    sh "cp $WORKSPACE/cache/case_00030.zip 110-ct-segmentation-quantize/kits19/kits19_frames/case_00030.zip"
    sh 'unzip 110-ct-segmentation-quantize/kits19/kits19_frames/case_00030.zip \
        -d 110-ct-segmentation-quantize/kits19/kits19_frames'
    sh 'cp -r 110-ct-segmentation-quantize/kits19/kits19_frames/case_00030 \
        110-ct-segmentation-quantize/kits19/kits19_frames/case_00001'
    sh "ln -s $WORKSPACE/cache/meter_det_model.tar.gz 203-meter-reader/model/meter_det_model.tar.gz"
    sh "ln -s $WORKSPACE/cache/meter_seg_model.tar.gz 203-meter-reader/model/meter_seg_model.tar.gz"
    sh "ln -s $WORKSPACE/cache/ch_PP-OCRv3_det_infer.tar 405-paddle-ocr-webcam/model/ch_PP-OCRv3_det_infer.tar"
    sh "ln -s $WORKSPACE/cache/ch_PP-OCRv3_rec_infer.tar 405-paddle-ocr-webcam/model/ch_PP-OCRv3_rec_infer.tar"

    sh label: 'Check files existance', script: 'ls -l 110-ct-segmentation-quantize/kits19/kits19_frames \
                                                && ls -l 203-meter-reader/model/ \
                                                && ls -l 405-paddle-ocr-webcam/model'
}

def statusUpdate(status) {
    if (params.propagateStatus) {
        withCredentials([string(credentialsId: 'github_openvino_notebooks_token', variable: 'TOKEN')]) {
            cmd = """curl "https://api.github.com/repos/wkobiela/openvino_notebooks/statuses/${params.Commit}" \
            -H "Content-Type: application/json" \
            -H "Authorization: token ${TOKEN}" \
            -X POST \
            -d "{\\"state\\": \\"${status}\\",\\"context\\": \\"${statusName}\\", \
            \\"description\\": \\"Jenkins\\", \\"target_url\\": \\"${env.BUILD_URL}\\"}\""""
            println(cmd)
            sh label: 'Update Github actions status', script: cmd
        }
    } else {
        println('Propagate status is disabled.')
    }
}