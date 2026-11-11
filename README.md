# Pemain Muzik

App Android untuk import senarai playlist Spotify dan main fail audio yang kau sendiri ada.

## Cara dapat APK (guna GitHub, tak perlu pasang apa-apa)

1. Daftar akaun di github.com (percuma), tekan **New repository**, beri nama `pemain-muzik`.
2. Tekan **uploading an existing file**. Extract zip ini, lepas itu seret SEMUA fail dan folder ke halaman itu, termasuk folder `.github`. Tekan **Commit changes**.
3. Buka tab **Actions**. Tunggu kerja "Bina APK" siap (lebih kurang 3-5 minit, tanda hijau).
4. Klik kerja itu, di bahagian **Artifacts** muat turun `pemain-muzik-apk`. Extract, dapat `app-debug.apk`.
5. Pindah ke telefon dan pasang. Android akan minta izinkan "pasang dari sumber tidak diketahui".

Kalau kerja Actions merah, klik dan salin mesej ralat, hantar pada Claude untuk dibetulkan.

## Sambung Spotify

1. Buka developer.spotify.com/dashboard, cipta app.
2. Tambah Redirect URI: `pemainmuzik://callback`
3. Salin Client ID, tampal dalam app bila tekan **Import Spotify**.

Had Spotify (sejak Februari 2026): pemilik app mesti Premium, app baru terhad 5 pengguna, dan hanya playlist yang kau miliki atau kolaborasi boleh dibaca. Untuk playlist lain, guna **Tampal senarai**.

## Guna

- **Tampal senarai**: satu baris satu lagu, format `Tajuk - Artis`, atau CSV dengan lajur "Track Name" dan "Artist Name(s)".
- **Tambah fail**: pilih fail audio. Fail yang namanya mengandungi tajuk lagu dalam senarai dipadankan sendiri.
- Ketik lagu yang belum ada fail untuk buka carian Jamendo (muzik berlesen bebas).
- Tekan lama pada lagu untuk buang.
