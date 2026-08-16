# ArticlePilot

ArticlePilot adalah aplikasi Android yang menjadi **translator dan execution engine** untuk menerbitkan artikel terstruktur ke IDN Times melalui browser automation. AI atau alat eksternal tetap bertanggung jawab menghasilkan konten dalam format ArticlePilot; aplikasi ini bertanggung jawab mempertahankan struktur, memvalidasi konten, menyiapkan media, menampilkan preview, menjalankan otomasi yang dapat diverifikasi, menyimpan progres, dan berhenti aman ketika keadaan browser tidak dapat dipastikan.

> ArticlePilot tidak menyertakan AI writer. Fondasi ini juga tidak mengklaim bahwa browser automation IDN Times telah berfungsi.

## Status proyek

Repositori ini adalah **fondasi produksi bertahap**, bukan MVP atau demo throwaway. Model domain, parser Article Format v1.0, generic validation engine, Media Core image pipeline JVM, Article Workspace Android, kontrak parser/validator/media/browser/automation, dokumentasi, dan pengujian deterministik telah dibuat. Fondasi Android WebView/session kini tersedia untuk membuka `https://community.idntimes.com/dashboard/create-article`, membatasi origin, melakukan inspeksi sanitized, dan menampilkan login/manual takeover state. Task 10 berhasil mengobservasi authenticated editor melalui cookie sesi yang diterapkan secara transient pada browser riset terisolasi; struktur editor dan kontrol utama didokumentasikan, tetapi tidak ada article mutation, upload, save, atau submit. Hasil lengkapnya ada di [`docs/idn-times-editor-recon-v1.md`](docs/idn-times-editor-recon-v1.md), dengan fixture offline di [`fixtures/idn-times/editor-v1-authenticated.html`](fixtures/idn-times/editor-v1-authenticated.html). Room schema, Android storage adapter, WorkManager orchestration, image compression, authenticated selector production, dan automation runner produksi sengaja belum diaktifkan karena stability, session bootstrap, dan read-back behavior belum diverifikasi.

| Area | Status tahap pertama |
| --- | --- |
| Android Article Workspace | Import/paste, parse, validation review, ordered article review, image processing, local preview, retry/cancel, dan strict local READY tersedia |
| Core article model | Tersedia dan extensible |
| Parser | Parser pure Kotlin Article Format v1.0 dan fixture-driven tests tersedia |
| Validation | Generic Article Validation Engine, policy requirements, severity, dan deterministic diagnostics tersedia |
| Image pipeline | Controlled downloader, temporary/ready storage, MIME/dimension inspection, validator, retry, state, cleanup, pipeline tests, dan integrasi workspace tersedia |
| Persistence | Boundary draft/revision/session/log tersedia; Room schema belum disetujui |
| Browser/WebView | Android WebView host/session, origin policy, sanitized inspection, manual-login fallback, dan UI state tersedia; Task 10 mengobservasi authenticated editor dan membuat fixture riset, tetapi belum mengaktifkan selector atau automasi produksi |
| Automation state/recovery | Checkpoint, state, recovery contracts, dan manual takeover boundary tersedia |
| IDN Times profile/selectors | Entry-point/origin profile tersedia; authenticated/editor markers dan selectors tetap kosong sampai current evidence direview |
| CI | Workflow build/test/lint disiapkan |

## Arsitektur ringkas

```text
app (Compose UI / composition root)
 ├── core:model       (Article, ImageAsset, drafts, publishing session)
 ├── core:parser      (versioned parser contracts and diagnostics)
 ├── core:validator   (generic and platform validation policies)
 ├── core:database    (Room boundary and local persistence contracts)
 ├── media:*          (download, processing, validation contracts)
 ├── browser:*        (session, WebView adapter, JS bridge)
 └── automation:*     (state, engine, profiles, selectors, recovery)

browser:webview menggunakan Android WebView sebagai adapter lifecycle. `browser:session` tetap platform-neutral untuk classifier dan origin policy. `app/browser` menerjemahkan `BrowserSessionState` ke UI tanpa menjalankan DOM atau JavaScript dari Compose.
```

