# ArticlePilot

ArticlePilot adalah aplikasi Android yang menjadi **translator dan execution engine** untuk menerbitkan artikel terstruktur ke IDN Times melalui browser automation. AI atau alat eksternal tetap bertanggung jawab menghasilkan konten dalam format ArticlePilot; aplikasi ini bertanggung jawab mempertahankan struktur, memvalidasi konten, menyiapkan media, menampilkan preview, menjalankan otomasi yang dapat diverifikasi, menyimpan progres, dan berhenti aman ketika keadaan browser tidak dapat dipastikan.

> ArticlePilot tidak menyertakan AI writer. Fondasi ini juga tidak mengklaim bahwa browser automation IDN Times telah berfungsi.

## Status proyek

Repositori ini adalah **fondasi produksi tahap pertama**, bukan MVP atau demo throwaway. Model domain, boundary modul, kontrak parser/validator/media/browser/automation, struktur persistence, dokumentasi, dan pengujian kontrak telah dibuat. Implementasi parser sintaks final, Room schema, image pipeline nyata, WebView bridge produksi, selector IDN Times terverifikasi, dan automation runner produksi sengaja belum diaktifkan karena spesifikasi format serta DOM platform belum final atau belum diverifikasi.

| Area | Status tahap pertama |
| --- | --- |
| Android Compose application shell | Tersedia sebagai composition root minimal |
| Core article model | Tersedia dan extensible |
| Parser | Kontrak versioned tersedia; implementasi sintaks final belum aktif |
| Validation | Kontrak policy dan diagnostic tersedia |
| Image pipeline | Boundary downloader/validator/processor tersedia; pipeline nyata belum aktif |
| Persistence | Boundary draft/revision/session/log tersedia; Room schema belum disetujui |
| Browser/WebView | Adapter boundary tersedia; tidak ada fake automation |
| Automation state/recovery | Checkpoint, state, recovery contracts, dan manual takeover boundary tersedia |
| IDN Times profile/selectors | Belum dikonfigurasi; selector tidak boleh ditebak |
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
```

Dependency direction dijaga dari domain ke adapter. Model artikel tidak mengetahui DOM, WebView, selector, atau kredensial. Profil platform adalah tempat bagi aturan IDN Times yang dapat berubah. Automation state machine bekerja dengan checkpoint dan bukti, bukan koordinat layar atau asumsi bahwa dispatch aksi berarti aksi berhasil.

## Setup pengembangan

Gunakan JDK 17 atau kompatibel dengan Android Gradle Plugin yang dikunci di root build. Android SDK dengan Android API 35 dan Build Tools yang sesuai diperlukan untuk modul aplikasi serta modul Android library. Setelah SDK tersedia, jalankan pemeriksaan berikut dari root repository:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Untuk IDE, buka root repository sebagai proyek Gradle. Jangan menyimpan `local.properties`, credential, cookie, atau data sesi di repository. Kredensial IDN Times tidak dirancang untuk dikirim ke backend ArticlePilot.

## Struktur repository

| Direktori | Tanggung jawab |
| --- | --- |
| `app/` | Android application shell dan composition root Compose |
| `core/model/` | Model artikel dan asset yang serializable serta extensible |
| `core/parser/` | Kontrak parser dan version registry |
| `core/validator/` | Validation policy, diagnostics, dan severity |
| `core/database/` | Boundary persistence lokal berbasis Room |
| `media/` | Downloader, processor, dan validator media |
| `browser/` | Browser session, WebView adapter, dan bridge |
| `automation/` | State machine, runner, profile, selector, dan recovery |
| `docs/` | Keputusan arsitektur dan spesifikasi evolutif |
| `.github/workflows/` | Build, test, dan lint pada pull request/push |

## Prinsip engineering

Implementasi production berikutnya harus mempertahankan beberapa batas. Kegagalan download atau upload harus menjadi hasil eksplisit yang terlihat pengguna. Aksi browser hanya boleh dianggap berhasil setelah evidence dari DOM atau state halaman cocok dengan kondisi sukses. CAPTCHA, anti-bot challenge, dan mekanisme keamanan autentikasi tidak boleh dibypass. Ketika selector atau state tidak dapat dikenali, sistem harus pause dan menawarkan manual takeover dengan verifikasi resume.

## Langkah implementasi berikutnya

Langkah berikut yang paling tepat adalah menyepakati format ArticlePilot versi `1.0` dan aturan validasi minimum, kemudian mengimplementasikan parser fixture-driven beserta validator generik. Setelah itu, skema Room dan lifecycle temporary media dapat diturunkan dari model yang sudah tersedia. Selector serta profile IDN Times baru boleh diisi setelah workflow editor dan DOM aktual diverifikasi secara manual, tanpa melewati mekanisme keamanan platform.

## Referensi teknis

[1]: https://developer.android.com/jetpack/compose "Jetpack Compose documentation"
[2]: https://developer.android.com/training/data-storage/room "Room persistence library documentation"
[3]: https://developer.android.com/topic/libraries/architecture/workmanager "WorkManager documentation"
[4]: https://developer.android.com/develop/ui/views/layout/webapps/webview "Android WebView documentation"
[5]: https://docs.gradle.org/current/userguide/gradle_wrapper.html "Gradle Wrapper documentation"
