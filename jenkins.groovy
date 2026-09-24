pipeline {
    agent any

    environment {
        IMAGE_NAME = "adhilsharaf/react-demo"
        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    stages {

        stage('Cleanup Workspace') {
            steps {
                deleteDir()
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Install Dependencies') {
            steps {
                bat 'npm install'
            }
        }

        stage('Sonar Scan') {
            steps {
                script {
                    def scannerHome = tool 'sonar-scanner'

                    withSonarQubeEnv('sonarqube') {
                        withCredentials([
                            string(
                                credentialsId: 'sonarqube-token',
                                variable: 'SONAR_TOKEN'
                            )
                        ]) {
                            bat "\"${scannerHome}\\bin\\sonar-scanner.bat\" -Dsonar.token=%SONAR_TOKEN%"
                        }
                    }
                }
            }
        }

        stage('Test Docker') {
            steps {
                bat 'docker ps'
            }
        }

        stage('Debug') {
            steps {
                bat 'cd'
                bat 'dir'
            }
        }

        stage('Build Docker Image') {
            steps {
                bat 'docker build -t %IMAGE_NAME%:%IMAGE_TAG% .'
            }
        }

        stage('Run Trivy Security Scan') {
            steps {
                bat 'trivy image %IMAGE_NAME%:%IMAGE_TAG%'
            }
        }

        stage('Push Image') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    bat '''
                        echo %DOCKER_PASS% | docker login -u %DOCKER_USER% --password-stdin
                        docker push %IMAGE_NAME%:%IMAGE_TAG%
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                bat '''
                    docker stop react-app >nul 2>&1 || echo No existing container
                    docker rm react-app >nul 2>&1 || echo No existing container
                    docker run -d --name react-app -p 8081:80 %IMAGE_NAME%:%IMAGE_TAG%
                '''
            }
        }
    }
}