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

        stage('Build') {
            steps {
                bat 'mvn clean compile -q'
            }
        }

        stage('Start Selenium Grid') {
            steps {
                bat '''
                    start /B java -jar selenium-server-4.41.0.jar standalone --max-sessions 4 --session-timeout 300 > selenium-grid.log 2>&1
                    timeout /T 15 /NOBREAK
                '''
            }
        }

        stage('Run Tests') {
            steps {
                bat 'mvn test -DsuiteXmlFile=src/test/resources/testng-all.xml -Dheadless=true -Dmaven.test.failure.ignore=true'
            }
        }

        stage('Allure Report') {
            steps {
                allure([
                    reportBuildPolicy: 'ALWAYS',
                    results: [[path: 'target/allure-results']]
                ])
            }
        }

        stage('HTML Report') {
            steps {
                publishHTML([
                    allowMissing         : true,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,
                    reportDir            : 'target/surefire-reports',
                    reportFiles          : 'index.html',
                    reportName           : 'TestNG Report'
                ])
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/screenshots/**, target/allure-results/**, selenium-grid.log',
                                 allowEmptyArchive: true
            }
        }
    }

    post {
        always {
            bat 'taskkill /F /IM java.exe 2>NUL || exit 0'
            echo "Build Result: ${currentBuild.result}"
        }
        success {
            echo '✅ BUILD SUCCESS — All tests passed'
        }
        failure {
            echo '❌ BUILD FAILED — Check Allure report'
        }
        unstable {
            echo '⚠ BUILD UNSTABLE — Some tests failed'
        }
    }
}
