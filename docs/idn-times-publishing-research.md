# IDN Times Publishing Workflow Reconnaissance

**Proyek:** ArticlePilot
**Dokumen:** Spesifikasi riset workflow publishing IDN Times Community
**Penulis:** Manus AI
**Tanggal riset:** 16 Agustus 2026
**Status:** Research-only specification; tidak ada browser automation yang diimplementasikan

## 1. Executive summary

Task ini meneliti workflow publikasi yang secara eksplisit didokumentasikan oleh IDN Times dan IDN Media Support, kemudian membandingkannya dengan fondasi ArticlePilot yang telah tersedia. Kesimpulan paling kuat adalah bahwa proses contributor bukan publikasi langsung: penulis menyiapkan dan mengirim artikel melalui IDN Times Community atau entry point IDN App, lalu artikel masuk ke moderasi dan penyuntingan editor sebelum dapat terbit.[1] [9] [10]

Dokumentasi publik yang dapat diakses menjelaskan konsep utama workflow: penulis memiliki akun, membuka entry point menulis, mengisi metadata dan isi, mengunggah cover serta gambar dalam artikel, menyertakan sumber gambar, dapat menyimpan draft, lalu mengirimkan tulisan ke editorial.[2] [3] [4] Namun, sebagian detail antarmuka yang paling penting bagi automation—DOM saat ini, editor type, selector, autosave, mekanisme upload yang berjalan saat ini, serta dialog konfirmasi submit—tidak dapat dikonfirmasi tanpa akses dashboard contributor yang terautentikasi. Dashboard publik tidak dapat diamati secara pasif dalam lingkungan riset ini; tidak ada login, pengiriman credential, CAPTCHA, MFA/OTP, atau security challenge yang dicoba.

State moderation yang didukung dokumentasi support adalah **Draft**, **Dimoderasi**, **Revisi**, **Terbit**, dan **Ditolak**.[5] FAQ lain menjelaskan bahwa artikel yang disubmit dimoderasi terlebih dahulu, dan tulisan yang memenuhi ketentuan dapat terbit dalam waktu kurang dari tujuh hari menurut panduan yang dipublikasikan.[6] Angka tersebut harus diperlakukan sebagai guidance historis, bukan SLA. ArticlePilot tidak boleh menganggap submit sebagai published; automation masa depan harus memverifikasi status eksternal secara terpisah.

Dokumen ini merekomendasikan agar ArticlePilot mempertahankan domain model yang terpisah dari DOM, tidak menebak selector, dan menambahkan evidence fixture hanya setelah observasi authenticated yang dikendalikan pengguna. Rekomendasi engineering berikutnya adalah **authenticated manual reconnaissance dan evidence capture**, kemudian baru selector profile serta browser implementation dapat disetujui.

## 2. Scope, method, dan batas penelitian

Riset ini menjawab pertanyaan: bagaimana seorang contributor bergerak dari artikel yang sudah siap sampai artikel disubmit atau terbit, apa saja konsep media dan metadata yang terlihat dari sumber resmi, serta bukti apa yang dibutuhkan untuk merancang Browser Automation Core.

Sumber primer diprioritaskan: landing page resmi IDN Times Community, artikel tutorial resmi IDN Times, dan artikel FAQ resmi IDN Media Support. Artikel tutorial yang memuat label UI lama digunakan sebagai **historical official evidence**, bukan sebagai bukti bahwa label dan DOM tersebut masih sama pada 16 Agustus 2026. Tidak ada sumber sekunder yang dipakai untuk menetapkan requirement produksi.

Klasifikasi fakta dalam dokumen ini adalah sebagai berikut.

| Klasifikasi | Makna dalam dokumen ini |
|---|---|
| **CONFIRMED** | Pernyataan tertulis secara eksplisit dalam sumber resmi publik yang dirujuk. Jika sumbernya tutorial lama, status juga diberi penanda historical. |
| **INFERRED** | Pemetaan engineering dari konsep yang terdokumentasi ke state atau boundary ArticlePilot. Inference bukan requirement platform. |
| **UNKNOWN** | Tidak tersedia bukti primer yang cukup, memerlukan dashboard authenticated, atau bertentangan antarversi dokumentasi sehingga tidak boleh dijadikan hard requirement. |

## 3. Sources dan evidence register

