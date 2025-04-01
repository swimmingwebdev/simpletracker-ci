def call(Map config) {
    pipeline {
        agent any

        environment {
            DOCKERHUB_REPO = config.dockerhubRepo
        }

        stages {
            stage('Lint') {
                steps {
                    sh 'pylint app.py --fail-under=5 || true'
                }
            }

            stage('Security Scan') {
                steps {
                    sh 'pip install bandit'
                    sh 'bandit -r . || true'
                }
            }

            stage('Package') {
                steps {
                    script {
                        docker.build(DOCKERHUB_REPO).push()
                    }
                }
            }

            stage('Deploy') {
                steps {
                    sshagent(credentials: ['your-ssh-cred-id']) {
                        sh 'ssh user@your-cloud-vm "cd /path/to/compose && docker-compose pull && docker-compose up -d"'
                    }
                }
            }
        }
    }
}
