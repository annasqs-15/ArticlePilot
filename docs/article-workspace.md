# ArticlePilot Article Workspace

## Tujuan dan batas

Article Workspace adalah application feature layer pertama yang menghubungkan input dokumen ArticlePilot dengan parser, generic validator, Media Core, dan review lokal. Workspace berakhir pada keadaan **artikel telah disiapkan secara lokal untuk publishing pipeline masa depan**. Workspace tidak membuka IDN Times, tidak mengakses WebView, tidak melakukan login, dan tidak mengunggah atau menerbitkan artikel.

Raw ArticlePilot source selalu dipertahankan selama workspace hidup. Source tidak dinormalisasi, diubah diam-diam, atau digantikan oleh hasil parser. Article terstruktur hanya menjadi representasi turunan yang dapat dibuat ulang melalui parse ulang.

## Layer dan dependency direction

```text
Compose UI
    |
    v
ArticleWorkspaceViewModel
    |
    v
Application/use-case contracts
    |                    \
    v                     v
core:parser          core:validator
    |                    |
    +---------> core:model

ArticleWorkspaceViewModel
    |
    v
WorkspaceMediaProcessor
    |
    v
MediaPipeline -> downloader / inspection / validator / storage
```

Composable tidak menjalankan parser, validator, network request, image decode, atau filesystem operation. `ArticleWorkspaceViewModel` mengorkestrasi operasi dan mengekspos immutable `StateFlow<ArticleWorkspaceUiState>`. Dependency konkret dibuat di composition root Android; unit test dapat menggantinya dengan deterministic fakes.

Kode feature disimpan di package `com.articlepilot.app.workspace` pada modul `app` untuk task ini. Pemisahan package `application`, `state`, dan `ui` menjaga boundary tanpa membuat modul Gradle baru sebelum kontrak feature stabil. Jika workspace atau use case nantinya dipakai oleh lebih dari satu Android entry point, layer application dapat dipindahkan ke modul feature tanpa mengubah core modules.

## Workspace state model

`ArticleWorkspaceUiState` menyimpan raw source, parse diagnostics, optional structured article, validation result, media item state, dan operation message. `phase` hanya diubah oleh ViewModel melalui transition helper; UI tidak dapat menciptakan state secara langsung.

| Phase | Kondisi | Action yang tersedia |
| --- | --- | --- |
| `EMPTY` | Belum ada source atau source kosong | Paste, input, parse |
| `IMPORTING` | Source sedang diproses secara asynchronous | Clear; parse operation berjalan |
| `INVALID` | Parse gagal atau blocking parse diagnostics tersedia | Edit source, parse ulang, clear |
| `PARSED` | Parse sukses dan article tersedia; review dapat dilakukan | Process media bila validation tidak blocking |
| `PROCESSING_MEDIA` | Satu atau lebih asset sedang diproses Media Core | Cancel |
| `FAILED` | Operasi media atau application gagal dan error terlihat | Retry failed media, parse ulang, clear |
| `CANCELLED` | Processing dihentikan pengguna; source/article tetap dipertahankan | Retry media, clear, parse ulang |
| `READY` | Parse sukses, generic validation tidak memiliki `ERROR`, dan setiap required image telah berhasil dipromosikan Media Core | Review, clear, future handoff |

Invariant utama adalah `READY` tidak boleh hidup bersama parse diagnostic, blocking validation issue, media failure, atau image yang belum `READY`. `READY` juga tidak berarti published; label UI harus menyatakan bahwa artikel baru siap untuk future publishing pipeline.

Untuk menghindari hilangnya context, parse failure hanya mengganti `parseDiagnostics` dan phase menjadi `INVALID`; raw source tetap tersimpan. Media failure hanya mengubah item media terkait dan phase menjadi `FAILED`; article dan source tetap tersedia untuk retry.

## Import dan parser integration

Import menerima seluruh text input tanpa modifikasi otomatis. Tombol **Paste** membaca clipboard hanya setelah aksi eksplisit pengguna melalui Android clipboard API. Clipboard tidak dibaca pada startup, tidak dikirim keluar perangkat, dan tidak ditulis ke log.

Tombol **Parse** menjalankan `ArticleFormatV1Parser` pada coroutine worker. `ParseResult.Failure` dipetakan satu-per-satu ke `ParseDiagnostic` yang menampilkan code, message, line, column, dan path jika tersedia. Tidak ada penggantian diagnostics menjadi pesan generik tunggal. `ParseResult.Success` menyimpan `Article` dan membangun daftar media dari cover lalu image blocks sesuai urutan dokumen.

## Validator integration