| ID | Sumber | Kegunaan | Kualitas evidence |
|---|---|---|---|
| [1] | [IDN Times Community landing page][1] | Entry point publik dan positioning Community sebagai platform menulis. | Official public; current landing page, tetapi tidak mengekspos editor authenticated. |
| [2] | [10 Step-by-step Nulis Artikel di IDN Times Community][2] | Title, excerpt, struktur subheading, sources, paragraf, proofreading, dan submit. | Official editorial tutorial; currentness label tidak dipastikan, sehingga perilaku UI diperlakukan hati-hati. |
| [3] | [Tutorial upload gambar di dashboard terbaru IDN Times Community][3] | Unggah gambar, source metadata, URL sumber, format, ukuran, dimensi, cover, dan `Tambah Section`. | Official image tutorial; requirement media diberi konteks sebagai published guidance, bukan discovery runtime. |
| [4] | [Mengenal Fitur Menulis Baru di IDN Times Community][4] | Historical dashboard flow, section, image/embed, save draft, submit, dan status menu. | Official historical UI tutorial; label current DOM UNKNOWN. |
| [5] | [FAQ: Apa Saja Proses Yang Terjadi Pada Tulisan Saya?][5] | Vocabulary dan arti status Draft, Dimoderasi, Revisi, Terbit, Ditolak. | Official support FAQ; status vocabulary CONFIRMED sebagai dokumentasi, kontrol UI current UNKNOWN. |
| [6] | [FAQ: Kapan Artikel Saya Terbit?][6] | Moderasi editor, kriteria umum, dan guidance waktu kurang dari tujuh hari. | Official support FAQ; bukan SLA. |
| [7] | [FAQ: Bagaimana Cara agar Tulisan Saya Terbit?][7] | Originalitas, kualitas, sumber gambar, watermark, plagiarism, dan prohibited content. | Official support FAQ; editorial eligibility guidance. |
| [8] | [Tips Agar Artikel Kamu Cepat Terbit][8] | Penjelasan bahwa pending dapat berarti menunggu review atau belum memenuhi requirement. | Official support tips; recommendation, bukan jaminan. |
| [9] | [FAQ: Apa itu Community Writer?][9] | Definisi contributor dan moderasi/penyuntingan sebelum publikasi. | Official support FAQ. |
| [10] | [FAQ: Bagaimana Cara Menjadi Community Writer di IDN Times Community?][10] | IDN App account, menu `Tulis Berita`, dan `Menu Kreator`. | Official support FAQ; entry point contributor paling eksplisit yang ditemukan. |
| [11] | [IDN Connect user guide: IDN Points][11] | Candidate current IDN Connect user guide yang ditemukan, tetapi halaman hanya merender loading shell saat diuji. | Candidate source; tidak dipakai untuk menetapkan UI atau workflow. |

> **Catatan akses:** `community.idntimes.com/dashboard/` tidak dapat dinavigasi dalam sandbox karena policy restriction. Tidak ada upaya untuk mengganti akses tersebut dengan login, mengambil credential, atau melewati challenge. Karena itu, observasi current authenticated editor tidak tersedia.

## 4. Human publishing workflow

Workflow berikut memisahkan perilaku yang didukung sumber dari langkah yang hanya merupakan pemetaan engineering. Urutan konseptualnya dimulai ketika contributor sudah memiliki artikel dan media yang akan dikirim.

| Langkah manusia | Tindakan yang didokumentasikan | Expected observable result | Status | Evidence |
|---|---|---|---|---|
| 1. Menyiapkan artikel | Penulis menyiapkan title, excerpt, isi, struktur section, gambar, dan source. Sumber resmi menganjurkan proofreading, struktur yang jelas, dan source URL lengkap. | Dokumen siap dimasukkan ke entry point menulis. | **CONFIRMED** untuk guidance editorial; format internal ArticlePilot adalah milik proyek. | [2] [3] [7] |
| 2. Membuka entry point | FAQ resmi menyebut IDN App: buat akun, pilih tombol `+` lalu `Tulis Berita`, atau `Profile` lalu `Menu Kreator`. Sumber lain menyebut Dashboard Community. | Halaman atau screen penulisan terbuka. | **CONFIRMED** untuk entry point yang didokumentasikan; entry point web current UNKNOWN. | [10] [11] |
| 3. Autentikasi | Pembuatan akun IDN App didokumentasikan sebelum mulai menulis. Tutorial lama menyebut login sebelum dashboard dan pernah menyebut Facebook/email. | Contributor berada dalam account/session yang dapat menulis. | **CONFIRMED** bahwa account diperlukan; provider, cookie/session lifetime, MFA/OTP, dan current login UI **UNKNOWN**. | [4] [10] |
| 4. Membuat tulisan | Historical tutorial mendeskripsikan entry penulisan melalui dashboard/ikon pena; FAQ current-ish mendeskripsikan `Tulis Berita`. | Form editor baru terbuka. | **CONFIRMED historical**; label current dan route **UNKNOWN**. | [4] [10] |
| 5. Mengisi metadata | Title, excerpt, category, cover, opening paragraph, dan section dijelaskan dalam tutorial. | Metadata terlihat di form dan dapat ditinjau. | **CONFIRMED** untuk title/excerpt/category/cover/body; current control semantics **UNKNOWN**. | [2] [3] [4] |
| 6. Menulis body | Tutorial menjelaskan opening paragraph, subheading, gambar, lalu narration; `Tambah Section` dipakai untuk struktur berulang. | Article body tersusun secara berurutan. | **CONFIRMED historical/documented concept**; arbitrary DOM insertion **UNKNOWN**. | [2] [3] [4] |
| 7. Memasukkan media | `Unggah Gambar` dan `Sisipkan Gambar/Embed` didokumentasikan. Image form mencakup source-related information dan source URL. | Gambar tersedia pada posisi body yang dipilih. | **CONFIRMED** bahwa upload/insertion concept ada; current dialog, selection, dan exact insertion behavior **UNKNOWN**. | [3] [4] |
| 8. Meninjau artikel | Penulis dianjurkan membaca ulang untuk typo, spelling, italic, dan struktur sebelum submit. | Article siap dikirim atau disimpan. | **CONFIRMED** sebagai editorial instruction; dedicated current preview control **UNKNOWN**. | [2] |
| 9. Menyimpan draft | Historical tutorial menyebut `Save as Draft`; FAQ mendefinisikan Draft sebagai konsep yang belum disubmit dan masih dapat diedit. | Artikel berada pada Draft. | **CONFIRMED historical/documented**; autosave dan current draft UI **UNKNOWN**. | [4] [5] |
| 10. Submit ke editorial | Historical tutorial menyebut `Submit to Editorial` dan tutorial penulisan menyebut klik submit ke editorial. | Artikel keluar dari draft dan masuk proses editorial. | **CONFIRMED** untuk submit concept; exact confirmation UI **UNKNOWN**. | [2] [4] |
| 11. Moderasi | Artikel yang dikirim dimoderasi dan diedit oleh editor. Status documented meliputi Dimoderasi, Revisi, Terbit, dan Ditolak. | Contributor dapat melihat hasil atau status editorial. | **CONFIRMED** sebagai proses/status vocabulary; current dashboard control **UNKNOWN**. | [5] [6] [9] |
| 12. Terbit atau perlu tindakan | Artikel yang lolos dapat terbit; artikel yang memerlukan perubahan dikembalikan dengan alasan/revisi; artikel yang ditolak dapat diperbaiki dan dikirim ulang menurut FAQ. | Status Terbit, Revisi, atau Ditolak terlihat pada account. | **CONFIRMED** secara documented; status transition event/API **UNKNOWN**. | [5] [6] |

