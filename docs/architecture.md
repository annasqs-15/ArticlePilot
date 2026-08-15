# ArticlePilot Architecture

## Tujuan

ArticlePilot dirancang sebagai aplikasi Android lokal yang menerjemahkan dokumen artikel terstruktur menjadi proses publishing yang dapat diverifikasi. Arsitektur ini memisahkan domain article dari media, browser, automation, persistence, dan UI agar perubahan pada sintaks artikel atau DOM IDN Times tidak memaksa penulisan ulang model internal.

## Batas modul

| Layer | Modul | Tanggung jawab utama | Tidak boleh mengetahui |
| --- | --- | --- | --- |
| Presentation/application feature | `app` | Compose entry point, Article Workspace ViewModel/use cases, dependency composition, navigasi UI | Detail selector, HTTP download, dan parser/media implementation di Composable |
| Domain | `core:model` | Article, section, block, image asset, draft, publishing session | Android View, WebView, IDN Times DOM |
| Translation | `core:parser` | Memetakan input versioned ke Article atau diagnostics | DOM platform |
| Rules | `core:validator` | `ArticleValidationEngine` menjalankan generic semantic rules, policy requirements, dan mengembalikan issues deterministik | Detail UI, parser source text, dan mekanisme download |
| Persistence | `core:database` | Adapter Room untuk draft, revision, session, log | Selector browser |
| Media | `media:*` | Download, decode/processing, validation, lifecycle file | Browser action |
| Browser | `browser:*` | WebView lifecycle, session observation, JS bridge | Article parsing rules |
| Automation | `automation:*` | State machine, profile, semantic selectors, retry/recovery | Raw Compose state |

Dependency direction mengalir dari adapter ke kontrak yang lebih stabil. `core:model` menjadi pusat data, sementara `app/workspace` mengorkestrasi session-scoped Article Workspace melalui ViewModel dan application contracts. `automation:profiles` tetap menggabungkan policy dan selector platform. Dengan demikian, IDN Times-specific logic tidak tersebar di parser, workspace UI, atau domain model.

## Keputusan penting

### Model internal tidak mengikuti sintaks input

Format artikel adalah boundary eksternal yang berversi. Parser mengembalikan `Article`, sedangkan format registry memilih parser berdasarkan version. Penambahan block type atau revisi sintaks harus menambah parser/translator dan migration policy, bukan mengubah automation DOM logic.

### Browser automation berbasis evidence

Sistem tidak menggunakan tap berdasarkan koordinat sebagai mekanisme utama. Perintah browser diwujudkan sebagai semantic selector dan verified action. Aksi dianggap berhasil hanya setelah inspeksi menunjukkan evidence yang diharapkan. `UnimplementedAutomationRunner` sengaja pause daripada membuat kesan bahwa workflow IDN Times sudah aman.

### Platform policy diisolasi

Aturan generik artikel dan aturan platform tidak dicampur. `ValidationPolicy` adalah kontrak; `ArticleValidationEngine` menjalankan aturan generic lalu hook policy secara deterministik. Profile platform nantinya menyuplai implementation ber-version, termasuk kebutuhan attribution dan fakta media yang harus tersedia. Selector catalog juga ber-version agar perubahan DOM dapat dikaji, diuji dengan fixture, dan diganti tanpa mengubah domain.

### Persistence lokal dan privacy

Draft, revision, image metadata, publishing session, checkpoint, dan log dirancang untuk disimpan lokal. Credential, cookie, dan file sementara tidak boleh masuk Git atau dikirim ke backend ArticlePilot. Room schema belum dibekukan dalam tahap ini karena perlu dipetakan terhadap kebutuhan migration dan cleanup lifecycle.

## Keputusan yang sengaja ditunda

| Keputusan | Alasan ditunda | Kriteria untuk melanjutkan |
| --- | --- | --- |
| Durable workspace persistence | Article Workspace menggunakan state session-scoped dan raw source tetap berada di ViewModel | Process-death restoration, draft retention, dan migration policy disetujui |
| Sintaks final ArticlePilot | Format v1.0 sudah dibekukan | Versi berikutnya memerlukan migration policy dan fixture baru |
| Room entities/DAOs | Skema harus mendukung revision, recovery, dan cleanup | Retention policy serta migration test tersedia |
| Android HTTP/storage integration | JVM Media Core sudah memiliki `JvmHttpTransport` dengan redirect, timeout, retry, size limit, dan temporary storage; adapter Android belum dipilih | App-private lifecycle, cancellation, WorkManager constraints, dan threat model ditetapkan |
| IDN Times selectors | DOM dan workflow aktual harus diverifikasi | Manual inspection dan selector fixtures tersedia |
| WebView bridge production | Bridge perlu lifecycle, origin, message validation, dan error contract | Threat model serta integration harness tersedia |
| WorkManager orchestration | Pipeline JVM belum memiliki worker Android untuk persisted resume | Retry constraints, foreground UX, dan checkpoint persistence ditetapkan |

## Struktur package

Package mengikuti modul, bukan screen atau vendor. `com.articlepilot.core.model` tidak mengimpor Android. Modul Android hanya digunakan ketika boundary memang membutuhkan WebView, Room runtime, atau Compose. Kontrak dapat diuji pada JVM sehingga sebagian besar test tidak memerlukan device maupun situs live.

## Referensi

[1]: https://developer.android.com/topic/architecture "Android app architecture guidance"
[2]: https://developer.android.com/training/data-storage/room "Room persistence library documentation"
[3]: https://developer.android.com/develop/ui/views/layout/webapps/webview "Android WebView documentation"
