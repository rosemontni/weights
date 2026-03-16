param(
    [string]$AvdName = "Medium_Phone_API_36.1",
    [switch]$KeepEmulatorRunning
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$adbPath = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulatorPath = Join-Path $sdkRoot "emulator\emulator.exe"
$jdkPath = "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
$outputDir = Join-Path $projectRoot "docs\assets"
$packageName = "com.codex.wyzescalebridge.debug"
$activityName = "$packageName/com.codex.wyzescalebridge.MainActivity"
$launchedEmulator = $false
$deviceSerial = $null

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

function Get-EmulatorSerial() {
    $devices = & $adbPath devices
    $serials = @(
        $devices |
            Select-String "emulator-\d+\s+device" |
            ForEach-Object { ($_ -split '\s+')[0] }
    )
    return @($serials)
}

function Invoke-Adb([string[]]$arguments) {
    if (-not $script:deviceSerial) {
        throw "No emulator serial has been selected."
    }

    & $adbPath -s $script:deviceSerial @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed for device $script:deviceSerial: $($arguments -join ' ')"
    }
}

function Wait-ForDeviceBoot() {
    for ($i = 0; $i -lt 120; $i++) {
        $serials = Get-EmulatorSerial
        if (@($serials).Length -gt 0) {
            $script:deviceSerial = @($serials)[0]
            break
        }
        Start-Sleep -Seconds 2
    }

    if (-not $script:deviceSerial) {
        throw "Timed out waiting for an emulator transport."
    }

    for ($i = 0; $i -lt 120; $i++) {
        $bootOutput = & $adbPath -s $script:deviceSerial shell getprop sys.boot_completed
        $boot = if ($bootOutput) { ($bootOutput | Select-Object -First 1).Trim() } else { "" }
        if ($boot -eq "1") {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for emulator boot."
}

function Ensure-Emulator() {
    $serials = Get-EmulatorSerial
    if (@($serials).Length -gt 0) {
        $script:deviceSerial = @($serials)[0]
        Write-Host "Using already running emulator."
        return
    }

    Write-Step "Starting emulator $AvdName"
    $arguments = @(
        "-avd", $AvdName,
        "-no-snapshot",
        "-no-boot-anim",
        "-no-audio",
        "-gpu", "swiftshader_indirect",
        "-no-window"
    )
    Start-Process -FilePath $emulatorPath -ArgumentList $arguments | Out-Null
    $script:launchedEmulator = $true
    Wait-ForDeviceBoot
}

function Configure-Device() {
    Write-Step "Configuring emulator"
    Invoke-Adb @("shell", "settings", "put", "system", "accelerometer_rotation", "0") | Out-Null
    Invoke-Adb @("shell", "settings", "put", "system", "user_rotation", "0") | Out-Null
    Invoke-Adb @("shell", "wm", "size", "1080x2340") | Out-Null
    Invoke-Adb @("shell", "settings", "put", "global", "window_animation_scale", "0") | Out-Null
    Invoke-Adb @("shell", "settings", "put", "global", "transition_animation_scale", "0") | Out-Null
    Invoke-Adb @("shell", "settings", "put", "global", "animator_duration_scale", "0") | Out-Null
    Invoke-Adb @("shell", "input", "keyevent", "82") | Out-Null
}

function Capture-Scene([string]$sceneName, [string]$outputFileName) {
    Write-Step "Capturing scene: $sceneName"
    Invoke-Adb @("shell", "am", "force-stop", $packageName) | Out-Null
    Invoke-Adb @("shell", "am", "start", "-W", "-n", $activityName, "--es", "screenshot_scene", $sceneName) | Out-Null
    Start-Sleep -Seconds 3

    $remotePath = "/sdcard/$outputFileName"
    $localPath = Join-Path $outputDir $outputFileName

    Invoke-Adb @("shell", "screencap", "-p", $remotePath) | Out-Null
    Invoke-Adb @("pull", $remotePath, $localPath) | Out-Null
    Invoke-Adb @("shell", "rm", $remotePath) | Out-Null
}

Write-Step "Checking toolchain"
Assert-PathExists $jdkPath "JDK"
Assert-PathExists $adbPath "adb"
Assert-PathExists $emulatorPath "Android emulator"
Assert-PathExists (Join-Path $projectRoot "gradlew.bat") "Gradle wrapper"

$env:JAVA_HOME = $jdkPath
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:Path = "$jdkPath\bin;$env:Path"

Set-Location $projectRoot

Write-Step "Building debug APK"
Invoke-Gradle @("assembleDebug")
Assert-PathExists $apkPath "Debug APK"

Ensure-Emulator
Configure-Device

Write-Step "Installing app"
Invoke-Adb @("install", "-r", $apkPath) | Out-Null

Write-Step "Saving screenshots"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
Capture-Scene -sceneName "overview" -outputFileName "live-overview.png"
Capture-Scene -sceneName "garmin" -outputFileName "live-garmin.png"

if ($launchedEmulator -and -not $KeepEmulatorRunning) {
    Write-Step "Stopping emulator"
    Invoke-Adb @("emu", "kill") | Out-Null
}

Write-Host ""
Write-Host "Live screenshots captured successfully." -ForegroundColor Green
Write-Host (Join-Path $outputDir "live-overview.png")
Write-Host (Join-Path $outputDir "live-garmin.png")
