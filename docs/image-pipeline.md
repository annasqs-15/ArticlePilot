# ArticlePilot Media Core Image Pipeline

## Tujuan

Media Core menerjemahkan satu `ImageAsset.downloadUrl` menjadi file lokal yang terinspeksi, tervalidasi, dan dipromosikan ke lifecycle `ready`. Proses ini tetap terpisah dari parser, article-level validator, editor UI, WebView, dan browser automation. `sourceUrl` hanya metadata atribusi dan tidak pernah digunakan sebagai fallback download.

Artikel-level orchestration tetap menjadi tanggung jawab caller. `MediaPipeline` memproses satu asset pada satu waktu sehingga caller dapat mempertahankan asosiasi asset dengan cover atau block asalnya melalui `ImageAssetId`.

## Pipeline production saat ini

```text
ImageAsset.downloadUrl
        |
        v
URL and scheme validation
        |
        v
Controlled HTTP(S) transport
        |
        v
Temporary file allocation
        |
        v
Content-Length preflight and streamed byte limit
        |
        v
MIME sniffing and image inspection
        |
        v
Decode / dimension / format policy validation
        |
        v
Atomic promotion to ready storage
        |
        v
ImageAsset updated with local reference and facts
```

| Tahap | Implementasi | Output | Failure yang terlihat |
| --- | --- | --- | --- |
| Download | `StreamingImageDownloader` dengan `JvmHttpTransport` | `DownloadResult.Success` atau `Failure` | URL, scheme, network, timeout, HTTP, redirect, destination, atau size reason |
| Temporary storage | `FileSystemMediaStorage.createTemporary` | File unik berstatus temporary | Explicit `Result` pada create failure |
| Inspection | `DefaultMediaInspector` | MIME, format, size, width, height, checksum-adjacent facts, decode status, inspection issues | Missing file, empty file, invalid image, unsupported format, decode, atau dimension failure |
| Validation | `DefaultImageValidator` | `ImageValidationResult` dengan issue code/severity/path | MIME, size, dimensions, MIME mismatch, atau decode policy violation |
| Promotion | `MediaStorage.promote` | File ready dan `StoredMedia` | Explicit promotion failure |
| Cleanup | `MediaStorage.delete` | Success atau typed storage failure | Delete failure tidak disembunyikan |
| Orchestration | `MediaPipeline` | `Ready` atau classified `Failure` dan observable snapshot | State transition, cleanup failure, cancellation, dan validation issues |

## Download controls

`StreamingImageDownloader` menolak URL non-absolute atau non-HTTP(S) sebelum membuka transport. `JvmHttpTransport` mengikuti redirect secara eksplisit, menyelesaikan relative `Location`, dan menerapkan redirect limit. Setiap redirect kembali melewati validasi URL pada loop transport; tidak ada pengubahan diam-diam menjadi source URL.

Response dengan status di luar 2xx menghasilkan HTTP failure. Retry hanya dilakukan untuk failure yang diklasifikasikan retryable dan dibatasi `maxAttempts`. Backoff dapat diinjeksi melalui policy. `Content-Length` yang melebihi `maxDownloadBytes` ditolak sebelum body ditulis; body dengan length tidak tersedia atau tidak jujur tetap dibatasi saat streaming melalui counting input stream.

Cancellation diperlakukan berbeda dari ordinary failure. Cancellation dipropagasikan kembali ke coroutine caller setelah cleanup temporary dijalankan dalam `NonCancellable` context. Hal ini mencegah job cancellation meninggalkan file `.part` yang tidak terlacak.

## Storage lifecycle

`FileSystemMediaStorage` menggunakan root yang diberikan oleh aplikasi, subdirektori `temporary`, dan subdirektori `ready`. Nama file remote tidak pernah digunakan sebagai local path. Temporary file memiliki nama unik; promotion memakai atomic move bila filesystem mendukungnya dan fallback move bila tidak.

`MediaStorage` memiliki boundary berikut:

```kotlin
interface MediaStorage {
    suspend fun createTemporary(asset: ImageAsset): Result<LocalMediaFile>
    suspend fun promote(file: LocalMediaFile, asset: ImageAsset): Result<StoredMedia>
    suspend fun delete(file: LocalMediaFile): StorageResult
    suspend fun exists(file: LocalMediaFile): Boolean
}
```

Delete terhadap file yang sudah tidak ada mengembalikan `NOT_FOUND`, bukan success palsu. Cleanup failure dibawa ke `MediaPipelineResult.Failure.cleanupFailure`. Pipeline tidak mengklaim asset siap ketika promotion atau cleanup gagal.

Current storage adapter adalah JVM filesystem adapter untuk core testing dan controlled execution. Android production adapter masih perlu menghubungkannya ke app-private internal/cache storage dan ownership draft/publishing session tanpa mengubah interface di atas.

