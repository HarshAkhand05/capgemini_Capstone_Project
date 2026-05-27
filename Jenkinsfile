pipeline {
    agent any

    tools {
        jdk   'JDK21'
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
                bat 'mvn test -Dsurefire.suiteXmlFiles=src/test/resources/testng-all.xml'
            }
        }

        stage('Generate Allure Report') {
            steps {
                bat 'mvn allure:report'
            }
        }

        stage('Publish Allure Report') {
            steps {
                allure([
                    includeProperties: false,
                    jdk: '',
                    reportBuildPolicy: 'ALWAYS',
                    results: [[path: 'allure-results']]
                ])
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts(
                    artifacts: 'target/screenshots/**, allure-results/**, target/surefire-reports/**',
                    allowEmptyArchive: true
                )
            }
        }
    }

    post {
        always {
            echo '=== Pipeline Finished ==='
        }
        success {
            echo 'BUILD SUCCESS — All tests passed'
        }
        failure {
            echo 'BUILD FAILED — Check Allure report'
        }
        unstable {
            echo 'BUILD UNSTABLE — Some tests failed'
        }
    }
}
