/* groovylint-disable DuplicateStringLiteral, NestedBlockDepth, UnnecessaryGString */

void publishIssue(String title, String body) {
    try {
        verify_cmd = 'curl -L \
                    -H "Accept: application/vnd.github+json" \
                    https://api.github.com/repos/wkobiela/jobscrapper/issues'

        result = sh(script: verify_cmd, returnStdout: true).trim()

        println(result)
        title_test = 'Program exits with exit code 1, when no parameter is given'
        if (result.contains(title_test)) {
            println('Yes it is!!')
        } 
        // withCredentials([string(credentialsId: 'github_token', variable: 'TOKEN')]) {
        //     cmd = """curl "https://api.github.com/repos/wkobiela/jobScrapper/issues/" \
        //     -H "Content-Type: application/json" \
        //     -H "Authorization: token """ + TOKEN + """\" \
        //     -X POST \
        //     -d "{\\"title\\":\\"${title}\\",\\"body\\": \\"${body}\\",\\"labels\\":[\\"bug\\"]}\""""
        //     sh label: 'Add comment to Github PR', script: cmd
        // }
    } catch (Exception ex) {
        echo "Cannot create issue. \n $ex"
    }
}

String default_body = "Automatic checks alarming failure. Check: $BUILD_URL"

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
                        String basic_thread = '[automatic checks] Basic scrapper call failed'
                        publishIssue(basic_thread, default_body)
                        println(ex)
                    }
                }
                stage('Verify help option') {
                    sh 'jobscrapper --help 2>&1 | tee help_log.txt'
                    out1 = sh(script: 'cat help_log.txt', returnStdout: true).trim()

                    String pattern1 = /(?:^|\W)usage(?:$|\W)/
                    results1 = (out1 =~ pattern1).findAll()
                    if (results1.size() == 1) {
                        println("Found ${results1.size()} matches, help seems to work.")
                    } else {
                        String help_thread = '[automatic checks] --help option is not working'
                        publishIssue(help_thread, default_body)
                        error "ERROR: Found ${results1.size()} matches. Verify scrapper help option."
                    }
                }
                stage('Verify init option') {
                    sh 'jobscrapper --init'
                    sh 'test -f config.json && echo "config.json exists."'
                }
                stage('Verify run option') {
                    command = 'jobscrapper --config config.json 2>&1 | tee run_log.txt'
                    // verify output here, if every scrapper works correctly
                    sh script: command

                    out2 = sh(script: 'cat run_log.txt', returnStdout: true).trim()

                    String pattern2 = /updateExcel: (.*?) new offers in (.*?)!/
                    results2 = (out2 =~ pattern2).findAll()
                    // verify, if found 3 matches
                    if (results2.size() == 3) {
                        println('Found 3 matches')
                    } else {
                        String run_thread = '[automatic checks] One of scrappers is not working'
                        publishIssue(run_thread, default_body)
                        error "ERROR: Found only ${results.size()} matches. Verify regex and scrapper operation."
                    }
                    // verify, if values are greater than 0
                    for (item in results2) {
                        if (item[1].toInteger() > 0) {
                            println("Found ${item[1]} offers using ${item[2]} scrapper.")
                        } else {
                            String run_thread = "[automatic checks] ${item[2]} scrapper is not working"
                            publishIssue(run_thread, default_body)
                            error "ERROR: ${item[2]} scrapper seems to malfunction!"
                        }
                    }
                }
                stage('Verify run option with debug') {
                    sh 'jobscrapper --config config.json --loglevel DEBUG'
                    sh 'test -f debug.log && echo "debug.log exists."'
                    
                    out3 = sh(script: 'cat debug.log', returnStdout: true).trim()

                    String pattern3 = /(?:^|\W)DEBUG(?:$|\W)/
                    results3 = (out3 =~ pattern3).findAll()
                    if (results3.size() > 0) {
                        println("Found ${results3.size()} matches")
                    } else {
                        String debug_thread = "[automatic checks] Debug logging is not working"
                        publishIssue(debug_thread, default_body)
                        error "ERROR: Found ${results3.size()} matches. Verify scrapper DEBUG logging."
                    }
                }
            } catch (Exception ex) {
                error("Build failed. $ex")
            }
        }
    }
        }
