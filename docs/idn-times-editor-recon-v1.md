# ArticlePilot — Task 10
## Controlled Authenticated IDN Times Editor Reconnaissance v1

**Tanggal observasi:** 16 Agustus 2026 GMT+7
**Connector:** Playwright; konektor Browser Saya tidak digunakan.
**Target:** [`https://community.idntimes.com/dashboard/create-article`](https://community.idntimes.com/dashboard/create-article)
**Scope:** Reconnaissance pasif dan evidence capture. Tidak ada pengisian kredensial, upload, penyimpanan draft, submit, publikasi, atau browser automation produksi.

## 1. Ringkasan hasil

Task 10 **tidak mencapai authenticated editor**. Playwright berhasil membuka target URL dan halaman memiliki title `Create Article Community | IDN Times`, tetapi accessibility snapshot yang dapat diamati hanya berisi public IDN Times shell: navigasi utama, tautan kategori publik, tombol pencarian/menu, dan tautan `Join Community`. Tidak ada bukti independen bahwa contributor session telah terautentikasi.

Upaya login manual tidak dapat dilakukan karena sesi Playwright yang berjalan di lingkungan terisolasi tidak menyediakan jendela takeover interaktif yang dapat dilihat atau dikendalikan oleh pemilik akun. Sesuai batas keamanan proyek, kredensial tidak diminta melalui chat, tidak diotomatisasi, dan tidak disimpan. Konektor Browser Saya juga tidak digunakan.

> **Kesimpulan autentikasi:** `PUBLIC_PAGE`. `AUTHENTICATED_EDITOR` tidak terobservasi.

Karena editor authenticated tidak tersedia, reconnaissance berhenti pada pemeriksaan awal. Semua kontrol editor, perilaku block, media, draft, dan submission dicatat sebagai **UNKNOWN**, bukan diperkirakan dari title, route, HTTP status, dokumentasi historis, atau public shell.

## 1a. Percobaan file sesi yang disediakan pengguna

Atas permintaan pemilik proyek, `set_cookies.txt` diperiksa secara terbatas dan hanya digunakan secara transient pada konteks Playwright. File tersebut berisi assignment `document.cookie` untuk tiga nama sesi (`id_token`, `access_token`, dan `refresh_token`) serta satu baris return. Nilai cookie, token, identitas akun, dan isi payload tidak dimasukkan ke laporan atau source code. File asli tidak disalin atau di-commit.

Assignment cookie diterapkan pada konteks browser sebelum target route dibuka. Hasil pengamatan yang disanitasi tetap tidak memenuhi authenticated-editor requirement: target route kembali ke public Community shell, URL akhir menjadi `https://community.idntimes.com/`, title menjadi `IDN Times Community - Tulis Artikelmu & Jadilah Penulis Populer | IDN Times`, dan snapshot menunjukkan public navigation serta kontrol `login`. Tidak ada `contenteditable`, textbox editor, file input, section control, atau image control yang terlihat.

Dengan demikian, percobaan file sesi **tidak membuktikan autentikasi**. Kemungkinan penyebab seperti token kedaluwarsa, atribut cookie yang tidak sesuai, domain/path mismatch, atau bootstrap session yang berbeda tetap **UNKNOWN**. ArticlePilot tidak boleh mengubah mekanisme ini menjadi credential/session injection produksi tanpa kontrak bootstrap yang terverifikasi.

## 2. Evidence chain

| Tahap | Hasil | Klasifikasi | Bukti aman |
|---|---|---|---|
| Membuka target route | Berhasil | OBSERVED | URL target terbuka di Playwright. |
| URL terakhir | `https://community.idntimes.com/dashboard/create-article` | OBSERVED | URL halaman aktif. |
| Page title | `Create Article Community \| IDN Times` | OBSERVED | Title halaman. |
| Public shell | Tampil | OBSERVED | Navigasi publik, kontrol `login`, dan konten Community terlihat pada accessibility snapshot. |
| Login marker | Tidak dicatat sebagai authenticated evidence | UNKNOWN | Tidak ada authenticated-only marker yang dapat diverifikasi. |
| Editor DOM | Tidak ditemukan | UNKNOWN | Tidak ada title input, body editor, section control, atau upload control yang terobservasi. |
| Manual takeover | Tidak tersedia pada sesi ini | BLOCKED | Tidak ada browser window interaktif yang dapat diambil alih pengguna. |
| Artikel mutation | Tidak dilakukan | NOT PERFORMED | Tidak ada click editorial, form fill, upload, save, atau submit. |

Title, route, keberadaan cookie assignment, dan keberhasilan navigasi **tidak** diperlakukan sebagai bukti autentikasi. Kesimpulan ini juga konsisten dengan reconnaissance sebelumnya yang membedakan page shell publik dari authenticated editor pada [`docs/idn-times-authenticated-reconnaissance.md`](idn-times-authenticated-reconnaissance.md).

## 3. Metode dan security boundary

Reconnaissance menggunakan browser Playwright khusus pada target domain. Tidak ada username, password, OTP, MFA code, cookie, token, authorization header, browser profile, WebView database, atau cache kredensial yang diminta, dicetak, disalin ke source code, atau dimasukkan ke repository.

Sesi berhenti sebelum tindakan yang dapat mengubah data. CAPTCHA, anti-bot challenge, dan mekanisme keamanan autentikasi tidak dilewati. Karena takeover pengguna tidak tersedia, tidak ada dasar yang aman untuk melanjutkan ke halaman contributor melalui login manual.

## 4. Temuan editor

| Area yang diwajibkan Task 10 | Hasil | Klasifikasi |
|---|---|---|
| Tipe editor: form, textarea, contenteditable, block editor, iframe, atau SPA | Tidak dapat diamati | UNKNOWN |
| Title | Tidak ada kontrol editor yang terlihat | UNKNOWN |
| Excerpt/description | Tidak ada kontrol editor yang terlihat | UNKNOWN |
| Category | Tidak ada kontrol editor yang terlihat | UNKNOWN |
| Tags | Tidak ada kontrol editor yang terlihat | UNKNOWN |
| Metadata lain | Tidak ada kontrol editor yang terlihat | UNKNOWN |
| Cover upload | Tidak ada trigger atau file input yang terlihat | UNKNOWN |
| Section creation | Tidak ada kontrol section yang terlihat | UNKNOWN |
| Paragraph/body | Tidak ada body editor yang terlihat | UNKNOWN |
| Heading/subheading | Tidak ada body editor yang terlihat | UNKNOWN |
| Formatting, link, atau embed | Tidak dapat diuji | UNKNOWN |
| Inline image upload | Tidak ada trigger atau file input yang terlihat | UNKNOWN |
| Caption/source/source URL/credit | Tidak ada image block yang dapat dipilih | UNKNOWN |
| Save/autosave/draft indicator | Tidak ada draft yang dibuat | UNKNOWN |
| Preview | Tidak ada kontrol preview yang dapat diamati | UNKNOWN |
| Submit/confirmation/validation | Tidak ada kontrol submit yang dapat diamati; tidak dilakukan | UNKNOWN |

## 5. Uji representasi ordering gambar

Uji synthetic `TEXT → IMAGE → TEXT → IMAGE → TEXT` **tidak dapat dijalankan** karena authenticated editor tidak pernah terverifikasi. Tidak ada bukti mengenai apakah gambar merupakan block terpisah, berada pada cursor aktif, dapat dipindahkan, dapat dihapus, atau mempertahankan urutan setelah save/reload.

**Jawaban deterministik:** `UNKNOWN`. ArticlePilot tidak boleh menyimpulkan bahwa ordered `ArticleBlock` dapat dipetakan ke editor IDN Times sebelum authenticated interaction dan read-back evidence tersedia.

## 6. Selector reconnaissance

Tidak ada selector editor yang dibuat atau dipromosikan ke modul `automation:selectors`. Elemen yang terlihat hanya merupakan evidence public-shell dan bukan kontrak automation produksi.

| Kandidat evidence | Klasifikasi | Batas penggunaan |
|---|---|---|
| Accessible navigation `IDN Times main navigation` | POTENTIALLY-STABLE | Public-shell observation saja; bukan authenticated marker. |
| Tautan `Join Community` | POTENTIALLY-STABLE | Public navigation saja; bukan editor selector. |
| Tombol `search` dan `burger` | UNKNOWN | UI publik; stabilitas dan relevansi editor tidak diketahui. |
| Route `/dashboard/create-article` | UNKNOWN | Route dapat dibuka, tetapi permission dan editor content tidak terbukti. |
| Page title `Create Article Community \| IDN Times` | UNKNOWN | Title tidak cukup untuk menyatakan authenticated state. |

Coordinate selector, generated class selector, random React identifier, dan selector produksi tidak digunakan.

## 7. Sanitized fixture

Sanitized authenticated HTML fixture **tidak dibuat** karena tidak ada authenticated editor structure yang aman untuk direpresentasikan. Membuat fixture editor dari dugaan akan menjadi fabricated evidence dan akan merusak pengujian offline.

## 8. Dampak terhadap arsitektur ArticlePilot

Tidak ada perubahan pada model artikel, parser, validator, Media Core, browser session contract, atau automation engine. Keputusan ini menjaga batas yang telah ditetapkan: domain tidak bergantung pada DOM, media tidak bergantung pada browser, dan automation tidak berjalan tanpa success evidence.

Boundary manual takeover tetap valid secara arsitektural, tetapi Task 10 ini menunjukkan bahwa mekanisme takeover interaktif harus tersedia pada lingkungan eksekusi yang akan digunakan untuk reconnaissance berikutnya. Keterbatasan tersebut bukan alasan untuk memasukkan credential injection atau session material ke aplikasi.

## 9. Limitasi dan unknowns

Temuan ini tidak menentukan editor web atau editor aplikasi yang digunakan IDN Times, struktur DOM authenticated, metadata requirements, cover constraints, body/block semantics, image metadata, draft persistence, preview, submission flow, moderation UI, rate limits, session lifetime, maupun selector stability. Dokumentasi resmi dan reconnaissance lama tetap dipisahkan dari current observation; tidak ada klaim historical yang dipromosikan menjadi evidence runtime.

Karena authenticated editor tidak tercapai, status implementasi berikut tetap **NOT ENABLED**: `OPEN_EDITOR`, `FILL_METADATA`, `UPLOAD_COVER`, `CREATE_SECTION`, `WRITE_SECTION`, `UPLOAD_IMAGE`, `FILL_IMAGE_METADATA`, `SAVE_DRAFT`, `SUBMIT`, dan `VERIFY`.

## 10. Verification status

Deterministic pure Kotlin module tests passed with Gradle using a single worker. The required aggregate command `./gradlew test lint assembleDebug --no-daemon --max-workers=1` could not complete because the sandbox has no Android SDK configured: neither `ANDROID_HOME` nor `ANDROID_SDK_ROOT` is set, and no standard SDK directory was present. This is an environment limitation, not a fabricated Android build result. No live IDN Times test is part of the ordinary test suite.

## 11. Recommended Task 11

Task berikutnya sebaiknya menyediakan sesi browser yang benar-benar dapat dikendalikan pengguna atau lingkungan perangkat/emulator ArticlePilot yang menampilkan WebView kepada pengguna. Login tetap harus dilakukan manual oleh pemilik akun dummy, tanpa mengirim kredensial ke ArticlePilot dan tanpa bypass CAPTCHA, MFA, OTP, atau anti-bot challenge. Karena file sesi yang dicoba berisi token yang tidak menghasilkan editor terautentikasi, pemilik akun sebaiknya mempertimbangkan invalidasi/rotasi sesi tersebut sebelum dipakai kembali di lingkungan lain.

Acceptance checkpoint pertama Task 11 harus berupa snapshot authenticated editor yang disanitasi dan memiliki beberapa bukti independen: route contributor, authenticated-only structure, title control, body editor, section/image controls, serta read-back evidence. Setelah snapshot tersebut direview, barulah kandidat `PublishingProfile`, fixture HTML lokal, dan selector research catalog dapat dirancang. Publishing automation dan submission tetap harus ditunda.

## References

[1]: https://community.idntimes.com/dashboard/create-article "IDN Times Community create article route"

[2]: ./idn-times-authenticated-reconnaissance.md "ArticlePilot Task 08 authenticated reconnaissance report"

[3]: ./idn-times-publishing-research.md "ArticlePilot Task 07 IDN Times publishing research"
