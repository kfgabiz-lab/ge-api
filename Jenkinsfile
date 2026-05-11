pipeline {
    agent any

    tools {
        jdk 'JDK21'
    }

    options {
        // 빌드 타임아웃 30분
        timeout(time: 30, unit: 'MINUTES')
        // 콘솔 출력 타임스탬프
        timestamps()
    }

    stages {
        // 소스 체크아웃
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // Gradle로 bootJar 빌드 (테스트 제외)
        stage('BE Build') {
            steps {
                echo '=== BE 빌드 시작 ==='
                sh 'chmod +x gradlew'
                sh './gradlew bootJar --no-daemon -x test'
                echo '=== BE 빌드 완료 ==='
            }
        }

        // SSH로 호스트 서버에 접속하여 BE 재시작
        stage('Deploy') {
            steps {
                echo '=== BE 배포 시작 ==='
                sshagent(['bo-be-server-ssh']) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no Administrator@host.docker.internal \
                            "powershell -ExecutionPolicy Bypass -File C:/Users/Administrator/.gemini/workspace/Bo/bo-api/scripts/restart-be.ps1"
                    '''
                }
                echo '=== BE 배포 완료 ==='
            }
        }
    }

    post {
        success {
            echo '✅ BE 빌드 및 배포 성공!'
            echo '결과물: build/libs/*.jar'
        }
        failure {
            echo '❌ BE 빌드/배포 실패 — 콘솔 로그를 확인하세요.'
        }
        always {
            echo "빌드번호: #${env.BUILD_NUMBER}"
        }
    }
}
