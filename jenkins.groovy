pipeline {
    agent any

    environment {
      IMAGE_NAME = "adhilsharaf/react-demo"
      IMAGE_TAG = "${BUILD_NUMBER}"
    }

    tools {
    sonarRunner 'sonar-scanner'
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
          dir('react-demo') {
            bat 'npm install'
          }
         } 
        }

        stage('Sonar Scan') {
        steps {
        withSonarQubeEnv('sonarqube') {
            withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                dir('react-demo') {
                    bat 'sonar-scanner -Dsonar.token=%SONAR_TOKEN%'
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
           bat 'docker build -t %IMAGE_NAME%:%IMAGE_TAG% -f react-demo/Dockerfile react-demo'
          }
        }
        stage('Install & Run Trivy') {
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
