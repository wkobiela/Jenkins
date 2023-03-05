podTemplate(
    containers: [
    containerTemplate(
        name: 'ubuntu',
        image: 'ubuntu:latest',
        command: 'sleep',
        args: '99d',
        resourceRequestCpu: '1',
        resourceLimitCpu: '2',
        resourceRequestMemory: '1G',
        resourceLimitMemory: '2G'
        )],
        nodeSelector: 'test=false'
        ) {

    node(POD_LABEL) {
        container('ubuntu') {
            stage('Cargo build') {
                sh 'git clone https://github.com/charliermarsh/ruff.git'
                dir('ruff') {
                    sh 'rustup show'
                    sh 'cargo build --all'
                    sh './target/debug/ruff_dev generate-all'
                }
            }
        }
    }
}