## Inspection dan MIME trust boundary

`DefaultMediaInspector` tidak mempercayai header HTTP sebagai bukti format. Format dideteksi dari magic bytes. JPEG, PNG, dan GIF diinspeksi menggunakan bounded Java ImageIO decode untuk memperoleh dimensi dan `decodeVerified = true`. WebP VP8X saat ini dapat dibaca untuk format dan dimensi header, tetapi tidak dianggap pixel-decoded; metadata tersebut diberi `WEBP_HEADER_ONLY_VERIFICATION` dan dapat ditolak oleh policy yang mewajibkan decode verification.

Declared MIME dari response disimpan sebagai fakta terpisah. Jika declared MIME berbeda dari detected MIME, inspector menghasilkan `MIME_MISMATCH`. `ImageValidationPolicy.rejectMimeMismatch` dapat mengubah mismatch menjadi error atau warning; issue tidak pernah dihapus dari result.

Decoder memakai `InspectionPolicy.maxDecodedPixels` untuk membatasi konsumsi memori saat ImageIO membaca gambar berukuran besar. Tidak ada bitmap allocation tanpa batas dan tidak ada network request pada inspection.

## Validation boundary

Media validator memakai hasil inspection yang sudah tersedia melalui `validateMetadata` sehingga pipeline tidak menginspeksi atau mendecode file dua kali. Policy generic dapat memeriksa:

- allowed MIME types;
- maximum file size;
- minimum dan maximum width/height;
- decode verification;
- MIME mismatch severity.

IDN Times-specific rules belum ditanamkan di Media Core. Platform profile dapat membuat `ImageValidationPolicy` sendiri setelah requirements platform diverifikasi. Article-level validator tetap menangani keberadaan URL, caption, credit, dan source metadata; Media Core menangani fakta file yang hanya dapat diketahui setelah download/inspection.

## Pipeline state dan recovery

`MediaPipeline` mengeluarkan snapshot yang dapat diamati melalui `MediaPipelineObserver`:

```text
NOT_STARTED
    -> DOWNLOADING
    -> DOWNLOADED
    -> INSPECTING
    -> VALIDATING
    -> READY
```

Failure path dapat berakhir pada `FAILED` atau `CLEANED` setelah temporary file berhasil dihapus. Cancellation mengeluarkan `CANCELLED` dan kemudian `CLEANED` bila cleanup berhasil, lalu tetap mempropagasikan cancellation ke caller. Snapshot memuat asset id, state, attempt, message, dan timestamp. Persistence checkpoint belum dipasang; caller yang memerlukan resume harus menyimpan snapshot dan mengikatnya ke publishing session.

Aksi tidak dianggap sukses hanya karena download selesai. Asset baru memiliki `processingStatus = READY`, `validationStatus = VALID`, local reference, MIME, dimensions, dan file size setelah inspection, validation, dan promotion berhasil.

## Optional processing boundary

`ImageProcessor` dan `ProcessingPolicy` tetap merupakan boundary terpisah untuk transformasi/compression. Implementasi compression, re-encode, dan format conversion belum diaktifkan pada task ini. Pipeline saat ini memproses file asli yang sudah tervalidasi dan mempromosikannya tanpa transformasi.

## Testing coverage

Media Core memiliki test JVM untuk:

- unique temporary files, promotion, existence, checksum, dan explicit missing-file delete;
- invalid scheme, content-length limit, streamed byte limit, retryable network failure, non-retryable HTTP failure, dan cancellation cleanup;
- real PNG decode, dimensions, MIME mismatch, invalid bytes, missing file, dan WebP VP8X header;
- MIME, size, dimension, decode, mismatch severity, and inspector failure policy;
- pipeline ready transitions, validation failure cleanup, download failure, observer snapshots, dan cancellation cleanup.

Semua test menggunakan injected transport, storage, inspector, validator, clock, atau observer. Test tidak membutuhkan live URL dan tidak mengubah repository dengan downloaded media.

## Belum diimplementasikan

Komponen berikut tetap sengaja berada di luar scope Media Core task ini:

- Android-specific storage adapter dan permission/lifecycle integration;
- WorkManager job untuk background download/resume;
- Room persistence untuk asset facts, pipeline snapshot, dan publishing session;
- image compression/re-encoding implementation;
- article-wide parallel scheduling and persisted retry queue;
- IDN Times-specific image rules;
- browser upload handoff dan WebView automation.

Boundary saat ini dibuat agar komponen-komponen tersebut dapat ditambahkan tanpa mengikat downloader pada UI, tanpa mengirim credential ke backend, dan tanpa memindahkan platform-specific behavior ke Article Core.
