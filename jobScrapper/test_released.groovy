void createIssue(String comment, String number) {
    try {
        withCredentials([string(credentialsId: 'github_token', variable: 'TOKEN')]) {
            cmd = """curl "https://api.github.com/repos/wkobiela/jobScrapper/issues/${number}/comments" \
            -H "Content-Type: application/json" \
            -H "Authorization: token """ + TOKEN + """\" \
            -X POST \
            -d "{\\"body\\": \\"${comment}\\"}\""""
            sh label: 'Add comment to Github PR', script: cmd
        }
    } catch (Exception ex) {
        echo "Cannot add comment. \n $ex"
    }
}

podTemplate(
    containers: [
    containerTemplate(
        name: 'jobscrapper-released',
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
        container('jobscrapper-released') {
            try {
                stage('Install released product from pypi') {
                    sh 'python3 --version'
                    sh 'python3 -m pip install --cache-dir=/mnt/pip_cache --upgrade pip'
                    cmd = 'python3 -m pip install --cache-dir=/mnt/pip_cache' +
                            ' --index-url https://test.pypi.org/simple/ --extra-index-url https://pypi.org/simple/' +
                            ' jobscrapper'
                    sh script: cmd
                }
            } catch (Exception ex) {
                error("Build failed. $ex")
            }
        }
    }
}
