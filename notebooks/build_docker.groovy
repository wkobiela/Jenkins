/* groovylint-disable CompileStatic, Indentation, NestedBlockDepth, NoJavaUtilDate */
import java.text.SimpleDateFormat

Date date = new Date()
SimpleDateFormat sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss Z', Locale.default)
SimpleDateFormat timeZone = new SimpleDateFormat('Z', Locale.default)
if (params.CheckoutDate == '') {
    formatted_date = "${sdf.format(date)}"
} else {
    formatted_date = "${params.CheckoutDate} ${timeZone.format(date)}"
}

node(params.NodeSelector) {
    currentBuild.displayName = "#$env.BUILD_NUMBER $env.JOB_BASE_NAME python: $params.PythonVersion"

    stage('Clean_start') {
        stage_log('CLEAN_START')
        cleanWs()
        try {
            sh 'docker rmi openvino_notebooks'
        } catch (Exception e) {
            println(e)
        }
    }

    stage('Clone') {
                stage_log('CLONE')
                sh 'git clone https://github.com/openvinotoolkit/openvino_notebooks.git'
                dir("$WORKSPACE/openvino_notebooks") {
                    sh """git checkout `git rev-list -n 1 --before="$formatted_date" main`"""
                }
            }

    stage('Build image') {
        dir("$WORKSPACE/openvino_notebooks") {
            sh label: 'Build docker image', script: 'docker build . -t openvino_notebooks'
        }
    }

    stage('Test image') {
        dir("$WORKSPACE/openvino_notebooks") {
            sh label: 'Test docker image', script: 'docker run --entrypoint /tmp/scripts/test openvino_notebooks'
        }
    }

    stage('Clean_fin') {
        stage_log('CLEAN_START')
        cleanWs()
        try {
            sh 'docker rmi openvino_notebooks'
        } catch (Exception e) {
            println(e)
        }
    }
}

void stage_log(String stage) {
    println("============================================= $stage STAGE =============================================")
}