Secara operasional, ArticlePilot hanya dapat mengontrol langkah sampai artikel menjadi **locally READY** dan menyiapkan input untuk langkah 2–10. Langkah moderasi, revisi editor, dan publikasi adalah proses eksternal yang harus direpresentasikan sebagai status observasi, bukan sebagai aksi yang dianggap berhasil oleh client.

## 5. Authentication workflow

Bukti publik yang paling jelas menyatakan bahwa contributor perlu membuat akun IDN App sebelum memilih `Tulis Berita` atau `Menu Kreator`.[10] Landing page Community sendiri tidak mengekspos editor yang dapat digunakan tanpa session.[1] Tutorial lama mendokumentasikan login sebelum dashboard serta provider Facebook/email, tetapi fakta tersebut berasal dari UI historis dan tidak boleh dipakai sebagai requirement provider current.[4]

| Authentication concern | Finding | Status |
|---|---|---|
| Account required | FAQ onboarding meminta pembuatan akun IDN App sebelum menulis. | **CONFIRMED** [10] |
| Web dashboard session | Dashboard web disebut oleh dokumentasi dan historical tutorial, tetapi session current tidak dapat diamati. | **INFERRED / UNKNOWN** [4] [11] |
| IDN App session | IDN App adalah entry point resmi yang terdokumentasi. | **CONFIRMED** [10] |
| OAuth/social providers | Facebook/email pernah disebut dalam tutorial lama. Provider current tidak dapat dikonfirmasi. | **CONFIRMED historical; current UNKNOWN** [4] |
| Cookies, tokens, session lifetime | Tidak dijelaskan oleh sumber yang dikonsultasikan. | **UNKNOWN** |
| MFA/OTP | Tidak terlihat dan tidak didokumentasikan oleh sumber yang dikonsultasikan. | **UNKNOWN** |
| CAPTCHA/anti-bot | Tidak diperiksa dan tidak boleh dibypass. Jika muncul, future automation harus pause. | **UNKNOWN / safety boundary** |
| Credential storage | Tidak boleh ditangani ArticlePilot backend; autentikasi harus tetap pada user-controlled browser/session. | **INFERRED architecture constraint** |

Konsekuensi engineering-nya adalah login harus menjadi **manual user-controlled step** pada fase awal Browser Core. ArticlePilot boleh mendeteksi halaman login atau session expiry, tetapi tidak boleh mengumpulkan password, mengisi credential secara otomatis, menyimpan token, atau melewati challenge keamanan.

## 6. Article field mapping

Mapping berikut membandingkan model ArticlePilot saat ini dengan konsep yang benar-benar muncul dalam sumber IDN Times. Status **CONFIRMED** berarti konsepnya didukung dokumentasi; status tersebut tidak berarti selector atau field API saat ini sudah diketahui.

| ArticlePilot field/block | Model saat ini | Kandidat IDN Times field/control | Status | Catatan evidence/gap |
|---|---|---|---|---|
| `Article.metadata.title` | `String` wajib | Title/judul artikel | **CONFIRMED** | Tutorial meminta judul menarik, jelas, dan ringkas. [2] |
| `Article.metadata.excerpt` | `String?` | Excerpt/ringkasan di bawah title | **CONFIRMED** | Excerpt dijelaskan dalam tutorial penulisan. [2] |
| `Article.metadata.category` | `String?` | Category/kategori | **CONFIRMED** | Category muncul pada tutorial form. [4] |
| `Article.metadata.tags` | `List<String>` | Tags/tag control | **UNKNOWN** | Tidak ada sumber primer yang dikonsultasikan yang menetapkan field/tag behavior current. |
| `Article.cover` | `ImageAsset?` | Cover image | **CONFIRMED** | Cover dan rekomendasi landscape didokumentasikan. [3] [4] |
| `ArticleSection.heading` | `String?` | Subheading/section heading | **CONFIRMED** sebagai konsep | `Tambah Section` didokumentasikan; current control dan jumlah minimal current perlu verifikasi. [2] [3] [4] |
| `TextBlock` | `text` biasa | Paragraph/narrative | **CONFIRMED** | Opening, closing, dan narration adalah konsep eksplisit. [2] [4] |
| `ImageBlock.asset` | Image block berurutan | Inline image di antara struktur artikel | **CONFIRMED** sebagai konsep | Tutorial menempatkan image dalam section; arbitrary insertion current **UNKNOWN**. [3] [4] |
| `ImageAsset.caption` | `String?` | Description/caption image | **CONFIRMED** | Form sumber menjelaskan description terkait image; label current belum diverifikasi. [3] |
| `ImageAsset.sourceUrl` | `String?` | Tautan URL sumber | **CONFIRMED** | Full source URL diminta, bukan hanya domain. [3] |
| `ImageAsset.sourceName` | `String?` | Nama sumber/domain/akun/photographer/agency | **CONFIRMED** | Conventions berbeda menurut asal media. [3] |
| `ImageAsset.credit` | `String?` | Credit/attribution | **CONFIRMED** sebagai kebutuhan provenance | Perbedaan antara label credit dan source field current **UNKNOWN**. [3] [7] |
| Future rich text | Belum ada span/mark model | Bold/italic/link/formatting toolbar | **UNKNOWN** | Sumber menyebut proofreading italic, tetapi current editor toolbar dan data model tidak terkonfirmasi. [2] |
| Future embed block | Belum ada | Video/social/embed | **CONFIRMED historical** | `Sisipkan Gambar/Embed` mencakup image, video embed, atau social post dalam tutorial lama. [4] |

