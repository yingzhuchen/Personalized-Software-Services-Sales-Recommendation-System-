pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Unit + latency regression gate') {
      steps {
        sh 'chmod +x scripts/measure-validation-gate.sh'
        sh './scripts/measure-validation-gate.sh'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
          archiveArtifacts artifacts: 'target/ci-validation-gate.json,target/site/jacoco/**', allowEmptyArchive: true
        }
      }
    }
  }
}
