pipeline {
    agent any

    environment {
      IMAGE_NAME = "kopilnagi/react-demo-docker"
      IMAGE_TAG = "${BUILD_NUMBER}"
    }

    tools {
      sonarQube 'sonar-scanner'
    }

    stages {

        stage('Cleanup Workspace') {
        steps {
          deleteDir()
          }
        }
        stage('Checkout') {
            steps {
                git branch: 'main',
                  credentialsId: 'GitHub-ssh-new',
                  url: 'git@github.com:kapilnagi007/My-First-Repo.git'
            }
        }
        stage('Install Dependencies'){
          steps{
            sh 'npm install -- force'
          }
        }

        stage('Sonar Scan') {
          steps {
            script{
              def scannerHome = tool 'sonar-scanner'

              withSonarQubeEnv('sonarqube') {
                sh '${scannerHome}/bin/sonar-scanner'
              }
            }
          }
        }
        
        stage('Test Docker'){
          steps {
            sh 'docker ps'
          }
        }

        stage('Debug') {
          steps {
            sh 'pwd'
            sh 'ls -la'
          }
        } 

        stage('Build Docker Image') {
          steps{
            sh 'docker build -t $IMAGE_NAME:$IMAGE_TAG -f react-demo/Dockerfile react-demo'
          }
        }
        stage('Install & Run Trivy') {
            steps {
                script {
                    // Downloads the standalone binary directly to the workspace without needing root access
                    sh 'curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b .'
                    
                    // Execute the scan using the local binary (./trivy)
                    sh './trivy image $IMAGE_NAME:$IMAGE_TAG'
                }
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
            ])
            {
              sh '''
              echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin
              docker push $IMAGE_NAME:$IMAGE_TAG
              '''
            }
          }
        }

        stage('Deploy'){
          steps {
            sh '''
            docker stop react-app || true
            docker rm react-app || true

            docker run -d --name react-app -p 80:80 $IMAGE_NAME:$IMAGE_TAG
            '''
          }
        }
    }
}
