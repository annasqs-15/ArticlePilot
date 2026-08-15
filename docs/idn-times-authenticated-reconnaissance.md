# ArticlePilot — Task 08
## Controlled Authenticated IDN Times Reconnaissance & Evidence Capture

**Tanggal dokumen:** 16 Agustus 2026 GMT+7
**Connector:** Playwright; My Browser tidak digunakan.
**Scope:** Reconnaissance dan evidence capture saja. Tidak ada production browser automation, artikel yang dibuat, draft yang disimpan, upload, submit, atau publikasi.

## 1. Executive summary

File sesi `set_cookies.txt` ditemukan dan digunakan hanya melalui mekanisme file-input serta konteks browser Playwright yang dibatasi pada domain IDN Times. Nilai cookie, nama cookie, credential, token, authentication header, request body, dan data akun tidak dicetak, disimpan, atau di-commit.

Session tersebut **tidak berhasil dikonfirmasi sebagai authenticated contributor session**. Navigasi ke dashboard sempat menghasilkan title `Manage Article Community | IDN Times`, tetapi setelah page settle accessibility snapshot kembali menunjukkan public Community shell dengan tombol `Masuk/Daftar`. Endpoint layout authenticated yang teramati mengembalikan `401`. Route page/RSC dengan status `200` hanya membuktikan page shell dapat diambil; status tersebut tidak membuktikan permission atau authenticated editor.

Karena authentication tidak terverifikasi, reconnaissance berhenti pada `CHECK_SESSION`. Current editor, metadata controls, body editor, cover workflow, inline image workflow, image metadata, draft persistence, preview, submission control, dan current moderation UI tidak dapat diamati. Semua item tersebut dicatat sebagai **UNKNOWN**, bukan diisi berdasarkan tutorial historical atau tebakan DOM.

> **Jawaban required untuk ordering:** Apakah ArticlePilot dapat secara deterministik mereproduksi `TEXT → IMAGE → TEXT → IMAGE → TEXT` pada current IDN Times editor? **UNKNOWN.** Editor authenticated dan perilaku insertion belum dapat diamati.

## 2. Security and safety boundary

| Boundary | Result |
|---|---|
| Session source | `set_cookies.txt` dari project runtime; tidak disalin ke repository. |
| Cookie exposure | Tidak ada cookie value atau nama cookie dalam report, logs yang dikomit, atau source code. |
| Credential entry | Tidak dilakukan. |
| Login automation | Tidak dilakukan. |
| CAPTCHA/MFA/OTP | Tidak muncul pada probe terbatas; tidak diisi atau dibypass. |
| Anti-bot/security bypass | Tidak dilakukan. |
| Article mutation | Tidak ada click editorial, form fill, upload, draft save, submit, logout manual, atau publication. |
| Production code | Tidak ada perubahan production code. |

Reconnaissance berhenti ketika session evidence tidak memenuhi authenticated requirement. Judul halaman atau HTTP `200` tidak diperlakukan sebagai bukti authentication.

## 3. Runtime and session method

Browser Firefox Playwright berhasil diinstal untuk connector Playwright. File sesi memiliki empat baris; tiga baris berupa assignment `document.cookie` dan satu baris lain tidak diproses. Assignment diterapkan hanya di konteks browser dan hanya untuk domain IDN yang relevan. Parser tidak mengembalikan nilai cookie.

Probe dilakukan secara pasif terhadap public entry point dan dashboard routes. Accessibility snapshot, URL/title, serta daftar request non-static digunakan untuk menentukan session result. Request body, response body privat, authentication header, dan unrelated credentials tidak diambil.

Evidence pendukung yang disanitasi tersimpan pada [`docs/idn-times-recon-task08-observations.md`](idn-times-recon-task08-observations.md).

## 4. Current entry point

