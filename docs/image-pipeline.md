# Image Pipeline

## Tujuan

Media Core mengubah referensi URL menjadi file lokal yang siap digunakan browser, tanpa mengikat proses tersebut pada editor IDN Times. Setiap `ImageAsset` tetap terkait dengan block atau cover asalnya melalui asset id dan tidak boleh hilang saat pipeline gagal.

## Tahapan

```text
ImageAsset.downloadUrl
        ↓
Download
        ↓
Content and size guard
        ↓
Decode
        ↓
Dimension validation
        ↓
File-size validation
        ↓
Format validation
        ↓
Optional processing/compression
        ↓
Temporary/local storage
        ↓
Ready for browser upload
```

| Tahap | Input | Output | Failure yang harus terlihat |
| --- | --- | --- | --- |
| Download | URL terpisah dari source URL | File lokal sementara | Invalid URL, network, HTTP, size limit |
| Decode | File lokal | Metadata MIME/dimensi | File corrupt atau unsupported |
| Validate | Metadata dan policy | Issues terklasifikasi | Format, dimensi, ukuran, metadata |
| Process | File valid dan processing policy | File output | Compression/transform error |
| Store | Output file | Local reference | Storage penuh atau path unavailable |
| Browser handoff | Asset + local reference | Upload-ready asset | File hilang, permission, stale reference |

## Contract dan status

`ImageDownloader`, `ImageProcessor`, dan `ImageValidator` adalah boundary terpisah. `ImageProcessingStatus` membedakan not started, downloading, downloaded, processing, ready, dan failed. `ImageValidationStatus` membedakan not validated, valid, dan invalid. Status ini harus dipersistenkan atau direkonstruksi dengan deterministik dari file metadata; jangan menampilkan asset ready hanya karena URL berhasil diambil.

Download result selalu sukses atau failure terstruktur dengan alasan dan `canRetry`. Failure network dapat retry dengan backoff. Failure format atau metadata harus ditampilkan sebagai validation issue dan tidak boleh diulang tanpa perubahan policy atau input. Pipeline harus dapat dilanjutkan untuk asset lain sambil tetap membuat artikel tidak publishable jika asset required gagal.

## File lifecycle

File temporary sebaiknya berada di app-specific cache atau storage internal dengan ownership yang terkait draft/publishing session. Cleanup harus memiliki retention policy, reference count atau session ownership, dan recovery guard. File yang masih direferensikan checkpoint tidak boleh dihapus. Cleanup job harus mencatat file yang gagal dihapus dan tidak menyembunyikan error storage.

## Keamanan dan resource

Downloader production harus memiliki timeout, redirect policy, maximum response size, MIME sniffing, dan pembatasan konsumsi memori. Decoder harus menghindari loading bitmap berukuran tak terbatas. URL download dan source URL harus divalidasi terpisah; source URL adalah metadata atribusi, bukan fallback download.

## Belum diimplementasikan

HTTP client, Android file destination, decoder metadata, image compressor, cleanup worker, dan policy IDN Times sengaja belum dibuat pada tahap fondasi. Kontraknya tersedia agar implementasi berikutnya dapat diuji tanpa mengubah Article model atau automation layer.
