/* groovylint-disable DuplicateStringLiteral, NestedBlockDepth */

def checkName = "${env.JOB_BASE_NAME}_python${params.Python}"

void statusUpdate(String status) {
    if (!params.propagateStatus) {
        echo 'Propagate status disabled.'
        return
    }

    withCredentials([string(credentialsId: 'github_token', variable: 'TOKEN')]) {
        def cmd = """
            curl -s -X POST \
            -H "Content-Type: application/json" \
            -H "Authorization: token ${TOKEN}" \
            https://api.github.com/repos/wkobiela/jobScrapper/statuses/${params.Commit} \
            -d '{
                "state": "${status}",
                "context": "${checkName}",
                "target_url": "${env.BUILD_URL}"
            }'
        """
        sh label: 'Update Github status', script: cmd
    }
}

currentBuild.displayName = "${checkName} #${env.BUILD_NUMBER}"

boolean testsFailed = false

podTemplate(
    securityContext: [
        fsGroup: 1000
    ],
    nodeSelector: 'test=true',
    volumes: [
        persistentVolumeClaim(
            mountPath: '/mnt/pip_cache',
            claimName: 'pip-cache-pvc',
            readOnly: false
        )
    ],
    containers: [
        containerTemplate(
            name: 'jobscrapper',
            image: 'wkobiela/jobscrapper_base:latest',
            alwaysPullImage: true,
            command: 'sleep',
            args: '99d',
            runAsUser: 1001,
            resourceRequestCpu: '1',
            resourceLimitCpu: '2',
            resourceRequestMemory: '2Gi',
            resourceLimitMemory: '3Gi',
            resourceRequestEphemeralStorage: '2Gi'
        )
    ]
) {

    node(POD_LABEL) {
        container('jobscrapper') {
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
                    sh "python${params.Python} --version"
                    sh "python${params.Python} -m pip install --cache-dir=/mnt/pip_cache --upgrade pip"
                    sh "python${params.Python} -m pip install --cache-dir=/mnt/pip_cache -r requirements.txt"
                }

                stage('Build package') {
                    sh "python${params.Python} -m build"
                }

                stage('Install package') {
                    def wheelFilename = sh(
                        script: 'find dist -name "*.whl" -print -quit',
                        returnStdout: true
                    ).trim()

                    sh "python${params.Python} -m pip install --cache-dir=/mnt/pip_cache ${wheelFilename}"
                }

                stage('Run scrapper') {
                    sh 'jobscrapper --config jobscrapper/config.json --loglevel DEBUG'

                    def out = sh(script: 'cat debug.log', returnStdout: true)
                    if (out =~ /(?i)(exception|error)/) {
                        error 'ERROR: Log contains exception or error!'
                    }
                }

                stage('Check files') {
                    sh 'test -f jobs.xlsx'
                    sh 'cp jobs.xlsx results.xlsx'
                    sh 'test -f debug.log'
                }

                stage('Run tests') {
                    def result = sh(
                        script: """
                            python${params.Python} -m pytest \
                            --html=report.html \
                            --cov-report term \
                            --cov-config=.coveragerc \
                            --cov-report html \
                            --cov=jobscrapper
                        """,
                        returnStatus: true
                    )

                    if (result != 0) {
                        unstable('Test stage failed.')
                        testsFailed = true
                    }
                }

                stage('Publish reports') {
                    publishHTML(target: [
                        allowMissing: true,
                        keepAll: true,
                        reportDir: WORKSPACE,
                        reportFiles: '*.html',
                        reportName: 'Pytest Report'
                    ])

                    publishHTML(target: [
                        allowMissing: true,
                        keepAll: true,
                        reportDir: "${WORKSPACE}/htmlcov",
                        reportFiles: '*.html',
                        reportName: 'Pytest Coverage'
                    ])

                    statusUpdate(testsFailed ? 'failure' : 'success')
                }

            } catch (Exception ex) {
                statusUpdate('failure')
                throw ex
            } finally {
                stage('Archive artifacts') {
                    archiveArtifacts(
                        allowEmptyArchive: true,
                        artifacts: '**/*.xlsx, **/*.whl, **/*.tar.gz'
                    )
                }
            }
        }
    }
}
