/* groovylint-disable DuplicateStringLiteral, NestedBlockDepth */
checkName = "${env.JOB_BASE_NAME}"

void statusUpdate(String status) {
    if (params.propagateStatus) {
        withCredentials([string(credentialsId: 'github_token', variable: 'TOKEN')]) {
            cmd = """curl "https://api.github.com/repos/wkobiela/jobScrapper/statuses/${params.Commit}" \
            -H "Content-Type: application/json" \
            -H "Authorization: token """ + TOKEN + """\" \
            -X POST \
            -d "{\\"state\\": \\"${status}\\",\\"context\\": \\"${checkName}\\", \
            \\"target_url\\": \\"${env.BUILD_URL}\\"}\""""
            sh label: 'Update Github actions status', script: cmd
        }
    } else {
        echo 'Propagate status disabled.'
    }
}

podTemplate(
    containers: [
    containerTemplate(
        name: 'bandit',
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
        container('bandit') {
            try {
                stage('GHA status check') {
                    statusUpdate('pending')
                }
                stage('Clone') {
                    sh "git clone ${params.Repo_url} ."
                    sh "git config --global --add safe.directory ${WORKSPACE}"
                    sh 'git config --global --add remote.origin.fetch "+refs/pull/*/head:refs/remotes/origin/pr/*"'
                    if (params.Change_ID != 'null') {
                        sh "git fetch origin pull/${params.Change_ID}/head:pr/${params.Change_ID}"
                        sh "git checkout pr/${params.Change_ID}"
                    }
                    sh "git reset --hard ${params.Commit}"
                }
                stage('Install dependencies') {
                    sh 'python3 -m pip install --cache-dir=/mnt/pip_cache --upgrade pip'
                    sh 'python3 -m pip install --cache-dir=/mnt/pip_cache bandit'
                }
                stage('Run bandit scan') {
                    sh '''echo 'skips: [B101]' >> bandit.yaml'''
                    sh 'bandit -c bandit.yaml -r .'
                }
                statusUpdate('success')
            } catch (Exception ex) {
                statusUpdate('failure')
                currentBuild.result = 'FAILURE'
            }
        }
    }
}
