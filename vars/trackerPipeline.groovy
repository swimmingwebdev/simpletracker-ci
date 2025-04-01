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
                    sh './venv/bin/pip install -r analyzer/requirements.txt'
                }
            }

            stage('Python Lint') {
                steps {
                    sh './venv/bin/pip install pylint'
                    sh './venv/bin/pylint --fail-under=5 analyzer/app.py'
                }
            }

            stage('Security Scan') {
                steps {
                    sh './venv/bin/pip install safety'
                    script {
                        def result = sh(
                            script: './venv/bin/safety scan --full-report --keyless --output screen || true',
                            returnStdout: true
                        ).trim()

                        echo result

                        if (result.contains('CRITICAL') || result.contains('HIGH')) {
                            error("Security scan failed — CRITICAL or HIGH vulnerabilities found.")
                        }
                    }
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
                        sh "docker build -f ${imageName}/Dockerfile -t ${dockerRepoName}:latest -t swimminwebdev/${dockerRepoName}:${imageName} ${imageName}/"
                        sh "docker push swimminwebdev/${dockerRepoName}:${imageName}"
                    }
                }
            }

            stage('Deploy') {
                when {
                    expression { params.DEPLOY }
                }
                steps {
                    sshagent(credentials: ['vm-ssh-key']) {
                        sh '''
                            ssh -o StrictHostKeyChecking=no azureuser@172.210.180.227 << EOF
                                cd /home/azureuser/simpletracker
                                git pull origin main
                                docker-compose pull
                                docker-compose up -d
                            EOF
                        '''
                    }
                }
            }
        }
    }
}

