# Absensi

Aplikasi absensi karyawan untuk perusahaan. **Satu HP di kantor** — karyawan absen
dengan tap nama di HP itu; data dipegang admin perusahaan.

- Absen masuk & pulang (tap nama), terlambat dihitung otomatis dari jam kerja
- Shift 1 08.00–15.00, shift 2 16.00–22.00 (toleransi terlambat 15 menit)
- Izin / sakit / cuti (dicatat admin)
- Rekap bulanan per karyawan + persentase kehadiran
- Menu Rekap, Karyawan, dan Lainnya dikunci PIN admin (4 digit)
- Desain: R1 Kaca Aurora (gelap + aurora + kaca)

## Teknologi

Kotlin + Jetpack Compose (Material 3 Expressive) + Room. minSdk 26.
Build lewat GitHub Actions (`workflow_dispatch`).

## Status

v1.0.0 — kerangka awal: layar Absen, Karyawan, Rekap, Lainnya + PIN admin.
Belum ada: ekspor Excel, backup cloud (Firebase), mode terang/gelap.

## Build lokal

Buka folder repo di Android Studio, jalankan dari terminal:

```
./gradlew assembleDebug
```

APK debug ada di `app/build/outputs/apk/debug/app-debug.apk`.