Tidak ada alasan untuk menghapus `tags`, `sourceUrl`, atau `ImageAsset` hanya karena current UI belum terobservasi. Sebaliknya, field yang belum terbukti digunakan oleh platform harus tetap dianggap **optional/platform-profile dependent** sampai evidence baru tersedia.

## 7. Image workflow

### 7.1 Cover image

Tutorial upload image menjelaskan action `Unggah Gambar`, menyebut cover sebaiknya landscape, dan memberi requirement published berupa ukuran maksimum 1 MB, minimum 600 × 315 px, serta format JPEG, PNG, atau GIF.[3] Requirement tersebut cukup kuat untuk menjadi **documented validation profile candidate**, tetapi belum boleh diberi label universal current platform invariant karena tutorial tidak membuktikan enforcement runtime atau perubahan sesudah tutorial diterbitkan.

### 7.2 Inline image dan insertion

Historical tutorial mendeskripsikan pola section berupa subheading, image, kemudian narrative paragraph, serta action `Tambah Section` dan `Sisipkan Gambar/Embed`.[3] [4] Ini mengonfirmasi bahwa image bukan hanya cover; image dapat menjadi bagian dari struktur body. ArticlePilot `ArticleSection.blocks` sudah dapat merepresentasikan urutan `TextBlock` dan `ImageBlock` tanpa coupling ke DOM.

Sumber tidak membuktikan bahwa contributor dapat memilih **setiap posisi arbitrer** antara dua paragraph, atau apakah insertion dilakukan pada caret, section boundary, modal picker, atau upload queue. Karena itu, arbitrary insertion harus diberi status **UNKNOWN**. Future automation harus memverifikasi block order sesudah insertion; mengirim file tanpa memeriksa posisi tidak cukup.

### 7.3 Source, caption, credit, dan provenance

Dialog/form yang dijelaskan tutorial memiliki area `Sumber`, caption/description, source name, dan field `Tautan URL` sumber. Full URL diminta, bukan hanya nama domain.[3] FAQ editorial juga menekankan source gambar yang original dan baik, serta melarang watermark dalam kriteria kemungkinan terbit tinggi.[7]

ArticlePilot saat ini sudah memisahkan `downloadUrl` dari `sourceUrl`, serta memiliki `sourceName`, `credit`, `caption`, MIME, dimensi, ukuran, local reference, dan validation status. Model tersebut cocok dengan kebutuhan provenance yang terlihat, tetapi belum menyimpan bukti hak penggunaan, permission evidence, atau hasil verifikasi bahwa source URL benar-benar mengarah ke asset yang diunggah.

### 7.4 Format, ukuran, dimensi, dan aspect ratio

Published image tutorial menyebut JPEG, PNG, GIF, maksimum 1 MB, minimum 600 × 315 px, dan rekomendasi dimensi lebih besar seperti 800 × 350 px atau rasio 3:2.[3] Guidance lain yang diterbitkan IDN menyebut 800 × 350 px dalam konteks category/article tertentu.[4] Karena terdapat perbedaan konteks dan tidak ada current authenticated enforcement test, ArticlePilot harus memodelkan angka tersebut sebagai **versioned platform policy candidate**, bukan hardcode global.

| Media requirement | Evidence result | Status untuk automation |
|---|---|---|
| JPEG, PNG, GIF | Explicitly listed in image tutorial. | **CONFIRMED documented guidance** [3] |
| Maximum 1 MB | Explicitly listed in image tutorial. | **CONFIRMED documented guidance; runtime enforcement UNKNOWN** [3] |
| Minimum 600 × 315 px | Explicitly listed in image tutorial. | **CONFIRMED documented guidance; scope/currentness UNKNOWN** [3] |
| Recommended larger dimensions / 3:2 | Explicitly described as recommendation. | **CONFIRMED recommendation; not hard requirement** [3] |
| 800 × 350 px universal requirement | Appears in category/editorial guidance but conflicts in scope with other recommendation. | **UNKNOWN as universal requirement** [4] |
| Cover landscape | Explicitly recommended. | **CONFIRMED recommendation** [3] |
| No watermark | Editorial eligibility guidance. | **CONFIRMED editorial criterion** [3] [7] |
| Pinterest/personal blog source restriction | Image tutorial guidance. | **CONFIRMED documented guidance; enforcement UNKNOWN** [3] |
| Permission for individual creator images | Image tutorial guidance. | **CONFIRMED documented guidance** [3] |

### 7.5 Duplicate, replace, delete, dan reorder