| Observation | Classification | Evidence |
|---|---|---|
| `https://community.idntimes.com/` dapat dibuka | OBSERVED | Navigasi Playwright berhasil. |
| Public title: `IDN Times Community - Tulis Artikelmu & Jadilah Penulis Populer \| IDN Times` | OBSERVED | Page title saat public entry point dibuka. |
| Public shell menampilkan accessible name `login` dan label `Masuk/Daftar` | OBSERVED | Accessibility snapshot. |
| `/dashboard/` dinormalisasi ke `/dashboard` | OBSERVED | URL setelah navigasi. |
| Initial title `/dashboard`: `Manage Article Community \| IDN Times` | OBSERVED | Title pada initial document load. |
| Setelah settle dashboard kembali ke public Community shell | OBSERVED | Accessibility snapshot setelah page settle. |
| Current contributor editor dapat dibuka | UNKNOWN | Authenticated state tidak terverifikasi. |
| Web editor dan IDN App editor adalah surface yang sama | UNKNOWN | Tidak dapat dibandingkan tanpa authenticated access. |
| Redirect antar domain contributor yang aktif | UNKNOWN | Tidak ada authenticated editor transition yang dapat diamati. |

**Current entry point yang dapat dipastikan:** public `community.idntimes.com`; entry point authenticated contributor **belum terverifikasi**.

## 5. Network and technical evidence

| Endpoint pattern | Observed result | Classification | Safe interpretation |
|---|---:|---|---|
| `/api/community/layout?publisher=idntimes&platform=desktop&slug=dashboard-notifications` | `401` pada beberapa observasi | OBSERVED | Layout API authenticated belum menerima session yang valid. |
| `/dashboard/manage-article?_rsc=...` | `200` pada beberapa observasi | OBSERVED | Page/RSC shell dapat diambil; permission dan editor content UNKNOWN. |
| `/dashboard/events?_rsc=...` | `200` pada beberapa observasi | OBSERVED | Page/RSC shell dapat diambil; authenticated content UNKNOWN. |
| `/dashboard/guidelines?_rsc=...` | `200` pada beberapa observasi | OBSERVED | Page/RSC shell dapat diambil; authenticated content UNKNOWN. |
| `/api/auth/logout` | `200` dan `NS_BINDING_ABORTED` terlihat pada trace | OBSERVED | Network event terlihat; penyebab/pemicunya tidak ditentukan dan endpoint tidak diklik manual. |

Tidak ada DOM editor, accessible name editor, `contenteditable`, `iframe`, file input editor, dialog upload, stable test ID, atau selector editor yang berhasil diamati. Page shell route bukan selector evidence.

### Selector classification

| Candidate | Classification | Reason |
|---|---|---|
| Public label `Masuk/Daftar` | POTENTIALLY-STABLE untuk public login detection saja | Terlihat pada accessibility snapshot, tetapi belum dijadikan production selector. |
| Title `Manage Article Community \| IDN Times` | UNKNOWN sebagai auth marker | Title terlihat sementara lalu shell kembali public. |
| `/dashboard/manage-article` route | UNKNOWN sebagai editor entry point | Route shell `200` tidak membuktikan permission atau usable editor. |
| RSC request parameters | DYNAMIC | Mengandung request-specific values dan bukan semantic editor contract. |
| Editor controls | UNKNOWN | Belum ada authenticated DOM evidence. |

Tidak ada selector yang dipromosikan ke `automation:selectors` atau production code.

## 6. Authenticated editor findings

| Area requested by Task 08 | Classification | Observation |
|---|---|---|
| Editor architecture: form/textarea/contenteditable/block/rich-text/iframe/SPA | UNKNOWN | Authenticated editor tidak terbuka. |
| Title control | UNKNOWN | Tidak dapat menginspeksi label, type, placeholder, required state, limit, atau persistence. |
| Excerpt control | UNKNOWN | Tidak dapat diinspeksi. |
| Category control | UNKNOWN | Tidak dapat diinspeksi. |
| Tags control | UNKNOWN | Tidak dapat diinspeksi. |
| Cover control | UNKNOWN | Tidak dapat diinspeksi. |
| Other metadata | UNKNOWN | Tidak dapat diinspeksi. |
| Paragraph creation | UNKNOWN | Tidak ada editor body. |
| Heading/subheading | UNKNOWN | Tidak ada editor body. |
| Line breaks/formatting/links/embeds | UNKNOWN | Tidak ada editor body. |
| Section creation | UNKNOWN | Tidak ada editor body. |
| Block ordering | UNKNOWN | Tidak ada editor body. |

