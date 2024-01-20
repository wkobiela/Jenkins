/* groovylint-disable DuplicateStringLiteral, NestedBlockDepth */
currentBuild.displayName = "release ${params.Commit} #$env.BUILD_NUMBER"

podTemplate(
    containers: [
    containerTemplate(
        name: 'jobscrapper',
        image: 'wkobiela/jobscrapper_base:latest',
        alwaysPullImage: true,
        command: 'sleep',
        args: '99d',
        resourceRequestCpu: '700m',
        resourceLimitCpu: '1',
        resourceRequestMemory: '1Gi',
        resourceLimitMemory: '1Gi',
        )],
        volumes: [
        persistentVolumeClaim(
            mountPath: '/mnt/pip_cache',
            claimName: 'pip-cache-pvc',
            readOnly: false
        )],
        nodeSelector: 'test=true',
        runAsUser: '1001'
        ) {
    node(POD_LABEL) {
        container('jobscrapper') {
            try {
                stage('Clone') {
                    sh "git clone ${params.Repo_url} ."
                    sh "git config --global --add safe.directory ${WORKSPACE}"
                    sh "git reset --hard ${params.Commit}"
                }
                stage('Install dependencies') {
                    sh 'python3 --version'
                    sh 'python3 -m pip install --cache-dir=/mnt/pip_cache --upgrade pip'
                    sh 'python3 -m pip install --cache-dir=/mnt/pip_cache -r requirements.txt'
                    sh 'python3 -m pip install --cache-dir=/mnt/pip_cache twine'
                }
                stage('Build package') {
                    sh 'python3 -m build'
                }
                stage('Upload to Pipy repository') {
                    withCredentials([usernamePassword(credentialsId: 'pypi_token',
                                    usernameVariable: 'TWINE_USERNAME',
                                    passwordVariable: 'TWINE_PASSWORD')]) {
                        sh 'python3 -m twine upload --repository testpypi dist/*'
                    }
                }
            } catch (Exception ex) {
                error("Build failed. $ex")
            }
        }
    }
}
