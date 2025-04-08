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
                    if (dockerRepoName != "storage") {
                    sh "./venv/bin/pip install -r ${dockerRepoName}/requirements.txt"
                } else {
                    echo "Skipping pip install for storage — requirements already installed in Docker image"
                }
                    //     script {
                    //         if (dockerRepoName == "storage") {
                    //             echo "Skipping apt-get install for storage — already handled in Dockerfile"
                    //             // echo "Installing system dependencies for mysqlclient (storage only)"
                    //             // sh '''
                    //             //     apt-get update && \
                    //             //     apt-get install -y build-essential default-libmysqlclient-dev pkg-config python3-dev
                    //             // '''
                    //         }
                    // }
                    // sh "./venv/bin/pip install -r ${dockerRepoName}/requirements.txt"
                }
            }

            stage('Python Lint') {
                steps {
                    sh './venv/bin/pip install pylint'
                    sh "./venv/bin/pylint --fail-under=5 ${dockerRepoName}/app.py"
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
                        sh "docker build -f ${imageName}/Dockerfile -t swimminwebdev/${dockerRepoName}:latest -t swimminwebdev/${dockerRepoName}:${imageName} ${imageName}/"
                        sh "docker push swimminwebdev/${dockerRepoName}:latest"
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
                        sh "ssh -i \"$KEY\" -o StrictHostKeyChecking=no ubuntu@ec2-34-234-232-11.compute-1.amazonaws.com 'cd /home/ubuntu/simpletracker && docker-compose pull ${dockerRepoName} && docker-compose up -d ${dockerRepoName}'"
                    }
                }
            }
        }
    }
}

