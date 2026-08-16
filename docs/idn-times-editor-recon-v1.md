# Controlled Authenticated IDN Times Editor Reconnaissance v1

**Tanggal observasi:** 16 Agustus 2026 GMT+7
**Target:** [`https://community.idntimes.com/dashboard/create-article`](https://community.idntimes.com/dashboard/create-article)
**Metode autentikasi:** Cookie sesi yang diberikan pemilik proyek, diterapkan secara transient pada konteks Playwright yang terisolasi. Tidak ada password, OTP, MFA, CAPTCHA, atau credential entry yang dilakukan.
**Scope:** Evidence capture dan observasi read-only. Tidak ada pengisian artikel, upload file, penyimpanan draft, logout, submit, atau publikasi.

## 1. Ringkasan hasil

Task 10 berhasil mencapai halaman editor contributor yang terautentikasi setelah cookie sesi pemilik proyek diterapkan secara transient. Evidence independen yang teramati mencakup kontrol akun, tautan `Create Article`, tautan `Community Dashboard`, dan kontrol `logout`. Target route tetap berada pada `/dashboard/create-article` dengan title `Create Article Community | IDN Times`.

> **Kesimpulan autentikasi:** `AUTHENTICATED_EDITOR` terobservasi.

Editor authenticated yang teramati adalah editor rich-text **TipTap/ProseMirror** berbasis `div[contenteditable=true]` dengan `role="textbox"`. Halaman juga menyediakan metadata publisher/channel, event code, article title, kontrol cover/main image, main description, toolbar formatting, dan tombol `Add Content`.

Task ini tidak melakukan mutasi. Karena tidak ada teks atau gambar yang dimasukkan, kemampuan mereproduksi urutan `TEXT → IMAGE → TEXT → IMAGE → TEXT`, perilaku inline-image setelah upload, persistence draft setelah save/reload, dan submission tetap **UNKNOWN**. Observasi editor tidak boleh disamakan dengan bukti bahwa publishing automation sudah berfungsi.

## 2. Evidence chain

| Tahap | Hasil | Klasifikasi | Evidence aman |
| --- | --- | --- | --- |
| Membuka target route | Berhasil | OBSERVED | Target route dapat dibuka dalam konteks browser. |
| URL terakhir | `/dashboard/create-article` | OBSERVED | Route editor tetap aktif setelah cookie transient. |
| Page title | `Create Article Community \| IDN Times` | OBSERVED | Title halaman editor. |
| Authenticated account shell | Terlihat | OBSERVED | Account control, `My Account`, `Create Article`, `Community Dashboard`, dan `logout` terlihat. Nilai identitas tidak disalin ke laporan. |
| Editor main area | Terlihat | OBSERVED | Main heading `Create Article`, field metadata, cover control, rich-text editor, toolbar, dan `Add Content`. |
| Authenticated-only marker | Terlihat | OBSERVED | Account/logout controls dan contributor dashboard controls muncul, berbeda dari public shell yang sebelumnya menampilkan `login`. |
| Editor DOM | Terlihat | OBSERVED | `div[contenteditable=true]`, `role=textbox`, class `tiptap ProseMirror`, dan paragraph child. |
| Article mutation | Tidak dilakukan | NOT PERFORMED | Tidak ada fill, upload, save, reset, logout, atau submit. |
| Manual takeover | Tidak diperlukan untuk percobaan ini | NOT USED | Cookie transient berhasil membawa browser ke editor; tidak ada login manual. |

HTTP status, route, page title, dan keberhasilan navigasi tidak digunakan sendirian sebagai bukti autentikasi. Klasifikasi `AUTHENTICATED_EDITOR` didasarkan pada kombinasi account shell authenticated dan struktur editor contributor yang dapat diamati.

## 3. Security boundary

Cookie attachment digunakan hanya sebagai input runtime transient untuk konteks browser. Nilai cookie, token, authorization header, private account URL, private article text, dan data session tidak dimasukkan ke source code, fixture, dokumentasi, commit, atau output laporan. Skrip transient yang dibutuhkan untuk transformasi dan injeksi telah dihapus setelah digunakan.

Reconnaissance tidak mengotomatisasi username/password, OTP, MFA, CAPTCHA, atau anti-bot challenge. Tidak ada request body, response body, header, atau token yang dibaca. Satu daftar request API hanya diperiksa pada tingkat method, path, dan status untuk evidence read-only; request tersebut tidak diperlakukan sebagai bukti persistence atau article mutation.

## 4. Temuan editor

### 4.1 Tipe editor

Editor body/main description teridentifikasi sebagai TipTap/ProseMirror. DOM yang terlihat adalah `div[contenteditable=true]` dengan `role="textbox"`, class `tiptap ProseMirror`, `tabindex="0"`, dan satu paragraph kosong. Container parent memiliki research-only attribute `data-cy="editor-content"`. Label `Main Description*` berada pada container yang sama dan counter `Word: 0` serta `Character: 0` terlihat.

Toolbar menyediakan tombol research-only untuk undo, redo, bold, underline, italic, strikethrough, blockquote, bullet list, numbered list, dan alignment kiri/tengah/kanan. ID yang diamati termasuk `editor-undo`, `editor-redo`, `editor-bold`, `editor-underline`, `editor-italic`, `editor-strikethrough`, `editor-blockqotes`, `editor-bullet-list`, `editor-bullet-number`, `editor-align-left`, `editor-align-center`, dan `editor-align-right`.

### 4.2 Metadata controls

| Control | Observasi | Status |
| --- | --- | --- |
| Publisher | Combobox/select berlabel `Publisher*`, name `publisher` pada DOM. | OBSERVED |
| Channel/category | Combobox/select berlabel `Channel*`; DOM juga memiliki select `category_id`. | OBSERVED |
| Event Code | Input placeholder `Insert Code` dan tombol `Use Code`. `Use Code` terlihat disabled pada keadaan awal. | OBSERVED |
| Article title | Text input name `details.0.value`, placeholder `Insert Title`, label `Article Title*`, counter `0/70`. | OBSERVED |
| Main description | Rich-text TipTap/ProseMirror dengan label `Main Description*`. | OBSERVED |
| Tags/excerpt/description terpisah | Tidak teramati sebagai kontrol terpisah pada snapshot awal. | UNKNOWN |

### 4.3 Cover/main image

Area berlabel `Image*` menyediakan tombol `Upload Image`. Teks requirement yang terlihat menyatakan bahwa image harus horizontal, rasio 3:2, dan format yang disebut adalah jpg, jpeg, heic, png, dan gif. Pada keadaan awal DOM tidak memiliki `input[type=file]`; file input dapat dibuat secara dinamis setelah tombol upload digunakan, tetapi mekanisme tersebut tidak diuji.

Belum diketahui apakah area ini secara platform dianggap cover image, main image, atau keduanya, dan belum diketahui bagaimana metadata source, source URL, credit, atau caption dikumpulkan. Tidak ada file yang dipilih atau diunggah.

### 4.4 Section and body controls

Body editor menggunakan satu rich-text contenteditable. Tombol `Add Content` terlihat di area editor. Klik satu kali dilakukan hanya untuk membuka affordance UI; tidak ada opsi yang dipilih dan tidak ada block yang ditambahkan. Menu yang terbuka tidak memberikan accessible menu node yang dapat diklasifikasikan secara aman pada snapshot yang diperoleh.

Section semantics, block insertion, heading/subheading behavior, paragraph boundary behavior, cursor placement, block reorder, dan block deletion belum dapat diverifikasi tanpa membuat perubahan pada artikel. Oleh karena itu, kontrol `Add Content` dicatat sebagai evidence research-only, bukan sebagai kontrak produksi.

### 4.5 Inline image behavior

Tidak ada inline-image button atau file input yang terlihat pada keadaan awal editor. Kontrol gambar yang terlihat adalah `Upload Image` pada area main image/cover. Karena tidak ada upload atau insertion yang dilakukan, belum ada evidence apakah gambar inline merupakan block TipTap, disisipkan pada cursor, dikelola oleh menu `Add Content`, atau diproses sebagai area cover terpisah.

**Jawaban deterministik untuk representasi `TEXT → IMAGE → TEXT → IMAGE → TEXT`: `UNKNOWN`.** Editor authenticated berhasil diamati, tetapi uji representasi ordering memerlukan data uji dan mutation terkontrol pada akun dummy. Task 10 berhenti sebelum mutation sesuai batas scope.

### 4.6 Draft, save, preview, dan submission

Accessibility search pada main area menemukan `Progress is Securely Saved Temporarily` dan `Reset Article`, tetapi tidak menemukan control eksplisit bernama save, draft, preview, review, submit, publish, send, atau moderation. Ini tidak membuktikan bahwa autosave atau submission tidak ada; hanya berarti control tersebut tidak terobservasi pada keadaan awal tanpa mutation.

Percobaan navigasi ulang ke route yang sama mengalami timeout saat menunggu `DOMContentLoaded` dan memunculkan dialog `beforeunload` generik. Dialog ditolak untuk mempertahankan halaman. Kejadian ini menunjukkan adanya unsaved-state/navigation guard pada browser state, tetapi bukan bukti bahwa draft telah tersimpan karena tidak ada isi artikel yang dibuat.

## 5. Selector research evidence

Tidak ada selector yang dipromosikan ke kontrak automation produksi. Kandidat berikut hanya disimpan sebagai evidence untuk riset lanjutan:

| Candidate | Evidence class | Reason and boundary |
| --- | --- | --- |
| `data-cy="editor-content"` | POTENTIALLY-STABLE | Semantik container editor terlihat, tetapi kestabilan lintas release belum diuji. |
| `contenteditable=true` + `role=textbox` | STABLE-LOOKING | Semantik standar editor teramati, tetapi belum cukup untuk menemukan instance yang tepat bila halaman berubah. |
| `name="publisher"` dan `name="category_id"` | POTENTIALLY-STABLE | Field metadata memiliki name yang bermakna; belum diverifikasi terhadap validasi dan variasi publisher/channel. |
| `name="details.0.value"` | UNKNOWN | Name memiliki indeks internal yang mungkin dinamis; tidak boleh diasumsikan stabil. |
| `placeholder="Insert Title"` | POTENTIALLY-STABLE | Label semantik terlihat, tetapi localization atau redesign dapat mengubah placeholder. |
| `data-cy="label-main-description"` | POTENTIALLY-STABLE | Label semantik terlihat; belum diuji lintas state. |
| `editor-*` IDs/data-cy | POTENTIALLY-STABLE | Toolbar memiliki ID semantik, tetapi belum ada read-back test dan satu ID teramati memiliki typo `blockqotes`. |
| Accessible text `Upload Image` | POTENTIALLY-STABLE | Control cover/main image terlihat; file-input lifecycle belum diverifikasi. |
| Accessible text `Add Content` | UNKNOWN | Affordance terlihat, tetapi menu options tidak teramati sebagai accessible nodes. |
| Generated CSS classes | DYNAMIC | Tidak digunakan dan tidak boleh menjadi selector produksi. |
| Coordinate selectors | FORBIDDEN | Tidak digunakan. |

## 6. Sanitized fixture

Fixture HTML lokal tersedia di [`fixtures/idn-times/editor-v1-authenticated.html`](../fixtures/idn-times/editor-v1-authenticated.html). Fixture tersebut hanya merepresentasikan struktur semantik yang relevan dengan placeholder dan sengaja tidak memuat cookie, token, password, authorization header, private account data, private article content, atau private URL query.

Fixture bukan rekonstruksi lengkap aplikasi IDN Times dan bukan bukti bahwa selector sudah stabil. Fixture disediakan agar parser evidence dan komponen riset dapat diuji offline sebelum ada kontrak automation produksi.

## 7. Dampak terhadap arsitektur ArticlePilot

Task 10 tidak mengubah model artikel, parser, validator, Media Core, BrowserSession contract, atau automation runner. Domain tetap tidak mengetahui DOM. Media tetap tidak mengetahui browser. Browser session tetap credential-free pada kontrak aplikasi dan tidak menerima password, cookie, token, atau authorization header dari UI.

Temuan baru hanya memperkaya evidence layer dan fixture research. Authenticated marker profile production belum diaktifkan karena evidence ini berasal dari satu observasi runtime dan belum memiliki stability/read-back validation lintas sesi. Selector catalog produksi juga tetap tidak diisi.

## 8. Limitations and unknowns

Task 10 belum menentukan editor block model lengkap, section creation semantics, inline-image insertion, image metadata source/credit/caption/source URL, file picker lifecycle, upload endpoint contract, image ordering after save/reload, autosave persistence, draft identifier semantics, preview state, validation rules, moderation state, submission confirmation, session lifetime, atau selector stability lintas release.

Task ini juga tidak membuktikan bahwa cookie session bootstrap tersebut cocok untuk Android WebView production. Mekanisme session bootstrap aplikasi tetap harus credential-free dan baru boleh dirancang setelah mekanisme resmi yang aman dan dapat diverifikasi diketahui. Cookie material harus dirotasi atau diinvalisasi oleh pemilik akun jika terdapat kekhawatiran keamanan.

## 9. Verification status

| Check | Result |
| --- | --- |
| Authenticated editor observation | PASS — `AUTHENTICATED_EDITOR` observed from independent account/editor evidence. |
| Selector production promotion | PASS — none promoted. |
| Article mutation | PASS — intentionally not performed. |
| Sanitized fixture | PASS — created at `fixtures/idn-times/editor-v1-authenticated.html`. |
| Credential/session leakage into repository | Must be checked before commit; user-provided session file is not part of repository. |
| Pure Kotlin tests | To be run after documentation/fixture update. |
| Aggregate Android test/lint/debug build | To be run; prior repository evidence reported missing Android SDK in this sandbox. |

## 10. Recommended Task 11

Task 11 sebaiknya menjadi **controlled offline editor contract validation** menggunakan fixture sanitized dan, bila pemilik proyek menyetujui mutation pada akun dummy, sesi device/emulator yang benar-benar dapat dikendalikan pengguna. Acceptance checkpoint pertama harus memverifikasi apakah `Add Content` menyediakan image/block option, apakah upload menghasilkan block yang dapat dibaca kembali, dan apakah urutan `TEXT → IMAGE → TEXT → IMAGE → TEXT` bertahan setelah save/reload.

Task 11 tidak boleh langsung mengaktifkan publishing automation. Sebelum itu, perlu ada read-back evidence, session bootstrap yang disetujui, profile versioning, selector stability test, failure/retry policy, serta manual takeover ketika state tidak dapat diverifikasi.

## References

[1]: https://community.idntimes.com/dashboard/create-article "IDN Times Community create article route"
[2]: ./idn-times-authenticated-reconnaissance.md "ArticlePilot prior authenticated reconnaissance report"
[3]: ./idn-times-publishing-research.md "ArticlePilot IDN Times publishing research"
[4]: ./browser-session.md "ArticlePilot browser session architecture"
