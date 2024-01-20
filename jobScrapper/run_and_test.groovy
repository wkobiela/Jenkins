/* groovylint-disable DuplicateStringLiteral, NestedBlockDepth */
checkName = "${env.JOB_BASE_NAME}_python${params.Python}"

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

currentBuild.displayName = "${checkName} #$env.BUILD_NUMBER"

boolean testsFailed = false

node('linux') {
    stage('Github check') {
        statusUpdate('pending')
    }
}

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
                    sh "python${params.Python} --version"
                    sh "python${params.Python} -m pip install --cache-dir=/mnt/pip_cache --upgrade pip"
                    sh "python${params.Python} -m pip install --cache-dir=/mnt/pip_cache -r requirements.txt"
                }
                stage('Build package') {
                    sh "python${params.Python} -m build"
                }
                stage('Install package') {
                    String wheelFilename = sh(script: 'find "dist/" -type f -name "*.whl"', returnStdout: true).trim()
                    sh "python${params.Python} -m pip install --cache-dir=/mnt/pip_cache ${wheelFilename}"
                }
                stage('Run scrapper') {
                    sh 'jobscrapper --config jobscrapper/config.json | tee run.log'
                    command = 'cat run.log'
                    out = sh(script: command, returnStdout: true).trim()
                    String searchTermRegex = /(?i)(exception|error|ERROR)/
                    if (out.toLowerCase() =~ searchTermRegex) {
                        error 'ERROR: Log is containing exception or error!'
                    } else {
                        echo 'All good.'
                    }
                }
                stage('Check files') {
                    sh 'test -f jobs.xlsx && echo "$FILE exists."'
                    sh 'cp jobs.xlsx results.xlsx'
                    sh 'test -f debug.log && echo "$FILE exists."'
                }
                stage('Run tests') {
                    result = sh(script: "python${params.Python} -m pytest --html=report.html --cov-report term \
                    --cov-report html --cov=jobscrapper", returnStatus: true)
                    if (result != 0) {
                            unstable('Test stage exited with non zero exit code.')
                            testsFailed = true
                    }
                }
                stage('Publish reports') {
                    publishHTML(target: [
                            allowMissing: true,
                            alwaysLinkToLastBuild: false,
                            keepAll: true,
                            reportDir: "$WORKSPACE",
                            reportFiles: '*.html',
                            reportName: 'Pytest Report'
                    ])
                    publishHTML(target: [
                            allowMissing: true,
                            alwaysLinkToLastBuild: false,
                            keepAll: true,
                            reportDir: "$WORKSPACE/htmlcov",
                            reportFiles: '*.html',
                            reportName: 'Pytest-cov Report'
                    ])
                    if (testsFailed) {
                        statusUpdate('failure')
                    } else {
                        statusUpdate('success')
                    }
                }
            } catch (Exception ex) {
                statusUpdate('failure')
                error("Build failed. $ex")
            } finally {
                stage('Archive artifacts') {
                    archiveArtifacts(allowEmptyArchive: true, artifacts: '**/*.xlsx, **/*.whl, **/*.tar.gz')
                    }
            }
        }
    }
}