Tidak ada evidence primer yang dikonsultasikan yang menjelaskan secara current bagaimana editor menangani duplicate image, replace image, delete image, atau reorder image sesudah upload. Semua perilaku tersebut adalah **UNKNOWN**. ArticlePilot tidak boleh menganggap upload idempotent atau menghapus dan mengulang upload tanpa bukti bahwa operation tersebut aman.

## 8. Editor behavior

Sumber historical mendeskripsikan dashboard dengan entry penulisan, field metadata, section, image/embed control, `Save as Draft`, `Submit to Editorial`, dan menu status.[4] Sumber upload image mendeskripsikan `Unggah Gambar`, source area, dan `Tambah Section`.[3] Ini cukup untuk membentuk **behavioral hypothesis**, tetapi tidak cukup untuk menghasilkan selector.

| Editor behavior | Evidence | Status |
|---|---|---|
| Form atau editor terstruktur dengan metadata dan body | Tutorial screenshot/text flow. | **CONFIRMED historical** [3] [4] |
| Section-based authoring | `Tambah Section`, subheading, image, narration. | **CONFIRMED documented concept** [2] [3] [4] |
| Rich-text toolbar | Tidak ada evidence current yang cukup. | **UNKNOWN** |
| `contenteditable` atau editor implementation | Tidak dapat diamati tanpa authenticated dashboard. | **UNKNOWN** |
| Upload control/modal | `Unggah Gambar` dan source form dijelaskan. | **CONFIRMED historical/documented; current DOM UNKNOWN** [3] |
| Image picker/library selection | Tidak dijelaskan dengan cukup. | **UNKNOWN** |
| Preview control | Penulis diminta reread, tetapi dedicated preview implementation current tidak terkonfirmasi. | **UNKNOWN** [2] |
| Autosave | Tidak ada evidence primer yang cukup. | **UNKNOWN** |
| Navigation between fields | Urutan form dideskripsikan secara konseptual, bukan keyboard/DOM behavior. | **UNKNOWN** |
| Current accessibility names/test IDs | Tidak tersedia. | **UNKNOWN** |

Maka, `automation:selectors` ArticlePilot tidak boleh diisi berdasarkan label historical semata. Selector profile harus memiliki version, target semantic, dan fixture evidence dari observasi current yang dikendalikan.

## 9. Draft, submission, dan moderation behavior

FAQ support mendokumentasikan status berikut:

| Status documented | Makna yang didokumentasikan | Automation interpretation |
|---|---|---|
| **Draft** | Tulisan belum disubmit dan masih dapat diedit. | State lokal/platform draft; safe to edit, tetapi current save evidence belum diketahui. [5] |
| **Dimoderasi** | Tulisan telah dikirim dan sedang dimoderasi/editor review. | Submitted-but-not-published; jangan menganggap success final. [5] |
| **Revisi** | Sebagian tulisan memerlukan perubahan agar memenuhi standar. | External action required; pause dan tampilkan reason kepada user. [5] |
| **Terbit** | Artikel telah published. | Final external success hanya setelah evidence status/article URL diverifikasi. [5] [9] |
| **Ditolak** | Tulisan tidak lolos proses; FAQ menyarankan versi yang diperbaiki dapat dikirim kembali. | Terminal untuk submission attempt, tetapi article revision baru adalah operation berbeda. [5] [6] |

FAQ waktu publikasi menyatakan bahwa setiap artikel melalui moderasi, editor memeriksa fakta, kualitas tulisan, dan penulisan sumber gambar; FAQ tersebut menyebut artikel yang memenuhi ketentuan dapat terbit dalam kurang dari tujuh hari.[6] FAQ proses juga menyatakan bahwa artikel yang tetap berada di `Dimoderasi` dalam tujuh hari masuk `Ditolak` menurut guidance yang ditampilkan.[5] Karena halaman support memiliki usia/updated label historis, ArticlePilot harus menyimpan timestamp dan observed status, bukan membuat timer yang menyimpulkan rejection tanpa membaca platform.

Konfirmasi submit yang aman harus berarti platform memperlihatkan evidence bahwa artikel berpindah dari Draft ke Dimoderasi atau menampilkan confirmation yang setara. `Submit` click event saja tidak cukup. Konfirmasi published harus berbeda lagi: status Terbit atau public article URL yang terverifikasi diperlukan.

## 10. Future automation state machine

State machine berikut adalah hasil pemetaan evidence, bukan implementasi dan bukan klaim bahwa semua state sudah dipastikan oleh DOM current. State yang diambil dari ArticlePilot sebelumnya dipertahankan, tetapi `SAVE_DRAFT`, `VERIFY_SUBMITTED`, dan external moderation states ditambahkan karena workflow publik mendukung konsep tersebut.

```text
START
  ↓
OPEN_ENTRY_POINT
  ↓
CHECK_SESSION ───────────────┐
  ↓                          │
OPEN_EDITOR                  │ session/challenge issue
  ↓                          │
FILL_METADATA                │
  ↓                          │
UPLOAD_COVER                 │
  ↓                          │
BUILD_BODY                   │
  ↓                          │
UPLOAD_INLINE_IMAGE          │
  ↓                          │
FILL_IMAGE_SOURCE_METADATA   │
  ↓                          │
VERIFY_BLOCK_ORDER           │
  ↓                          │
SAVE_DRAFT (optional)        │
  ↓                          │
FINAL_REVIEW                 │
  ↓                          │
SUBMIT_TO_EDITORIAL          │
  ↓                          │
VERIFY_SUBMITTED → DIMODERASI│
  ↓                          │
WAIT_EXTERNAL_MODERATION     │
  ├─ REVISI ── MANUAL_REVIEW_AND_RESUBMIT
  ├─ DITOLAK ── NEW_REVISION_REQUIRED
  └─ TERBIT ── VERIFY_PUBLIC_RESULT → COMPLETE
                             │
                             └─ PAUSE_FOR_MANUAL_TAKEOVER
```

