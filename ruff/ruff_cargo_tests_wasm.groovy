/* groovylint-disable NestedBlockDepth */

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
            stage('Cargo-tests (wasm)') {
                sh 'git clone https://github.com/charliermarsh/ruff.git'
                sh 'curl -fsSL https://deb.nodesource.com/setup_18.x | bash -'
                sh 'apt install nodejs -y'
                sh 'curl https://rustwasm.github.io/wasm-pack/installer/init.sh -sSf | sh'
                sh 'rustup target add wasm32-unknown-unknown'
                sh 'cd ruff/crates/ruff_wasm && wasm-pack test --node'
            }
        }
    }
}
