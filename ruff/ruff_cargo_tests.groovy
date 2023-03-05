podTemplate(
    containers: [
    containerTemplate(
        name: 'rust',
        image: 'rust:latest',
        command: 'sleep',
        args: '99d',
        resourceRequestCpu: '1',
        resourceLimitCpu: '2',
        resourceRequestMemory: '2Gi',
        resourceLimitMemory: '4Gi',
        )],
        nodeSelector: 'bigger=true'
        ) {

    node(POD_LABEL) {
        container('rust') {
            stage('Cargo-tests') {
                sh 'git clone https://github.com/charliermarsh/ruff.git'
                dir('ruff') {
                    sh 'apt update'
                    sh 'apt install python3-pip --no-install-recommends -y'
                    sh 'rustup show'
                    sh 'cargo install cargo-insta'
                    try {
                        sh 'pip install black[d]==22.12.0'
                    } catch (Exception e) {
                        println("ERROR: ${e}")
                    }
                    sh 'cargo insta test --all --all-features --delete-unreferenced-snapshots'
                    sh 'git diff --exit-code'
                    sh 'cargo test --package ruff_cli --test black_compatibility_test -- --ignored'
                    sh 'cargo doc --all --no-deps'
                    sh 'rm -rf ruff'
                }
            }
        }
    }
}
