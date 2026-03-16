# Wyze Scale Bridge Setup And Usage

This document explains how to set up, test, build, install, and use the Android app from this repository.

## 1. What This App Does

Wyze Scale Bridge is an Android app that:

- imports a Wyze Scale CSV export
- parses weight and body-fat data
- writes those records into Android Health Connect

Important limitation:

- it does not send data directly into Garmin Connect
- Garmin Connect automatic consumer import from this kind of third-party Android app is not supported in a normal stable way

## 2. Project Location

Repository root:

`C:\Users\xliup\OneDrive\Documents\codex\weights`

Main installable APK output:

`C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\outputs\apk\debug\app-debug.apk`

## 3. Prerequisites

Before using the project, make sure these are installed:

1. Java 17
2. Android Studio
3. Android SDK
4. Gradle wrapper files in this repo

This project is already configured for:

- Java: Eclipse Temurin 17
- Android Studio: installed
- Android SDK: default location
- Gradle wrapper: included in the repo

Default Android SDK path:

`C:\Users\xliup\AppData\Local\Android\Sdk`

## 4. Open The Project In Android Studio

1. Start Android Studio.
2. Choose `Open`.
3. Open this folder:
   `C:\Users\xliup\OneDrive\Documents\codex\weights`
4. Wait for Gradle sync to complete.
5. If Android Studio asks to trust the project or install missing components, approve them.

## 5. Command Line Build And Test

Open PowerShell and run:

```powershell
cd C:\Users\xliup\OneDrive\Documents\codex\weights
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

What these commands do:

- `test` runs the unit tests
- `assembleDebug` builds the debug APK

## 6. One-Command Full Test

This repo includes a full test script:

`C:\Users\xliup\OneDrive\Documents\codex\weights\scripts\run_full_test.ps1`

Run it with:

```powershell
cd C:\Users\xliup\OneDrive\Documents\codex\weights
.\scripts\run_full_test.ps1
```

What it checks:

1. Java is installed
2. Android SDK is installed
3. Gradle wrapper exists
4. unit tests pass
5. debug APK builds successfully
6. expected reports and APK output exist

Expected outputs:

- Debug APK:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\outputs\apk\debug\app-debug.apk`
- Debug test report:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\reports\tests\testDebugUnitTest\index.html`
- Release test report:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\reports\tests\testReleaseUnitTest\index.html`

## 7. Build The APK

If you only want the installable app package:

```powershell
cd C:\Users\xliup\OneDrive\Documents\codex\weights
.\gradlew.bat assembleDebug
```

APK output:

`C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\outputs\apk\debug\app-debug.apk`

## 8. Install The APK On Your Phone Manually

Use these steps if USB debugging is not working.

1. Build the APK.
2. Locate the file:
   `C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\outputs\apk\debug\app-debug.apk`
3. Copy that file to your Android phone.
4. On the phone, open the APK file.
5. If Android asks for permission to install from this source, allow it.
6. Complete the installation.

## 9. Install The APK With USB And adb

This repo includes a helper script:

`C:\Users\xliup\OneDrive\Documents\codex\weights\scripts\install_debug_apk.ps1`

### 9.1 Prepare The Phone

1. Open the phone's `Settings`.
2. Enable `Developer options`.
3. Enable `USB debugging`.
4. Connect the phone to the PC with a USB cable that supports data.
5. Set USB mode to `File transfer` if needed.
6. Accept the `Allow USB debugging` prompt on the phone.

### 9.2 Install From PowerShell

```powershell
cd C:\Users\xliup\OneDrive\Documents\codex\weights
.\scripts\install_debug_apk.ps1
```

To rebuild before installing:

```powershell
.\scripts\install_debug_apk.ps1 -Rebuild
```

If no device is detected, the script stops with a clear error.

## 10. First-Time App Usage On The Phone

After the app is installed:

1. Open `Wyze Scale Bridge`.
2. Tap `Import Wyze CSV`.
3. Select the CSV file exported from Wyze.
4. Review the imported measurements shown in the app.
5. Tap `Write to Health Connect`.
6. If prompted, grant Health Connect permissions.
7. Wait for the success message.

## 11. Export Data From Wyze

General workflow:

1. Open the Wyze app.
2. Find the Wyze Scale data export option.
3. Export your scale data as CSV.
4. Move that CSV file to your Android phone if needed.
5. Import it using Wyze Scale Bridge.

## 12. Health Connect Setup

If Health Connect is not ready:

1. Install or update Health Connect on the phone.
2. Open Wyze Scale Bridge.
3. Tap `Open Health Connect setup` if shown.
4. Grant the app permission to write:
   - weight
   - body fat

## 13. How Sync Works

The app now uses stable record IDs for imported measurements.

That means:

- re-syncing the same imported data should not keep creating duplicate records
- Health Connect writes are tied to deterministic IDs based on the imported measurement

## 14. Test Reports

After running tests, reports are available here:

- Debug unit tests:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\reports\tests\testDebugUnitTest\index.html`
- Release unit tests:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\reports\tests\testReleaseUnitTest\index.html`

Open those HTML files in a browser to inspect detailed results.

## 15. Troubleshooting

### Java not found

Open a new terminal and run:

```powershell
java -version
```

If it fails, confirm Java 17 is installed and `JAVA_HOME` is set.

### Android SDK not found

Check this folder exists:

`C:\Users\xliup\AppData\Local\Android\Sdk`

If it does not, open Android Studio and install the default SDK components.

### Build fails because of stale files

Run:

```powershell
cd C:\Users\xliup\OneDrive\Documents\codex\weights
.\scripts\run_full_test.ps1
```

That script clears stale test output before running.

### Phone not detected by adb

Check these:

1. phone is unlocked
2. USB debugging is enabled
3. USB mode is `File transfer`
4. the phone accepted the PC authorization prompt
5. the cable supports data, not just charging

You can also test manually:

```powershell
C:\Users\xliup\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
```

### APK installs but app cannot sync

Check:

1. Health Connect is installed
2. Health Connect permissions were granted
3. the imported CSV is a real Wyze export

## 16. Key Project Files

- App entry point:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\src\main\java\com\codex\wyzescalebridge\MainActivity.kt`
- CSV parser:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\src\main\java\com\codex\wyzescalebridge\data\WyzeCsvParser.kt`
- Health Connect writer:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\src\main\java\com\codex\wyzescalebridge\data\HealthConnectWriter.kt`
- Parser tests:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\src\test\java\com\codex\wyzescalebridge\data\WyzeCsvParserTest.kt`
- Full test script:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\scripts\run_full_test.ps1`
- Phone install script:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\scripts\install_debug_apk.ps1`

## 17. Recommended Daily Workflow

1. Edit code in Android Studio.
2. Run:
   `.\scripts\run_full_test.ps1`
3. Build or rebuild the APK if needed.
4. Install with:
   `.\scripts\install_debug_apk.ps1 -Rebuild`
5. Open the app on the phone and test with a Wyze CSV export.