Dependency direction dijaga dari domain ke adapter. Model artikel tidak mengetahui DOM, WebView, selector, atau kredensial. Profil platform adalah tempat bagi aturan IDN Times yang dapat berubah. Automation state machine bekerja dengan checkpoint dan bukti, bukan koordinat layar atau asumsi bahwa dispatch aksi berarti aksi berhasil.

## Setup pengembangan

Gunakan JDK 21 atau kompatibel dengan Android Gradle Plugin yang dikunci di root build. Android SDK dengan Android API 35 dan Build Tools yang sesuai diperlukan untuk modul aplikasi serta modul Android library. Setelah SDK tersedia, jalankan pemeriksaan berikut dari root repository:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Untuk IDE, buka root repository sebagai proyek Gradle. Jangan menyimpan `local.properties`, credential, cookie, atau data sesi di repository. Kredensial IDN Times tidak dirancang untuk dikirim ke backend ArticlePilot.

## Struktur repository

| Direktori | Tanggung jawab |
| --- | --- |
| `app/` | Android Article Workspace, Compose UI, immutable presentation state, ViewModel/application orchestration, dan composition root |
| `core/model/` | Model artikel dan asset yang serializable serta extensible |
| `core/parser/` | Parser v1.0, kontrak versioned, diagnostics, dan fixture-driven tests |
| `core/validator/` | Validation policy, diagnostics, dan severity |
| `core/database/` | Boundary persistence lokal berbasis Room |
| `media/` | Storage, controlled downloader, byte/image inspection, validator, processor pipeline, dan media tests |
| `browser/` | Browser session contract/classifier, Android WebView host/session, dan safe inspection bridge |
| `automation/` | State machine, runner, profile, selector, dan recovery |
| `docs/` | Keputusan arsitektur dan spesifikasi evolutif |
| `.github/workflows/` | Build, test, dan lint pada pull request/push |

## Prinsip engineering

Implementasi production berikutnya harus mempertahankan beberapa batas. Kegagalan download atau upload harus menjadi hasil eksplisit yang terlihat pengguna. Aksi browser hanya boleh dianggap berhasil setelah evidence dari DOM atau state halaman cocok dengan kondisi sukses. CAPTCHA, anti-bot challenge, dan mekanisme keamanan autentikasi tidak boleh dibypass. Ketika selector atau state tidak dapat dikenali, sistem harus pause dan menawarkan manual takeover dengan verifikasi resume.

## Langkah implementasi berikutnya

Langkah berikut yang paling tepat adalah melakukan controlled offline/editor-contract validation pada fixture sanitized dan, bila diperlukan, mutation terbatas pada akun dummy melalui device/emulator yang benar-benar dapat dikendalikan pengguna. Task 10 telah menghasilkan authenticated editor snapshot dan fixture, tetapi belum memverifikasi inline-image ordering, draft persistence, session bootstrap Android, atau selector stability. Tambahkan read-back tests, profile version, dan selector research catalog hanya setelah evidence tersebut tersedia. Jangan mengaktifkan mutation bridge atau publishing automation sebelum editor structure, session semantics, dan recovery behavior benar-benar diverifikasi.

## Referensi teknis

[1]: https://developer.android.com/jetpack/compose "Jetpack Compose documentation"
[2]: https://developer.android.com/training/data-storage/room "Room persistence library documentation"
[3]: https://developer.android.com/topic/libraries/architecture/workmanager "WorkManager documentation"
[4]: https://developer.android.com/develop/ui/views/layout/webapps/webview "Android WebView documentation"
[5]: https://docs.gradle.org/current/userguide/gradle_wrapper.html "Gradle Wrapper documentation"