Historical official documentation tetap dicatat sebagai `DOCUMENTED` pada `docs/idn-times-publishing-research.md`, tetapi tidak dipromosikan menjadi current observation.

## 7. Cover and inline image workflow

Cover upload dan inline image upload tidak diuji karena session authenticated tidak terverifikasi dan Task melarang improvisasi bypass. Tidak ada synthetic article atau synthetic image yang diunggah.

| Required behavior | Classification | Reason |
|---|---|---|
| Cover upload trigger | UNKNOWN | Editor tidak tersedia. |
| File picker/file input | UNKNOWN | Tidak ada upload control yang dapat diamati. |
| Accepted formats/size/dimensions shown by current UI | UNKNOWN | Published tutorial adalah historical/documented evidence, bukan current UI evidence. |
| Upload progress/success/preview | UNKNOWN | Tidak diuji. |
| Cover replace/delete | UNKNOWN | Tidak diuji. |
| Cover save/reload persistence | UNKNOWN | Tidak ada draft. |
| Inline upload trigger | UNKNOWN | Editor tidak tersedia. |
| Upload separate from insertion | UNKNOWN | Tidak dapat diamati. |
| Image block versus inline content | UNKNOWN | Tidak dapat diamati. |
| Cursor/active block insertion semantics | UNKNOWN | Tidak dapat diamati. |
| Section requirement | UNKNOWN | Tidak dapat diamati. |
| Multiple/repeated upload | UNKNOWN | Tidak diuji. |
| Reuse/move/delete/replace image | UNKNOWN | Tidak diuji. |
| Ordering persistence after save/reload | UNKNOWN | Tidak ada draft. |

### Deterministic ordering answer

**UNKNOWN.** Tidak ada evidence yang dapat membuktikan apakah current editor mendukung atau menolak reproduksi deterministik `TEXT → IMAGE → TEXT → IMAGE → TEXT`. ArticlePilot tidak boleh menganggap ordered `ArticleBlock` otomatis dapat dipetakan ke editor sebelum authenticated interaction dan read-back structure berhasil diuji.

## 8. Image metadata

Tidak ada image selection atau upload sehingga tidak ada field current yang dapat diobservasi.

| Field | Classification | Current UI evidence |
|---|---|---|
| Caption | UNKNOWN | Tidak tersedia tanpa editor. |
| Description | UNKNOWN | Tidak tersedia tanpa editor. |
| Source | UNKNOWN | Tidak tersedia tanpa editor. |
| Source name | UNKNOWN | Tidak tersedia tanpa editor. |
| Source URL | UNKNOWN | Tidak tersedia tanpa editor. |
| Credit | UNKNOWN | Tidak tersedia tanpa editor. |
| Alt text | UNKNOWN | Tidak tersedia tanpa editor. |
| Copyright/attribution | UNKNOWN | Tidak tersedia tanpa editor. |
| Requiredness, association, persistence | UNKNOWN | Tidak dapat diuji. |

Tutorial resmi yang menyebut source, caption/description, source name, dan source URL tetap berstatus `DOCUMENTED` historical pada riset Task 07; tutorial tersebut bukan bukti field current atau semantic equivalence.

## 9. Draft persistence and preview

Tidak ada draft yang dibuat atau disimpan. Karena itu, persistence tidak boleh disimpulkan dari page shell.

