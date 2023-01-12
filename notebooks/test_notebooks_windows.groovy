/* groovylint-disable CompileStatic, Indentation, NestedBlockDepth, NoJavaUtilDate */
import java.text.SimpleDateFormat

Date date = new Date()
SimpleDateFormat sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss', Locale.default)
if (params.CheckoutDate == '') {
    formatted_date = sdf.format(date)
} else {
    formatted_date = params.CheckoutDate
}

node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER $env.JOB_BASE_NAME python: $params.PythonVersion"

    String pythonVersion = params.PythonVersion.replaceAll('\\.', '')

    stage('Clean_start') {
        stage_log('CLEAN_START')
        cleanWs()
    }

    try {
        stage('Clone') {
            stage_log('CLONE')
            bat label: 'Clone repository', script: 'git clone https://github.com/openvinotoolkit/openvino_notebooks.git'
            dir("$WORKSPACE/openvino_notebooks") {
                    bat """git checkout `git rev-list -n 1 --before="$formatted_date" main`"""
                }
        }
        stage('Get changed files') {
            stage_log('GET CHANGED FILES')
            dir("$WORKSPACE/openvino_notebooks") {
                bat label: 'Diff between last commits', script: 'git diff --name-only HEAD~1 HEAD > test_notebooks.txt'
                bat label: 'Show changed files', script: 'type test_notebooks.txt'
            }
        }
        stage('Check cache') {
            stage_log('CHECK CACHE')
            dir("$WORKSPACE/../cache") {
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
                bat label: 'Create virtual env', script: """"C:\\Program Files\\Python${pythonVersion}\\python.exe" \
                -m venv openvino_env"""
            }
        }
        stage('Install dependencies') {
            stage_log('INSTALL DEPENDENCIES')
            dir("$env.WORKSPACE/openvino_notebooks") {
                bat label: 'Upgrade pip', script: 'openvino_env\\Scripts\\activate.bat && \
                python -m pip install --upgrade pip'
                bat label: 'Install requirements', script: "openvino_env\\Scripts\\activate.bat && \
                python -m pip install -r .ci\\dev-requirements.txt --cache-dir $WORKSPACE\\..\\cache"
                bat label: 'Install ipykernel', script: 'openvino_env\\Scripts\\activate.bat && \
                python -m ipykernel install --user --name openvino_env'
            }
        }
        stage('Patch notebooks') {
            stage_log('PATCH NOTEBOOKS')
            dir("$env.WORKSPACE/openvino_notebooks") {
                bat label: 'Patch openvino_notebooks',
                    script: 'openvino_env\\Scripts\\activate.bat && python .ci/patch_notebooks.py notebooks/'
            }
        }
        stage('Check install') {
            stage_log('CHECK INSTALL')
            dir("$env.WORKSPACE/openvino_notebooks") {
                bat label: 'Check installation',
                    script: 'openvino_env\\Scripts\\activate.bat && python check_install.py'
                bat label: 'Verify jupyter',
                    script: 'openvino_env\\Scripts\\activate.bat && jupyter lab notebooks --help'
            }
        }
        stage('Test') {
            stage_log('TEST')
            try {
                dir("$env.WORKSPACE/openvino_notebooks") {
                    bat "openvino_env\\Scripts\\activate.bat && python .ci/validate_notebooks.py \
                    ${params.TestOptions} --report_dir test_report/${JOB_BASE_NAME}_${params.PythonVersion}"
                }
            } catch (Exception ex) {
                unstable("Test stage exited with exception $ex")
            }
        }
        stage('Create report') {
            stage_log('REPORT')
            try {
                publishHTML(target: [
                    allowMissing: true,
                    alwaysLinkToLastBuild: false,
                    keepAll: false,
                    reportDir: "openvino_notebooks/test_report/${JOB_BASE_NAME}_${params.PythonVersion}",
                    reportFiles: '**/*.html',
                    reportName: 'Pytest Report'
                    ])
            } catch (Exception ex) {
                unstable("Failed to create report: $ex")
            }
        }
        stage('Archive artifacts') {
            stage_log('ARCHIVE')
            try {
                archiveArtifacts(allowEmptyArchive: true, artifacts: 'openvino_notebooks/test_report/**/*.csv')
            } catch (Exception ex) {
                unstable("Failed archiving artifacts: $ex")
            }
        }
    } catch (Exception e) {
        error "Exception message: $e"
    } finally {
        stage('Clean_fin') {
            stage_log('CLEAN_FIN')
            cleanWs()
        }
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
        int status = bat(script: "dir | findstr $f.key", returnStatus: true)
        if (status == 0) {
            println("File $f.key exist.")
        } else {
            println("File $f.key do not exist. Download")
            bat "curl -o $f.key $f.value"
        }
    }
}

void reuseCachedFiles() {
    bat 'MD 110-ct-segmentation-quantize\\kits19\\kits19_frames'
    bat 'MD 203-meter-reader\\model'
    bat 'MD 405-paddle-ocr-webcam\\model'
    bat "copy $WORKSPACE\\..\\cache\\case_00030.zip 110-ct-segmentation-quantize\\kits19\\kits19_frames\\case_00030.zip"
    bat 'tar -xf 110-ct-segmentation-quantize\\kits19\\kits19_frames\\case_00030.zip \
        -C 110-ct-segmentation-quantize\\kits19\\kits19_frames'
    bat 'xcopy 110-ct-segmentation-quantize\\kits19\\kits19_frames\\case_00030 \
        110-ct-segmentation-quantize\\kits19\\kits19_frames\\case_00001\\'
    bat "copy $WORKSPACE\\..\\cache\\meter_det_model.tar.gz 203-meter-reader\\model\\meter_det_model.tar.gz"
    bat "copy $WORKSPACE\\..\\cache\\meter_seg_model.tar.gz 203-meter-reader\\model\\meter_seg_model.tar.gz"
    bat "copy $WORKSPACE\\..\\cache\\ch_PP-OCRv3_det_infer.tar 405-paddle-ocr-webcam\\model\\ch_PP-OCRv3_det_infer.tar"
    bat "copy $WORKSPACE\\..\\cache\\ch_PP-OCRv3_rec_infer.tar 405-paddle-ocr-webcam\\model\\ch_PP-OCRv3_rec_infer.tar"
// bat label: 'Check files existance', script: 'ls -l 110-ct-segmentation-quantize/kits19/kits19_frames \
//                                             && ls -l 203-meter-reader/model/ \
//                                             && ls -l 405-paddle-ocr-webcam/model'
}
