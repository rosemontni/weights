# Wyze Scale Bridge Quick Start

This is the shortest path to build, install, and use the app.

## 1. Open PowerShell In The Project

```powershell
cd C:\Users\xliup\OneDrive\Documents\codex\weights
```

## 2. Run The Full Check

```powershell
.\scripts\run_full_test.ps1
```

This verifies:

- Java
- Android SDK
- Gradle build
- unit tests
- APK output

## 3. Build The App

```powershell
.\gradlew.bat assembleDebug
```

APK location:

`C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\outputs\apk\debug\app-debug.apk`

## 4. Install On Your Phone

### Option A: Manual install

1. Copy `app-debug.apk` to your phone.
2. Open it on the phone.
3. Allow install from this source if Android asks.
4. Install the app.

### Option B: USB install

1. Enable `Developer options` on the phone.
2. Enable `USB debugging`.
3. Connect the phone with a data-capable USB cable.
4. Accept the `Allow USB debugging` prompt.
5. Run:

```powershell
.\scripts\install_debug_apk.ps1 -Rebuild
```

## 5. Use The App

1. Open `Wyze Scale Bridge` on the phone.
2. Tap `Import Wyze CSV`.
3. Choose your Wyze Scale CSV export.
4. Tap `Write to Health Connect`.
5. Grant Health Connect permission if prompted.

## 6. If Something Fails

Use the full guide:

`C:\Users\xliup\OneDrive\Documents\codex\weights\docs\SETUP_AND_USAGE.md`

## 7. Most Useful Commands

```powershell
cd C:\Users\xliup\OneDrive\Documents\codex\weights
.\scripts\run_full_test.ps1
.\gradlew.bat assembleDebug
.\scripts\install_debug_apk.ps1 -Rebuild
```
