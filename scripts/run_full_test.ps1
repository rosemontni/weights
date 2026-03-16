param(
    [switch]$SkipClean
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkPath = "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$defaultSdkPath = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
$debugReportPath = Join-Path $projectRoot "app\build\reports\tests\testDebugUnitTest\index.html"
$releaseReportPath = Join-Path $projectRoot "app\build\reports\tests\testReleaseUnitTest\index.html"

function Write-Step([string]$message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Assert-PathExists([string]$path, [string]$label) {
    if (-not (Test-Path $path)) {
        throw "$label not found: $path"
    }
}

function Invoke-Gradle([string[]]$arguments) {
    Write-Host "Running: .\gradlew.bat $($arguments -join ' ')"
    & ".\gradlew.bat" @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle command failed: .\gradlew.bat $($arguments -join ' ')"
    }
}

Write-Step "Checking toolchain"
Assert-PathExists $jdkPath "JDK"
Assert-PathExists $defaultSdkPath "Android SDK"
Assert-PathExists (Join-Path $projectRoot "gradlew.bat") "Gradle wrapper"

$env:JAVA_HOME = $jdkPath
$env:ANDROID_HOME = $defaultSdkPath
$env:ANDROID_SDK_ROOT = $defaultSdkPath
$env:Path = "$jdkPath\bin;$env:Path"

Set-Location $projectRoot

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"

if (-not $SkipClean) {
    Write-Step "Cleaning generated test output"
    $pathsToRemove = @(
        "app\build\test-results",
        "app\build\reports\tests"
    )

    foreach ($relativePath in $pathsToRemove) {
        $fullPath = Join-Path $projectRoot $relativePath
        if (Test-Path $fullPath) {
            Remove-Item $fullPath -Recurse -Force
        }
    }
}

Write-Step "Running unit tests"
Invoke-Gradle @("test")

Write-Step "Building debug APK"
Invoke-Gradle @("assembleDebug")

Write-Step "Verifying outputs"
Assert-PathExists $apkPath "Debug APK"
Assert-PathExists $debugReportPath "Debug unit test report"
Assert-PathExists $releaseReportPath "Release unit test report"

Write-Host ""
Write-Host "Full project test completed successfully." -ForegroundColor Green
Write-Host "APK: $apkPath"
Write-Host "Debug report: $debugReportPath"
Write-Host "Release report: $releaseReportPath"
