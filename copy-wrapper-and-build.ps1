$ErrorActionPreference = 'Stop'
$sourceProject = 'C:\universalcrew-1.20.1-v3-source'

if (-not (Test-Path .\gradlew.bat)) {
    if (Test-Path "$sourceProject\gradlew.bat") {
        Copy-Item "$sourceProject\gradlew.bat" . -Force
    }
}

if (-not (Test-Path .\gradle\wrapper\gradle-wrapper.jar)) {
    if (Test-Path "$sourceProject\gradle\wrapper") {
        New-Item -ItemType Directory -Force .\gradle\wrapper | Out-Null
        Copy-Item "$sourceProject\gradle\wrapper\*" .\gradle\wrapper\ -Force
    }
}

if (-not (Test-Path .\gradlew.bat)) {
    throw 'gradlew.bat bulunamadı. Çalışan Forge 1.20.1 projenizden gradlew.bat ve gradle\wrapper klasörünü bu projeye kopyalayın.'
}

& .\gradlew.bat clean build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host ''
Write-Host 'BUILD BASARILI.' -ForegroundColor Green
Write-Host 'Jar: build\libs\universal-npc-spawner-1.20.1-1.0.0.jar' -ForegroundColor Cyan
