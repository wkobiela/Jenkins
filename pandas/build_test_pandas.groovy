node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER node: $env.NODE_NAME"

    threads = sh(script: 'nproc', returnStdout: true).trim().toInteger()
    if (threads > 4) {
        println("More than 4 threads: ${threads}")
        threads = threads / 2
        println("Set threads: ${threads}")
    } else if (threads == 4) {
        println("Exactly ${threads} threads.")
        println("Set threads: ${threads}")
    } else {
        println("Another variant & set threads: ${threads}")
    }

    stage('Clean') {
        println('========================================= CLEAN STAGE ===============================================')
        try {
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
            sh label: 'Clean workspace', script: "sudo rm -rf $env.WORKSPACE/*"
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
    println('========================================== STARTING CONTAINER ===========================================')
    docker.image('wkobiela/pandas_build_base:latest').inside(' -v /etc/localtime:/etc/localtime:ro') {
        stage('Clone') {
            println('========================================== CLONE STAGE ==========================================')
            try {
                sh 'git clone https://github.com/pandas-dev/pandas.git'
            } catch (Exception e) {
                error "Stage failed with exception $e"
            }
        }
        stage('Prepare') {
            println('======================================== PREPARE STAGE ==========================================')
            try {
                dir("$env.WORKSPACE/pandas") {
                    sh label: 'Install additional requirements', script: 'python3 -m pip install \
                    pytest \
                    hypothesis \
                    cython numexpr \
                    bottleneck tables \
                    tabulate \
                    pytest-html'

                    sh label: 'Install requirements', script: 'python3 -m pip install -r requirements-dev.txt'
                }
            } catch (Exception e) {
                error "Stage failed with exception $e"
            }
        }
        stage('Build') {
            println('======================================== BUILD STAGE ============================================')
            try {
                dir("$env.WORKSPACE/pandas") {
                    sh label: 'Build pandas', script: "python3 setup.py build_ext -j ${threads}"
                    sh label: 'Install pandas', script: 'python3 -m pip install -e . \
                    --no-build-isolation --no-use-pep517'
                }
            } catch (Exception e) {
                error "Stage failed with exception $e"
            }
        }
        stage('Test') {
            println('========================================== TEST STAGE ===========================================')
            try {
                dir("$env.WORKSPACE/pandas") {
                    sh label: 'Run base tests', script: "pytest --skip-slow --skip-network --skip-db \
                                                        $env.WORKSPACE/pandas/pandas/tests/base \
                                                        --html=report_base.html --junitxml=report_base.xml"
                }
            }
            catch (Exception e) {
                unstable("Test stage exited with exception $e")
            }
        }
        stage('Create report') {
            println('======================================== REPORT STAGE ===========================================')
            try {
                publishHTML(target: [
                        allowMissing: true,
                        alwaysLinkToLastBuild: false,
                        keepAll: false,
                        reportDir: 'pandas',
                        reportFiles: '*.html',
                        reportName: 'Pytest Report'
                ])
                dir("$env.WORKSPACE/pandas") {
                    junit skipPublishingChecks: true, testResults: '*.xml'
                }
            }
            catch (Exception e) {
                error "Stage failed with exception $e"
            }
        }
    }
    stage('Clean') {
        println('========================================== CLEAN STAGE ==============================================')
        try {
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
            sh label: 'Clean workspace', script: "sudo rm -rf $env.WORKSPACE/*"
            sh label: 'Check workspace size', script: "du -sh $env.WORKSPACE"
        } catch (Exception e) {
            error "Stage failed with exception $e"
        }
    }
}
