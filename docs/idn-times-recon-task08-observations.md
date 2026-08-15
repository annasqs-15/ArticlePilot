# Task 08 — Sanitized Playwright Reconnaissance Observations

**Tanggal observasi:** 16 Agustus 2026 (sesi sandbox; timestamp runtime Playwright 15 Agustus 2026 UTC)

Dokumen ini hanya berisi evidence yang disanitasi. Nilai cookie, nama cookie, credential, token, request body, response body privat, dan data identitas akun tidak disimpan.

## Runtime dan batasan akses

Connector yang digunakan adalah **Playwright**. Connector **My Browser tidak digunakan**. Browser Firefox Playwright berhasil diinstal setelah runtime melaporkan executable belum tersedia.

File sesi ditemukan pada project runtime melalui file-input browser. Fingerprint format menunjukkan empat baris, tiga baris berupa assignment `document.cookie`, satu baris lain tidak diproses. File bukan JSON dan bukan Netscape cookie-jar tab-separated. Nilai setiap assignment hanya digunakan di dalam konteks browser dan tidak dikembalikan.

## Public entry point

Navigasi ke `https://community.idntimes.com/` berhasil. Judul halaman yang terlihat adalah **IDN Times Community - Tulis Artikelmu & Jadilah Penulis Populer | IDN Times**. Accessibility snapshot menampilkan tombol login dengan accessible name `login` dan label **Masuk/Daftar**.

## Session application attempt

Tiga assignment cookie diterapkan hanya pada konteks Playwright dan hanya untuk domain IDN yang relevan. Tidak ada login form yang diisi, tidak ada credential yang diminta, dan tidak ada aksi publikasi yang dilakukan.

Navigasi ke `https://community.idntimes.com/dashboard/` menghasilkan URL normalisasi `https://community.idntimes.com/dashboard` dan title **Manage Article Community | IDN Times** pada saat initial document load. Setelah halaman settle, accessibility snapshot kembali menampilkan shell Community publik dengan tombol **Masuk/Daftar**. Dengan demikian, title dashboard tidak cukup untuk menyimpulkan authenticated session.

## Network evidence

Request non-static yang terlihat selama navigasi mencakup:

| Endpoint pattern | Observed result | Interpretation |
|---|---:|---|
| `/api/community/layout?publisher=idntimes&platform=desktop&slug=dashboard-notifications` | `401` pada beberapa observasi | Evidence kuat bahwa layout API authenticated belum menerima session yang valid. |
| `/dashboard/manage-article?_rsc=...` | `200` pada beberapa observasi | RSC/page shell dapat diambil, tetapi status `200` tidak membuktikan authentication atau permission. |
| `/dashboard/events?_rsc=...` | `200` pada beberapa observasi | Page shell dapat diambil; isi authenticated tidak disimpulkan. |
| `/dashboard/guidelines?_rsc=...` | `200` pada beberapa observasi | Page shell dapat diambil; isi authenticated tidak disimpulkan. |
| `/api/auth/logout` | `200` dan `NS_BINDING_ABORTED` terlihat pada trace | Terlihat dalam network trace; penyebab dan pemicunya tidak ditentukan. Tidak ada tombol logout yang diklik secara manual. |

Tidak ada CAPTCHA, MFA, OTP, atau suspicious-login challenge yang terlihat pada probe terbatas ini.

## Reconnaissance classification

| Claim | Confidence | Basis |
|---|---|---|
| Playwright dapat membuka public Community entry point | `CONFIRMED` | Navigasi dan accessibility snapshot. |
| Public page menampilkan `Masuk/Daftar` | `CONFIRMED` | Accessibility snapshot. |
| File sesi memiliki tiga assignment `document.cookie` yang dapat diproses | `CONFIRMED` | File-input runtime dan fingerprint format. |
| Assignment cookie yang tersedia menghasilkan authenticated contributor session | `NOT CONFIRMED` | Dashboard title sempat terlihat, tetapi page settle kembali ke public shell dan layout API mengembalikan `401`. |
| Dashboard/page shell memiliki route `manage-article`, `events`, dan `guidelines` | `CONFIRMED` | Network request listing. |
| Isi editor, field controls, upload dialog, selector, atau workflow submit dapat diamati | `UNKNOWN` | Authenticated state belum terverifikasi. |

## Safety boundary

Reconnaissance berhenti pada observasi pasif. Tidak ada click, form fill, upload artikel, submit, perubahan draft, logout manual, credential entry, CAPTCHA interaction, anti-bot bypass, atau extraction of cookie values. Evidence ini tidak boleh dipakai untuk membangun selector production sebelum authenticated session yang valid dan user-controlled dapat diverifikasi.
