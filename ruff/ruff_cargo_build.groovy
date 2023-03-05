podTemplate(
    containers: [
    containerTemplate(
        name: 'rust',
        image: 'rust:latest',
        command: 'sleep',
        args: '99d',
        resourceRequestCpu: '1',
        resourceLimitCpu: '2',
        resourceRequestMemory: '3Gi',
        resourceLimitMemory: '4Gi',
        )],
        nodeSelector: 'bigger=true'
        ) {

    node(POD_LABEL) {
        container('rust') {
            stage('Cargo-build') {
                sh 'git clone https://github.com/charliermarsh/ruff.git'
                dir('ruff') {
                    sh 'rustup show'
                    sh 'cargo build --all'
                    sh './target/debug/ruff_dev generate-all'
                    sh '''git diff --quiet README.md || \
                    echo "::error file=README.md::This file is outdated. Run 'cargo dev generate-all'."'''
                    sh '''git diff --quiet ruff.schema.json || \
                    echo "::error file=ruff.schema.json::This file is outdated. Run 'cargo dev generate-all'."'''
                    sh '''git diff --exit-code -- README.md ruff.schema.json docs'''
                }
                sh 'rm -rf ruff'
            }
        }
    }
}
