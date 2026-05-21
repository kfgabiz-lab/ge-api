# BO API 빌드 스크립트 (Java 25)
# 사용법: .\build.ps1

$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"

Write-Host ">>> Gradle 데몬 종료"
.\gradlew.bat --stop

Write-Host ">>> Java 25 클린 빌드 시작"
.\gradlew.bat clean bootJar --rerun-tasks

if ($LASTEXITCODE -eq 0) {
    Write-Host ">>> 빌드 성공"
} else {
    Write-Host ">>> 빌드 실패"
    exit 1
}
