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
            stage('Tests-scripts') {
                sh 'git clone https://github.com/charliermarsh/ruff.git'
                dir('ruff') {
                    sh 'rustup show'
                    sh './scripts/add_rule.py --name DoTheThing --prefix PL --code C0999 --linter pylint'
                    sh 'cargo check'
                    sh 'cargo fmt --all --check'
                    sh './scripts/add_plugin.py test --url https://pypi.org/project/-test/0.1.0/ --prefix TST'
                    sh './scripts/add_rule.py --name FirstRule --prefix TST --code 001 --linter test'
                    sh 'cargo check'
                    sh 'cargo fmt --all --check'
                }
            }
        }
    }
}
