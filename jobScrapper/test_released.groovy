/* groovylint-disable DuplicateStringLiteral, NestedBlockDepth */

void publishIssue(String comment, String number) {
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
                    cmd2 = 'python3 -m pip install --cache-dir=/mnt/pip_cache' +
                            ' --index-url https://test.pypi.org/simple/ --extra-index-url https://pypi.org/simple/' +
                            '--upgrade jobscrapper'
                    sh script: cmd2
                }
                stage('Verify basic run') {
                    try {
                        sh 'jobscrapper'
                    }
                    catch (Exception ex) {
                        println(ex)
                    }
                }
                stage('Verify help option') {
                    sh 'jobscrapper --help'
                }
                stage('Verify init option') {
                    sh 'jobscrapper --init'
                    sh 'test -f config.json && echo "config.json exists."'
                }
                stage('Verify run option') {
                    command = 'jobscrapper --config config.json 2>&1 | tee saved_log.txt'
                    // verify output here, if every scrapper works correctly
                    sh script: command

                    out = sh(script: 'cat saved_log.txt', returnStdout: true).trim()

                    String pattern = /updateExcel: (.*?) new offers in (.*?)!/
                    results = (out =~ pattern).findAll()
                    // verify, if found 3 matches
                    if (results.size() == 3) {
                        println('Found 3 matches')
                    } else {
                        error "ERROR: Found only ${results.size()} matches. Verify regex and scrapper operation."
                    }
                    // verify, if values are greater than 0
                    for (item in results) {
                        if (item[1].toInteger() > 0) {
                            println("Found ${item[1]} offers using ${item[2]} scrapper.")
                        } else {
                            error "ERROR: ${item[2]} scrapper seems to malfunction!"
                        }
                    }
                }
                stage('Verify run option with debug') {
                    sh 'jobscrapper --config config.json --loglevel DEBUG'
                    // verify output here, if DEBUG logs are showing
                    sh 'test -f debug.log && echo "debug.log exists."'
                    out2 = sh(script: 'cat debug.log', returnStdout: true).trim()

                    String pattern2 = /(?:^|\W)DEBUG(?:$|\W)/
                    results2 = (out2 =~ pattern2).findAll()
                    if (results2.size() > 0) {
                        println("Found ${results2.size()} matches")
                    } else {
                        error "ERROR: Found ${results2.size()} matches. Verify scrapper DEBUG logging."
                    }
                }
            } catch (Exception ex) {
                error("Build failed. $ex")
            }
        }
    }
        }