| Behavior | Classification |
|---|---|
| Manual save action | UNKNOWN |
| Autosave | UNKNOWN |
| Save indicator | UNKNOWN |
| Draft status in current UI | UNKNOWN |
| Persistence after reload | UNKNOWN |
| Persistence after leaving/reopening | UNKNOWN |
| Uploaded image persistence | UNKNOWN |
| Block-order persistence | UNKNOWN |
| Metadata persistence | UNKNOWN |
| Preview availability | UNKNOWN |
| Preview route/modal/new tab | UNKNOWN |
| Preview saved-state semantics | UNKNOWN |

Official support documentation establishes documented moderation states, including Draft, Dimoderasi, Revisi, Terbit, and Ditolak, tetapi current authenticated status UI tidak terobservasi pada Task 08.

## 10. Submission and moderation/status UI

Submission control tidak diinspeksi karena editor tidak tersedia. Tidak ada article submission dan tidak ada confirmation dialog yang dibuka.

| Requested item | Classification |
|---|---|
| Exact submit button label | UNKNOWN |
| Enabled/disabled state | UNKNOWN |
| Pre-submit validation | UNKNOWN |
| Warning message | UNKNOWN |
| Confirmation dialog | UNKNOWN |
| Submit destination/transition without submitting | UNKNOWN |
| Current Draft UI | UNKNOWN |
| Current Dimoderasi UI | UNKNOWN |
| Current Revisi UI | UNKNOWN |
| Current Terbit UI | UNKNOWN |
| Current Ditolak UI | UNKNOWN |

Remote platform status harus tetap dipisahkan dari automation execution state. Official documentation is `DOCUMENTED`; current UI remains `UNKNOWN`.

## 11. ArticlePilot mapping

| ArticlePilot concept | Current IDN Times UI | Evidence | Confidence | Automation implication |
|---|---|---|---|---|
| `title` | UNKNOWN | No authenticated editor. | UNKNOWN | Do not select or fill. |
| `excerpt` | UNKNOWN | No authenticated editor. | UNKNOWN | Do not select or fill. |
| `category` | UNKNOWN | No authenticated editor. | UNKNOWN | Do not select or fill. |
| `tags` | UNKNOWN | No authenticated editor. | UNKNOWN | Keep optional and profile-dependent. |
| `cover` | UNKNOWN | No upload control. | UNKNOWN | Do not upload. |
| `section` | UNKNOWN | No body editor. | UNKNOWN | Do not create. |
| `heading` | UNKNOWN | No body editor. | UNKNOWN | Do not create. |
| `paragraph` | UNKNOWN | No body editor. | UNKNOWN | Do not write. |
| `inline image` | UNKNOWN | No body editor or image control. | UNKNOWN | Do not attempt ordering. |
| `caption` | UNKNOWN | No selected image. | UNKNOWN | Preserve domain field; UI mapping pending. |
| `source` | UNKNOWN | No selected image. | UNKNOWN | Preserve provenance; UI mapping pending. |
| `source URL` | UNKNOWN | No selected image. | UNKNOWN | Preserve source/download separation. |
| `image ordering` | UNKNOWN | No synthetic article. | UNKNOWN | Require read-back evidence before automation. |
| `draft` | UNKNOWN current UI; DOCUMENTED historical/support concept | No draft created; official docs describe concept. | DOCUMENTED only for platform concept | Separate remote status from local checkpoint. |
| `preview` | UNKNOWN | No editor. | UNKNOWN | Do not implement preview selector. |
| `submit` | UNKNOWN | No editor and no submit action. | UNKNOWN | Manual confirmation required in future. |
| `moderation status` | UNKNOWN current UI; DOCUMENTED official states | Support FAQ documents states, not current DOM. | DOCUMENTED for concept, UNKNOWN for UI | Persist external status separately. |

## 12. Automation boundary

The boundary below is based on current evidence, not on the fact that a human might be able to perform the operation manually.

