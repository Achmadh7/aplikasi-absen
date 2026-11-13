# Absensi

Employee attendance app for a company. **One shared phone at the office** — employees check in by tapping their name on that phone; the company admin holds the data.

- Check in & check out (tap a name); lateness is calculated automatically from working hours
- Shift 1 08:00–15:00, shift 2 16:00–22:00 (15-minute late tolerance)
- Leave / sick / day off (recorded by the admin)
- Monthly recap per employee + attendance percentage
- Design: R1 Kaca Aurora (dark + aurora + glass)

## Tech stack

Kotlin + Jetpack Compose (Material 3 Expressive) + Room. Package `id.absensi.app`, v1.0.0, minSdk 26.
R8 is enabled in release builds (minify + shrinkResources). Weekly Dependabot updates (Mondays 03:00 WIB, gradle + github-actions). Attendance-logic unit tests run in CI. Builds run on GitHub Actions (`workflow_dispatch`).

## Status

v1.0.0 — initial scaffold: Absen, Karyawan, Rekap, and Lainnya screens (all menus open, no PIN gate).
Not yet available: Excel export, cloud backup (Firebase), light/dark mode.

## In-app updates

The app checks the latest GitHub Release when it opens (silently — the Absen screen is never interrupted). If a newer version exists, the **Pembaruan Aplikasi** card in Lainnya shows it; **Periksa Pembaruan** checks manually. **Unduh & Pasang** downloads the APK and opens the installer automatically when the download finishes. The first time, Android asks you to allow "Install unknown apps" for Absensi — allow it once.

To publish an update: bump `versionName`/`versionCode` in `app/build.gradle.kts`, build the signed release APK (CI artifact `Absensi-Release`), then create a GitHub Release tagged `vX.Y.Z` (matching the new version) and attach the APK file to it. The app only sees releases that carry an `.apk` asset. No release exists yet, so the check finds nothing until the first one is published.

## Build

- **CI:** Actions → Build Absensi → Run workflow (branch `main`). Artifacts: `Absensi-Debug` and `Absensi-Release` (signed with the release keystore; the keystore itself is restored in CI from the `ABSENSI_KEYSTORE_BASE64` secret).
- **Local:** open the project in Android Studio and press Run. (This repo has no Gradle wrapper yet; local builds go through Android Studio.)