| State | Input | Action boundary | Expected observable result | Failure possibilities | Recovery |
|---|---|---|---|---|---|
| `OPEN_ENTRY_POINT` | Locally READY article dan user-controlled session. | Buka entry point yang disetujui profile. | Page marker cocok dengan entry point. | URL blocked, redirect, app/web divergence. | Inspect current page; pause jika unknown. |
| `CHECK_SESSION` | Page loaded. | Inspect login/editor indicator. | Authenticated writer state terverifikasi. | Session expired, login required, security challenge. | Manual login/takeover; tidak mengisi credential. |
| `OPEN_EDITOR` | Authenticated contributor state. | Pilih `Tulis Berita`, `Menu Kreator`, atau current equivalent. | Editor creation surface terdeteksi. | Menu berubah, modal tidak muncul, permission denied. | Reinspect; pause bila halaman unknown. |
| `FILL_METADATA` | Title, excerpt, category, optional tags, cover metadata. | Fill semantic fields one per one. | Field values read-back cocok. | Validation error, wrong field, lost focus. | Reinspect field, retry only idempotent fill. |
| `UPLOAD_COVER` | Validated local cover file. | Dispatch file upload through verified control. | Preview/file metadata appears and cover association verified. | Size/format reject, upload timeout, wrong slot. | Remove/replace only if behavior proven; otherwise pause. |
| `BUILD_BODY` | Ordered sections and blocks. | Create section and write text. | Section count, headings, and text read-back match source. | Rich text transform, missing section, autosave failure. | Re-read state and checkpoint; do not blindly continue. |
| `UPLOAD_INLINE_IMAGE` | Image block id and local file. | Upload at selected section/block boundary. | Image preview/asset evidence associated with intended block. | Upload failed, duplicate, wrong position. | Retry with evidence; manual takeover if association unclear. |
| `FILL_IMAGE_SOURCE_METADATA` | Caption, source name, source URL, credit. | Fill source/description fields. | Values read-back and attached to correct image. | Required field error, label mismatch, source URL rejected. | Reinspect, fix data, pause if field identity uncertain. |
| `VERIFY_BLOCK_ORDER` | Article block manifest. | Inspect rendered/editor structure. | Ordered block evidence equals manifest. | Image inserted adjacent to wrong text/section. | Undo/manual correction; never submit unknown ordering. |
| `SAVE_DRAFT` | Partially or fully built article. | Dispatch save draft if current control is verified. | Platform status remains Draft and values persist after reload. | Save error, navigation, stale state. | Reload and compare; retry only with idempotency evidence. |
| `FINAL_REVIEW` | Complete filled article. | Inspect metadata, block count, warning, image source. | All required values and evidence match. | Hidden validation, missing image, source mismatch. | Return to specific block; no submit. |
| `SUBMIT_TO_EDITORIAL` | Review passed and user confirmation. | Dispatch submit once. | Action result observed; not yet final success. | Double submit, modal, network timeout. | Reinspect status; do not repeat blindly. |
| `VERIFY_SUBMITTED` | Post-submit page/result. | Inspect status indicator/list. | Status changes to Dimoderasi or equivalent confirmed evidence. | Submit accepted but status stale/unknown. | Refresh/reopen dashboard; pause if ambiguous. |
| `WAIT_EXTERNAL_MODERATION` | Submitted status and timestamp. | No blind automation; observe only when user requests. | Status becomes Revisi, Ditolak, or Terbit. | Long pending, changed policy, account issue. | Notify user; preserve session and evidence. |
| `HANDLE_REVISI_OR_DITOLAK` | Platform status and reason. | Pause for human decision and new article revision. | User acknowledges reason and prepares new revision. | Reason unavailable, status ambiguous. | Manual takeover/support; never overwrite source silently. |
| `VERIFY_PUBLIC_RESULT` | Terbit status or public article URL. | Open/inspect public result without modifying it. | Public article identity/title/status match. | Preview differs, not indexed, URL unavailable. | Mark verification incomplete, not Complete. |
| `COMPLETE` | Verified published evidence. | Persist final checkpoint and evidence. | Session completed with timestamp and URL/status. | Evidence later invalidated. | Keep audit log; do not erase prior evidence. |

State transitions must be evidence-driven. Existing ArticlePilot `AutomationCheckpoint`, `AutomationFailure`, `ManualTakeoverRequest`, and `SelectorCatalog` boundaries are suitable starting points, tetapi platform-specific profile belum boleh diaktifkan sebelum evidence current tersedia.

## 11. Failure points dan required detection information

