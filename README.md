# Galeriku

Aplikasi galeri Android buatan sendiri (Kotlin + Jetpack Compose). Membaca foto dan video langsung dari HP, tanpa internet.

## Fitur

- Grid foto + video (durasi tampil), urut tanggal, jumlah kolom 2–5
- Viewer layar penuh: geser antar foto, cubit/ketuk dua kali untuk zoom, bagikan, info file
- Pilih banyak item (tekan lama)
- Favorit
- Album buatan sendiri (buat, ganti nama, hapus, tambah/keluarkan foto) + folder bawaan perangkat
- Sampah: foto yang dihapus disembunyikan dan bisa dipulihkan; tombol "Urungkan" setelah menghapus
- Setelan hapus: **pindahkan ke sampah** atau **hapus permanen**
- Tawaran hapus permanen otomatis untuk sampah yang sudah lewat 7 / 30 / 60 hari (atau manual)
- Tema terang / gelap / ikut sistem

## Cara mendapatkan APK (tanpa Android Studio)

1. Buat repositori baru di GitHub (boleh private).
2. Upload **seluruh isi** folder proyek ini ke repositori itu (termasuk folder `.github`).
3. Buka tab **Actions** → workflow **Build APK** akan jalan otomatis (sekitar 5–10 menit). Kalau tidak jalan, pilih workflow-nya lalu **Run workflow**.
4. Setelah hijau, buka run tersebut → bagian **Artifacts** → unduh **Galeriku-APK** (zip berisi `app-release.apk`).
5. Ekstrak zip, kirim `app-release.apk` ke HP, lalu ketuk untuk install. Android akan meminta izin "Install dari sumber tidak dikenal" untuk aplikasi yang kamu pakai membukanya (File Manager / Chrome).

## Build sendiri di komputer

Buka folder ini di Android Studio, tunggu sync, lalu **Build > Build APK(s)**. Atau lewat terminal (butuh JDK 17 + Android SDK):

```
gradle assembleDebug
```

Hasil: `app/build/outputs/apk/debug/app-release.apk`

## Catatan

- Butuh Android 11 (API 30) ke atas.
- APK yang dihasilkan adalah build debug (ditandatangani kunci debug), cukup untuk dipasang di HP sendiri.
- "Sampah" bersifat di dalam aplikasi: file asli tetap ada di HP sampai dihapus permanen, jadi aplikasi galeri lain masih bisa menampilkannya.
- Penghapusan permanen selalu memunculkan dialog konfirmasi dari Android. Itu aturan sistem, tidak bisa dilewati.
- Favorit, album, dan sampah disimpan lokal di aplikasi. Kalau aplikasi di-uninstall, data itu hilang (foto aslinya tidak).