| Operation | Classification | Reason |
|---|---|---|
| LOGIN | PAUSE / USER TAKEOVER | Session not confirmed; ArticlePilot must not fill credentials. |
| SECURITY CHALLENGE | PAUSE / USER TAKEOVER | No bypass; stop if challenge appears. |
| OPEN EDITOR | UNKNOWN | Public route shell is not authenticated editor evidence. |
| TITLE | UNKNOWN | No target or success read-back. |
| EXCERPT | UNKNOWN | No target or success read-back. |
| CATEGORY | UNKNOWN | No target or success read-back. |
| TAGS | UNKNOWN | No target or success read-back. |
| COVER | UNKNOWN | No upload target or success evidence. |
| SECTION | UNKNOWN | No editor target or success evidence. |
| HEADING | UNKNOWN | No editor target or success evidence. |
| PARAGRAPH | UNKNOWN | No editor target or success evidence. |
| IMAGE UPLOAD | UNKNOWN | No upload target or success evidence. |
| IMAGE INSERTION | UNKNOWN | No insertion semantics evidence. |
| IMAGE METADATA | UNKNOWN | No selected image or field evidence. |
| REORDER | UNKNOWN | No current behavior evidence; destructive/idempotency risk. |
| DELETE | UNKNOWN | No current behavior evidence; destructive action. |
| SAVE DRAFT | UNKNOWN | No save control or persistence evidence. |
| PREVIEW | UNKNOWN | No preview control evidence. |
| SUBMIT | PAUSE / USER TAKEOVER | Never submit reconnaissance article; current target unknown. |
| STATUS VERIFICATION | UNKNOWN | Current status UI not accessible; official concepts are documented separately. |

## 13. Evidence-based future state machine

Only the following transition is supported by current Task 08 evidence:

```text
OPEN_PUBLIC_ENTRY_POINT
        ↓
CHECK_SESSION
        ├─ authenticated evidence present → NOT OBSERVED; STOP FOR REVIEW
        ├─ public shell / login marker     → PAUSE_FOR_MANUAL_TAKEOVER
        └─ security challenge              → SECURITY_CHALLENGE_OBSERVED; STOP
```

| State | Entry condition | Action | Expected evidence | Failure mode | Recovery/manual takeover |
|---|---|---|---|---|---|
| `OPEN_PUBLIC_ENTRY_POINT` | No production operation; browser context available. | Open approved public Community URL. | URL/title/accessibility snapshot. | Network failure or redirect. | Reopen once under bounded policy; otherwise stop. |
| `CHECK_SESSION` | Page loaded. | Read only public/auth markers and safe status evidence. | Authenticated contributor marker, not merely title/HTTP 200. | Public `Masuk/Daftar`, API `401`, expired/invalid session. | `PAUSE_FOR_MANUAL_TAKEOVER`; no credential entry by ArticlePilot. |
| `PAUSE_FOR_MANUAL_TAKEOVER` | Auth evidence absent or ambiguous. | Stop all editor actions and request user-controlled action. | User later provides verified authenticated page state. | User cannot authenticate or challenge appears. | Keep session paused; do not bypass. |
| `SECURITY_CHALLENGE_OBSERVED` | CAPTCHA/MFA/OTP/suspicious-login appears. | Stop immediately. | Challenge marker recorded without secret. | Any attempt to bypass would violate boundary. | User handles challenge manually; restart reconnaissance only after safe verification. |

Downstream states such as `OPEN_EDITOR`, `FILL_METADATA`, `UPLOAD_COVER`, `BUILD_BODY`, `UPLOAD_INLINE_IMAGE`, `SAVE_DRAFT`, `SUBMIT`, and `VERIFY_STATUS` remain **NOT OBSERVED / NOT ENABLED**. They must not be promoted from the historical research state machine until current authenticated evidence exists.

Automation execution completion must remain distinct from remote platform status. Even future `AUTOMATION_COMPLETE` would not imply `PUBLISHED`; only verified remote `Terbit` evidence or a verified public article identity can establish publication.

## 14. ArticlePilot gap analysis