| Failure point | What automation must detect | Safe response |
|---|---|---|
| Session expired | Login indicator, redirect, missing editor permission, atau auth state berubah. | Pause and request manual takeover; do not store or submit credentials. |
| Upload failed | Explicit upload error, no preview, missing file metadata, timeout, or response not associated with target block. | Retry according to bounded policy; otherwise pause with asset/block id. |
| Image rejected | Format/size/dimension error or server-side rejection message. | Preserve platform message, map to asset validation issue, do not silently discard. |
| Wrong image position | Post-upload structure does not match expected block id/order. | Stop before next block; manual correction or deterministic recovery only after evidence. |
| Duplicate image | Existing image association or editor warning. | Treat as unknown/idempotency risk until current behavior is observed. |
| Replace/delete/reorder ambiguity | Action result not reflected in verified structure. | Pause; never perform destructive action on guessed target. |
| Autosave failure | Reload loses field values or status stays stale. | Reinspect draft and compare manifest; do not assume persistence. |
| Page navigation/reload | URL/page marker changed unexpectedly or checkpoint evidence invalidated. | Reinspect from checkpoint and invalidate unverified state. |
| Modal not appearing | Expected semantic dialog/file input absent after action. | Retry only within policy; pause if UI is unknown. |
| Network timeout | Transport timeout with no authoritative result. | Reopen/inspect status before retrying submit or upload. |
| Validation error | Field-level error, missing source, unsupported media, or editorial message. | Return to exact field/block and surface message. |
| Submission rejected | Status Ditolak or explicit rejection reason. | Mark attempt terminal; require user-directed revision, not automatic resubmit. |
| Revision required | Status Revisi and reason. | Pause for human review; retain original and new revision separately. |
| Moderation pending | Dimoderasi persists or status unavailable. | Do not treat timeout as rejection; observe and notify. |
| Security challenge | CAPTCHA, anti-bot, suspicious-login, or equivalent. | Pause and require manual user action; no bypass. |

For each failure, future persistence will need session id, article id, phase, section/block id, attempt, timestamp, last verified evidence, current URL/page, platform status, and user-facing reason. These values align with existing recovery contracts but are not yet durable in Room.

## 12. ArticlePilot gap analysis

### 12.1 Capabilities already present

ArticlePilot already has a useful local preparation boundary. Article Format v1.0 is parsed into an extensible `Article` model with metadata, cover, sections, ordered `TextBlock`/`ImageBlock`, and `ImageAsset` provenance fields. Generic article validation is policy-driven. Media Core downloads, inspects, validates, stores, cleans up, and reports state without coupling to the browser. The Android Article Workspace preserves raw source, exposes parser diagnostics, displays ordered blocks, processes media, and gates local readiness strictly.

The repository also already contains browser and automation contracts: `BrowserSession`, `BrowserBridge`, `AutomationPhase`, `AutomationCheckpoint`, `RecoveryPolicy`, `PublishingProfile`, and semantic `SelectorCatalog`. These contracts explicitly separate domain model from DOM and include manual takeover as a first-class boundary. The current `UnconfiguredIdnTimesProfile` and unimplemented runner correctly prevent guesses from becoming production behavior.

### 12.2 Missing or incomplete fields

| Gap | Why it matters | Recommended treatment |
|---|---|---|
| Platform publication status | Current model has `PublishingState`, but not Draft/Dimoderasi/Revisi/Terbit/Ditolak. | Add a platform-status value object or profile-owned status mapping after current evidence is captured. |
| Submission identity | Future verification may need remote article id, public URL, or submission timestamp. | Add optional external identity and evidence fields to publishing session, not Article core. |
| Revision/rejection reason | Support workflow explicitly includes revision/rejection explanations. | Persist structured platform message and preserve revisions as separate draft revisions. |
| Rich text marks | Current `TextBlock` is plain text; current editor formatting is unknown. | Do not add speculative marks yet; capture current editor behavior first. |
| Embed/video/social blocks | Historical tutorial mentions `Sisipkan Gambar/Embed`. | Consider future block type only after current product requirement is confirmed. |
| Tags semantics | ArticlePilot supports tags, but source research does not prove current IDN control or requirement. | Keep optional; do not make blocking until verified. |
| Image rights evidence | Model has source/credit/caption but not permission/provenance evidence. | Add policy-level provenance/rights evidence if editorial requirement becomes operational. |
| Uploaded remote asset identity | Local file reference is not the same as platform media id or URL. | Add upload result identity in Browser/Automation Core, not Media Core. |
| Current editor field taxonomy | Historical labels may have changed. | Use selector profile evidence fixtures; do not rename domain fields based on UI guess. |

### 12.3 Potentially unnecessary or profile-dependent fields

`tags` may be valid for some IDN entry point or article type but is not confirmed as a current required control. `credit` and `sourceName` may map to one combined source form or two separate controls; both should remain in the domain because provenance distinctions are useful, while the platform profile decides how they are rendered.

The current `ArticleSection` abstraction is not contradicted by the source material. The documented section/subheading behavior supports it. However, the source does not prove that every section must contain an image, so ArticlePilot should not make that rule generic. A category-specific `PlatformValidationPolicy` may eventually express a requirement such as an image per listicle subheading if current guidance and enforcement justify it.

### 12.4 Workflow gaps

The current `PublishingSession` states—Not Started, In Progress, Paused for Manual Takeover, Failed, and Completed—are useful execution states but do not represent the platform’s editorial status. A future session should distinguish **automation execution state** from **remote publication state**. For example, automation may be completed for submission while remote state is still Dimoderasi. Conflating those concepts would make a successful submit look like a published article.

Persistence is also not yet available. Room entities, draft revisions, publishing checkpoints, automation logs, evidence snapshots, and media lifecycle retention remain deferred. WorkManager should not be added until operation idempotency and checkpoint semantics are defined.

## 13. Open questions

The following questions remain explicitly open and must not be turned into selectors or hard requirements without new evidence.

