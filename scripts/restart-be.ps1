# Bo BE 서버 재시작 스크립트
# Jenkins 배포 스테이지에서 SSH로 호출됨

$projectPath = "C:\Users\Administrator\.gemini\workspace\Bo\bo-api"
$buildPath   = "$projectPath\build\libs"
$profile     = "dev"
$port        = 8002

Write-Host "=== Bo BE 재시작 시작 ==="

# 8002 포트 사용 중인 프로세스 종료
$conn = netstat -ano | Select-String ":$port\s"
if ($conn) {
    $pid = ($conn -split '\s+')[-1]
    Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
    Write-Host "기존 프로세스(PID: $pid) 종료"
    Start-Sleep -Seconds 3
} else {
    Write-Host "실행 중인 BE 프로세스 없음"
}

# 최신 jar 파일 선택
$jar = Get-ChildItem -Path $buildPath -Filter "*.jar" |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1

if (-not $jar) {
    Write-Error "jar 파일을 찾을 수 없습니다: $buildPath"
    exit 1
}

# 백그라운드로 서버 시작
Start-Process -FilePath "java" `
    -ArgumentList "-jar", "-Dspring.profiles.active=$profile", $jar.FullName `
    -WorkingDirectory $buildPath `
    -WindowStyle Hidden

Write-Host "=== Bo BE 시작 완료: $($jar.Name) ==="
