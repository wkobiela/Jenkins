podTemplate(
    containers: [
    containerTemplate(
        name: 'rust',
        image: 'rust:latest',
        command: 'sleep',
        args: '99d',
        resourceRequestCpu: '1',
        resourceLimitCpu: '2',
        resourceRequestMemory: '1Gi',
        resourceLimitMemory: '2Gi',
        )],
        nodeSelector: 'test=true'
        ) {

    node(POD_LABEL) {
        container('rust') {
            stage('Cargo-fmt') {
                sh 'git clone https://github.com/charliermarsh/ruff.git'
                dir('ruff') {
                    sh 'rustup component add rustfmt'
                    sh 'cargo fmt --all --check'
                }
            }
        }
    }
}
