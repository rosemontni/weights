# Release Metadata

## Release Name

Wyze Scale Bridge 1.0.0

## Version

- Version name: `1.0.0`
- Version code: `2`

## Summary

Wyze Scale Bridge is an Android app that imports Wyze Scale CSV exports and writes weight and body-fat measurements into Android Health Connect.

## Current Scope

- Supported input: Wyze Scale CSV export
- Supported output: Android Health Connect
- Target platform: Android 9 and newer

## Known Limitation

Garmin Connect automatic consumer import is not implemented because there is no stable supported path for this app to push those records directly into Garmin Connect.

## Release Artifacts

- Debug APK path:
  `C:\Users\xliup\OneDrive\Documents\codex\weights\app\build\outputs\apk\debug\app-debug.apk`

## Recommended Release Notes

Wyze Scale Bridge 1.0.0 is the first packaged Android release of the project. It imports Wyze Scale CSV exports, previews the measurements, and writes them into Android Health Connect with idempotent sync behavior so repeated imports do not keep duplicating entries.