| Topic | Open question |
|---|---|
| Entry point convergence | Is the current web Dashboard Community still active, or is IDN App now the canonical contributor editor? Are both backed by the same account and article state? |
| Authentication | Which login providers are current? Are cookies, app tokens, SSO, MFA, or OTP involved? What is the safe manual takeover boundary? |
| Editor implementation | Is the current editor contenteditable, a structured form, a rich-text editor, or a native app screen? Which controls have stable accessible names or test IDs? |
| Metadata | Are excerpt, category, and tags all current fields? Which are required by article category? |
| Body structure | Is `Tambah Section` still present? Can a contributor insert an image at any arbitrary caret/block boundary? |
| Image upload | Is upload synchronous or queued? Is there a media library? Does upload preserve local ordering? |
| Image replacement | Can an uploaded image be replaced, deleted, or reordered without recreating the section? |
| Image requirements | Are 1 MB, 600 × 315 px, JPEG/PNG/GIF, and landscape cover still enforced? Are WebP or other formats accepted now? |
| Source metadata | Are description, source name, credit, and source URL separate fields? Which are required for cover versus inline media? |
| Draft behavior | Is save manual, autosaved, or both? What evidence confirms durable draft persistence after reload? |
| Submit confirmation | Does submit show a modal, redirect, toast, status change, or all of these? Is duplicate submit prevented? |
| Moderation | Does the seven-day guidance still apply? Are Revisi and Ditolak distinct current states? What is the exact resubmission flow? |
| Published verification | Is a public article URL immediately available? How should an editor distinguish a published Community article from an unavailable/removed article? |
| Error semantics | What exact errors are returned for upload, source URL, policy, session, and moderation failures? |
| Rate limits | Are there upload, submit, or account-level rate limits that affect safe retry? |

## 14. Recommended next engineering task

The next task should be **Controlled Authenticated Reconnaissance and Evidence Capture**, not production browser automation. The user should manually open the current contributor editor in a user-controlled authenticated browser/session. ArticlePilot should not request or store credentials. The session should be used only to record a human-observed workflow checklist and sanitized local HTML/screenshot fixtures where permitted.

The evidence capture task should answer the open questions in a controlled order: current entry point, authentication result, editor field inventory, image upload dialog, insertion position, draft persistence, submit transition, and moderation status representation. Each observation should record timestamp, URL/page class, visible semantic label, action, result, and whether the result persisted after reload. Sensitive values, cookies, tokens, and personal information must be excluded from fixtures.

Only after that evidence is reviewed should ArticlePilot implement a versioned `PublishingProfile`, selector definitions, local HTML integration fixtures, and a browser state-machine adapter. The profile should initially support manual takeover for any unknown state. No live submission should be used as a routine test, and no CAPTCHA or anti-bot mechanism should be automated or bypassed.

## 15. Documentation and implementation boundary for this task

This task intentionally makes no production code changes. It adds only this research specification and the temporary local research notes used during synthesis; the temporary notes should not be committed as the source of truth. No WebView, JavaScript bridge implementation, DOM selector, coordinate click, login automation, credential storage, CAPTCHA bypass, anti-bot bypass, or automatic publishing was added.

The source of truth for future Browser Automation design is this document together with `docs/automation.md`, `docs/recovery.md`, `docs/architecture.md`, and the existing browser/automation contracts. Where this document says **UNKNOWN**, ArticlePilot must pause at implementation time rather than invent behavior.

## References

[1]: https://community.idntimes.com/ "IDN Times Community landing page"

[2]: https://www.idntimes.com/life/inspiration/nulis-artikel-c1c2-01-km3zy-jr1xmb "10 Step-by-step Nulis Artikel di IDN Times Community"

[3]: https://www.idntimes.com/life/inspiration/tutorial-upload-gambar-di-dashboard-baru-idn-times-community-c1c2-01-km3zy-n1ln7z "Tutorial upload gambar di dashboard terbaru IDN Times Community"

[4]: https://www.idntimes.com/life/inspiration/mengenal-fitur-menulis-baru-di-idn-times-community-c1c2-01-km3zy-7smt4x "Mengenal Fitur Menulis Baru di IDN Times Community"

[5]: https://idnmediasupport.zendesk.com/hc/en-us/articles/18333213618329--FAQ-Community-Apa-Saja-Proses-Yang-Terjadi-Pada-Tulisan-Saya "FAQ Community: Apa Saja Proses Yang Terjadi Pada Tulisan Saya?"

[6]: https://idnmediasupport.zendesk.com/hc/en-us/articles/18333554936601--FAQ-Community-Kapan-Artikel-Saya-Terbit "FAQ Community: Kapan Artikel Saya Terbit?"

[7]: https://idnmediasupport.zendesk.com/hc/en-us/articles/18333348819993--FAQ-Community-Bagaimana-Cara-agar-Tulisan-Saya-Terbit "FAQ Community: Bagaimana Cara agar Tulisan Saya Terbit?"

[8]: https://idnmediasupport.zendesk.com/hc/en-us/articles/15269024570905--Tips-Community-Tips-Agar-Artikel-Kamu-Cepat-Terbit "Tips Community: Tips Agar Artikel Kamu Cepat Terbit"

[9]: https://idnmediasupport.zendesk.com/hc/en-us/articles/18332789102617--FAQ-Community-Apa-itu-Community-Writer "FAQ Community: Apa itu Community Writer?"

[10]: https://idnmediasupport.zendesk.com/hc/en-us/articles/18332891131929--FAQ-Community-Bagaimana-Cara-Menjadi-Community-Writer-di-IDN-Times-Community "FAQ Community: Bagaimana Cara Menjadi Community Writer di IDN Times Community?"

[11]: https://connect.idn.media/my-account/user-guide/points "IDN Connect: IDN Points user guide"
