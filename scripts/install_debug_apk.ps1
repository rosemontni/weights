param(
    [switch]$Rebuild
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkPath = "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$sdkPath = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$adbPath = Join-Path $sdkPath "platform-tools\adb.exe"
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"

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
Assert-PathExists $sdkPath "Android SDK"
Assert-PathExists $adbPath "adb"
Assert-PathExists (Join-Path $projectRoot "gradlew.bat") "Gradle wrapper"

$env:JAVA_HOME = $jdkPath
$env:ANDROID_HOME = $sdkPath
$env:ANDROID_SDK_ROOT = $sdkPath
$env:Path = "$jdkPath\bin;$env:Path"

Set-Location $projectRoot

if ($Rebuild -or -not (Test-Path $apkPath)) {
    Write-Step "Building debug APK"
    Invoke-Gradle @("assembleDebug")
}

Write-Step "Checking connected Android devices"
$deviceLines = & $adbPath devices
$authorizedDevices = $deviceLines | Where-Object { $_ -match "\tdevice$" }

if (-not $authorizedDevices) {
    Write-Host ($deviceLines -join [Environment]::NewLine)
    throw "No authorized Android device detected. Unlock the phone, enable USB debugging, and accept the prompt."
}

Write-Step "Installing debug APK"
& $adbPath install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    throw "adb install failed."
}

Write-Host ""
Write-Host "Debug APK installed successfully." -ForegroundColor Green
Write-Host "APK: $apkPath"
