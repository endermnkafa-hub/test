$ErrorActionPreference = 'Stop'
if (Test-Path .\gradlew.bat) {
    & .\gradlew.bat clean build
    exit $LASTEXITCODE
}
Write-Host 'No Gradle wrapper found in this source tree.' -ForegroundColor Yellow
Write-Host 'Copy gradlew.bat and the gradle folder from your working Forge 1.20.1 project into this folder, then run:'
Write-Host '  .\gradlew.bat clean build'
