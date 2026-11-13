# Absensi

Employee attendance app for a company. **One shared phone at the office** — employees check in by tapping their name on that phone; the company admin holds the data.

- Check in & check out (tap a name); lateness is calculated automatically from working hours
- Shift 1 08:00–15:00, shift 2 16:00–22:00 (15-minute late tolerance)
- Leave / sick / day off (recorded by the admin)
- Monthly recap per employee + attendance percentage
- Rekap, Karyawan, and Lainnya menus are locked behind an admin PIN (4 digits)
- Design: R1 Kaca Aurora (dark + aurora + glass)

## Tech stack

Kotlin + Jetpack Compose (Material 3 Expressive) + Room. Package `id.absensi.app`, v1.0.0, minSdk 26.
R8 is enabled in release builds (minify + shrinkResources). Weekly Dependabot updates (Mondays 03:00 WIB, gradle + github-actions). Attendance-logic unit tests run in CI. Builds run on GitHub Actions (`workflow_dispatch`).

## Status

v1.0.0 — initial scaffold: Absen, Karyawan, Rekap, and Lainnya screens + admin PIN.
Not yet available: Excel export, cloud backup (Firebase), light/dark mode.

## Build

- **CI:** Actions → Build Absensi → Run workflow (branch `main`). Artifacts: `Absensi-Debug` and `Absensi-Release-Unsigned` (the release APK is unsigned for now — no keystore yet).
- **Local:** open the project in Android Studio and press Run. (This repo has no Gradle wrapper yet; local builds go through Android Studio.)