Setelah parse sukses, ViewModel menjalankan `ArticleValidationEngine` dengan `GenericArticleValidationPolicy`. Generic validator memeriksa struktur dan metadata yang memang menjadi tanggung jawab core. Workspace tidak menambahkan aturan IDN Times-specific atau menebak requirement platform.

Validation issues dipertahankan dalam urutan deterministic dan ditampilkan berdasarkan `ERROR`, `WARNING`, dan `INFO`. Hanya `ERROR` yang blocking. Setelah Media Core menghasilkan fakta file, asset pada article diperbarui dan validation dijalankan kembali sebelum state dapat menjadi `READY`.

## Media integration

Workspace memproses asset satu per satu melalui `MediaPipeline`. Caller mempertahankan asosiasi `ImageAssetId` dengan cover atau block asal. Observer pipeline memetakan snapshot ke item media UI sehingga state download, inspection, validation, failure, cancellation, dan ready terlihat pada image yang benar.

Media state UI membedakan metadata deklaratif dari fakta hasil download. Metadata mencakup download URL, source URL, source name, credit, dan caption. Fakta hasil Media Core mencakup MIME, width, height, file size, local file reference, dan validation status.

Tombol **Process media** hanya berjalan ketika article telah berhasil diparse dan tidak memiliki blocking generic validation error. Processing memakai coroutine worker, tidak menjalankan network atau decode di main thread, dan dapat dibatalkan. Tombol **Retry failed media** memproses kembali asset yang belum ready tanpa membuang source atau article model. Pipeline tidak menganggap dispatch action sebagai sukses; item hanya menjadi `READY` setelah `MediaPipelineResult.Ready` mengembalikan file yang dipromosikan.

## Preview lokal

Review article mempertahankan urutan `TextBlock` dan `ImageBlock` sebagaimana terdapat pada `Article.sections[].blocks`. Cover ditampilkan terpisah sebagai cover, sedangkan content images ditampilkan inline pada posisi block-nya.

Jika `localFileReference` tersedia dan file masih ada, UI mencoba membaca file lokal untuk preview. URL remote tidak digunakan sebagai pengganti preview lokal yang tervalidasi. File yang belum siap atau gagal didecode menampilkan placeholder status dan pesan yang dapat dipahami; kegagalan decode tidak boleh menyebabkan Composable crash.

## Error handling

Error dibedakan menjadi empat kategori agar recovery dapat diarahkan dengan benar:

1. **Parser error**: source tetap tersedia dan pengguna memperbaiki text berdasarkan diagnostics.
2. **Article validation error**: article tetap tersedia, issue ditampilkan per severity/path, dan processing media diblokir hanya bila ada `ERROR`.
3. **Media processing error**: asset terkait diberi failure state dan retry; kegagalan tidak disembunyikan.
4. **Cancellation**: processing berhenti secara eksplisit, Media Core menjalankan cleanup sesuai policy, dan source/article tetap dapat dilanjutkan.

Unexpected application exception menjadi `FAILED` dengan pesan yang terlihat dan tanpa klaim bahwa artikel siap. Logging task ini tidak menyimpan credential, clipboard content, atau raw source secara eksternal.

## Persistence dan recovery masa depan

Task ini menggunakan state session-scoped pada ViewModel dan tidak memperkenalkan Room schema besar. Raw source dan article belum durable setelah process death. Draft persistence, revision history, media facts, retry queue, dan durable checkpoint akan ditambahkan setelah workspace contract terbukti dan retention/cleanup policy disetujui.

Android app-private storage digunakan oleh composition root melalui adapter filesystem yang tersedia saat ini. WorkManager dan Android-specific storage lifecycle belum menjadi bagian dari task ini; keduanya adalah follow-up untuk membuat processing dapat resume setelah app termination.

## Future browser publishing integration

Browser publishing sengaja tetap berada di luar workspace. Handoff masa depan hanya boleh menerima article yang telah memenuhi invariant `READY` dan local media references yang masih tervalidasi. Handoff tersebut nantinya menjadi boundary baru dengan WebView session, semantic DOM evidence, selector profile ber-version, pause/manual takeover, dan verification; tidak ada bagian dari Article Workspace yang boleh bergantung pada selector IDN Times.

## Test strategy

Test workspace memakai parser/validator/media fakes deterministic untuk memeriksa state dan error transitions tanpa live URL, live IDN Times, atau clipboard global. Coverage mencakup empty input, parser diagnostics, title/excerpt/tags, section/block ordering, media success/failure/cancellation/retry, validation severity, dan invariant bahwa `READY` hanya muncul setelah seluruh prerequisite terpenuhi. Compose semantics diberi label yang stabil agar integration test dapat memeriksa error dan ordering tanpa koordinat layar.
