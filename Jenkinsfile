pipeline {
    agent any
    tools {
        jdk   'JDK21'
        maven 'Maven'
        allure 'allure'  
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
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    bat 'mvn test -Dsurefire.suiteXmlFiles=src/test/resources/testng-all.xml'
                }
            }
        }
        stage('Generate Allure Report') {
            steps {
                // USE allure:report NOT allure:serve
                bat 'mvn allure:report'
            }
        }
        stage('Publish Allure Report') {
            steps {
                // USE publishHTML as fallback if Allure plugin not installed
                publishHTML(target: [
                    allowMissing         : false,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,
                    reportDir            : 'target/site/allure-maven-plugin',
                    reportFiles          : 'index.html',
                    reportName           : 'Allure Report'
                ])
            }
        }
        stage('Archive') {
            steps {
                archiveArtifacts(
                    artifacts: 'target/screenshots/**, target/allure-results/**, target/surefire-reports/**',
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
            echo 'BUILD FAILED — Defect found'
        }
       
    }
}