| Gap | Relevance after Task 08 | Recommendation |
|---|---|---|
| Missing article fields | Current field controls were not observed; domain fields may not map one-to-one. | Keep domain model stable; verify current field taxonomy before changes. |
| Missing block types | Rich text/embed/section semantics unknown. | Do not add speculative block types. |
| Missing media metadata | Current caption/source/alt/copyright semantics unknown. | Preserve provenance fields; add platform mapping only after evidence. |
| Missing remote media identity | No upload result observed. | Add platform upload identity in Browser/Automation Core later, not Media Core. |
| Missing publication identity | No submission/public URL observed. | Add remote article identity and evidence object to publishing session. |
| Missing revision state | Current UI not observed; official docs document Revisi. | Keep remote status separate and persist revision reason when observed. |
| Missing rejection reason | Current UI not observed; official docs document Ditolak. | Preserve platform message as structured evidence, not generic failure. |
| Missing durable checkpoints | No live draft operation was performed. | Room/WorkManager remains deferred until idempotency semantics are observed. |
| Missing evidence objects | Repository contracts need sanitized DOM/status evidence for future verification. | Add evidence model only after evidence schema and retention policy are approved. |
| Missing user confirmation states | No submit/confirmation UI observed. | Require explicit user confirmation before any future submission. |

No architecture redesign is recommended from this unsuccessful authentication probe. Existing separation between core model, Media Core, Browser Core, Automation Core, manual takeover, and remote publication status remains appropriate.

## 15. Unknown findings register

The following remain explicitly `UNKNOWN`: authenticated editor architecture; exact current contributor entry point after login; title/excerpt/category/tags/cover controls; requiredness and character limits; body editor type; heading, paragraph, line-break, formatting, link, and embed behavior; section creation; cover upload; inline image upload; file picker; image block versus inline content; cursor/active-block insertion; exact `TEXT → IMAGE → TEXT → IMAGE → TEXT` ordering; image move/delete/replace/reuse; caption/description/source/source name/source URL/credit/alt/copyright fields; draft save/autosave/reload persistence; preview; submit control and warning; current moderation/status UI; DOM semantics; stable selectors; rate limits; and authenticated domain/session lifetime.

Historical documentation must not silently override these unknowns. If a future current observation conflicts with historical documentation, current sanitized evidence receives priority and the historical claim remains labeled `DOCUMENTED` or `HISTORICAL`.

## 16. Recommended next task

Repeat controlled reconnaissance with a Playwright session that produces a verifiable authenticated contributor marker. If the user must authenticate manually, use browser takeover only on the already opened IDN page; ArticlePilot must not receive or store credentials. After authentication is visibly confirmed, capture only synthetic article evidence in this order: editor surface, metadata, section/body, cover, inline image ordering, image metadata, draft persistence, preview, and non-submitting submission control.

The first acceptance checkpoint should be a sanitized authenticated editor snapshot. Only after that snapshot is reviewed should a versioned `PublishingProfile`, local HTML fixtures, and selector candidates be designed. Do not implement production Browser Automation or perform live submission before review.

## References

[1]: ./idn-times-publishing-research.md "ArticlePilot Task 07 IDN Times publishing research"

[2]: ./idn-times-recon-task08-observations.md "Sanitized Task 08 Playwright observations"

[3]: https://community.idntimes.com/ "IDN Times Community public entry point"

[4]: https://idnmediasupport.zendesk.com/hc/en-us/articles/18332891131929--FAQ-Community-Bagaimana-Cara-Menjadi-Community-Writer-di-IDN-Times-Community "Official Community Writer FAQ"

[5]: https://idnmediasupport.zendesk.com/hc/en-us/articles/18333213618329--FAQ-Community-Apa-Saja-Proses-Yang-Terjadi-Pada-Tulisan-Saya "Official article process FAQ"

[6]: https://idnmediasupport.zendesk.com/hc/en-us/articles/18333554936601--FAQ-Community-Kapan-Artikel-Saya-Terbit "Official publication timing FAQ"

[7]: https://github.com/annasqs-15/ArticlePilot "ArticlePilot repository"
