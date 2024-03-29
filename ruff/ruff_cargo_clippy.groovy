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
            stage('Cargo-clippy') {
                sh 'git clone https://github.com/charliermarsh/ruff.git'
                dir('ruff') {
                    sh 'rustup component add clippy'
                    sh 'cargo clippy --workspace --all-targets --all-features -- -D warnings'
                }
            }
        }
    }
}
