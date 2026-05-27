pipeline {

    agent any

    tools {
        jdk 'JDK21'
        maven 'Maven'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Clean Project') {
            steps {
                bat 'mvn clean'
            }
        }

        stage('Run Tests') {
            steps {
                bat 'mvn test -Dmaven.test.failure.ignore=true'
            }
        }

        stage('Generate Allure Report') {
            steps {
                allure([
                    includeProperties: false,
                    jdk: '',
                    results: [[path: 'target/allure-results']]
                ])
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts(
                    artifacts: 'target/screenshots/**, target/allure-results/**, selenium-grid.log',
                    allowEmptyArchive: true
                )
            }
        }
    }

    post {

       

        failure {
            echo 'BUILD FAILED — Check Allure report'
        }

        unstable {
            echo '⚠ BUILD UNSTABLE — Some tests failed'
        }

        success {
            echo 'BUILD SUCCESS — All tests passed'
        }
    }
}
