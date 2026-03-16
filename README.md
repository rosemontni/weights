# Wyze Scale Bridge

Android starter app that imports a Wyze Scale CSV export and writes the measurements into Android Health Connect.

Detailed setup and usage guide:

- [Setup And Usage](C:\Users\xliup\OneDrive\Documents\codex\weights\docs\SETUP_AND_USAGE.md)
- [Quick Start](C:\Users\xliup\OneDrive\Documents\codex\weights\docs\QUICK_START.md)
- [Release Metadata](C:\Users\xliup\OneDrive\Documents\codex\weights\docs\RELEASE.md)

## What it does

- Imports Wyze Scale export files from the Wyze app.
- Parses weight and body-fat measurements.
- Writes those records into Health Connect on Android 9+.
- Shows the latest imported measurements in a simple Compose UI.

## What it does not do

It does not push data directly into Garmin Connect.

That limitation is deliberate:

- Wyze officially supports exporting scale data and syncing with Google Fit or Apple Health, but not Garmin Connect.
- Garmin's public Connect developer APIs are aimed at approved partners and business integrations, not normal consumer Android apps.
- Garmin support also says Garmin Connect does not forward data it received from one third-party service on to another third-party service, which blocks the usual bridge approach.

So this codebase gives you the safest supported foundation: Wyze export -> Android app -> Health Connect.

## Build

1. Open the folder in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on an Android 9+ device.
4. Import a CSV exported from Wyze Scale.
5. Grant Health Connect permission when prompted.
6. Tap `Write to Health Connect`.

## Release

- Current release name: `Wyze Scale Bridge 1.0.0`
- Changelog: [CHANGELOG.md](C:\Users\xliup\OneDrive\Documents\codex\weights\CHANGELOG.md)
- Release metadata: [RELEASE.md](C:\Users\xliup\OneDrive\Documents\codex\weights\docs\RELEASE.md)

## Local helper scripts

- Full test runner:
  `.\scripts\run_full_test.ps1`
- Phone install runner:
  `.\scripts\install_debug_apk.ps1`

## Realistic next steps if you want Garmin anyway

1. Try an unofficial Garmin Connect private API login flow.
2. Build a server-side integration if you can get access to Garmin's partner program.
3. Accept a semi-manual workflow where this app writes to Health Connect and Garmin remains separate.

## Sources used for scope decisions

- Wyze says scale data can be exported from the app: [Wyze export article](https://support.wyze.com/hc/en-us/articles/360042271292-Can-I-export-my-Wyze-Scale-data)
- Wyze says scale data can sync with Google Fit: [Wyze Google Fit article](https://support.wyze.com/hc/en-us/articles/360039174572-How-do-I-sync-the-Wyze-app-with-Google-Fit)
- Android Health Connect getting started: [Android Developers](https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started)
- Health Connect data types and permissions: [Android Developers](https://developer.android.com/health-and-fitness/health-connect/data-types)
- Garmin Connect Developer Program overview and partner-style access: [Garmin Training API](https://developer.garmin.com/gc-developer-program/training-api/)
- Garmin support on third-party forwarding limits: [Garmin support article](https://support.garmin.com/en-IN/?faq=glJyCFNknq1gbFIL4MBGn6)
