def call(dockerRepoName, imageName, portNum) {
    pipeline {
        agent { label 'python_agent2' }

        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
        }

        stages {

            stage('Setup Python Env') {
                steps {
                    sh 'python3 -m venv venv'
                    sh './venv/bin/pip install --upgrade pip'
                    sh './venv/bin/pip install -r requirements.txt'
                }
            }

            stage('Python Lint') {
                steps {
                    sh './venv/bin/pylint --fail-under=5 analyzer/app.py'
                }
            }

            stage('Security Scan') {
                steps {
                    sh './venv/bin/pip install safety'
                    sh './venv/bin/safety check --full-report --output text --exit-code 1'
                }
                post {
                    failure {
                        echo "Security scan failed — fix critical issues!"
                    }
                    success {
                        echo "No critical vulnerabilities found."
                    }
                }
            }

            stage('Package') {
                when {
                    expression { env.GIT_BRANCH == 'origin/main' }
                }
                steps {
                    withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                        sh "echo $TOKEN | docker login -u swimminwebdev --password-stdin"
                        sh "docker build -t ${dockerRepoName}:latest -t swimminwebdev/${dockerRepoName}:${imageName} ."
                        sh "docker push swimminwebdev/${dockerRepoName}:${imageName}"
                    }
                }
            }

            stage('Deploy') {
                when {
                    expression { params.DEPLOY }
                }
                steps {
                    sh "docker stop ${dockerRepoName} || true && docker rm ${dockerRepoName} || true"
                    sh "docker run -d -p ${portNum}:${portNum} --name ${dockerRepoName} ${dockerRepoName}:latest"
                }
            }
        }
    }
}

