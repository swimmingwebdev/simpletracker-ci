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
                        sh "docker push swimminwebdev/${serviceName}:latest"
                        sh "docker push swimminwebdev/${dockerRepoName}:${imageName}"
                    }
                }
            }

            stage('Deploy') {
                when {
                    expression { params.DEPLOY }
                }
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: 'ec2-ssh-key', keyFileVariable: 'KEY')]) {
                        sh '''
                            ssh -i $KEY -o StrictHostKeyChecking=no ubuntu@ec2-34-234-232-11.compute-1.amazonaws.com << 'EOSSH'
                            cd /home/ubuntu/simpletracker
                            docker-compose pull
                            docker-compose up -d
                            EOSSH
                        '''
                    }
                }
            }
        }
    }
}

