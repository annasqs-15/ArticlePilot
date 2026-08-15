# Recovery and Resumability

## Prinsip

Automation harus mengetahui apa yang benar-benar telah selesai. Attempted action bukan completed action. Progres yang dapat memengaruhi keputusan berikutnya disimpan sebagai checkpoint bersama evidence terakhir, attempt count, section index, block id, dan timestamp.

## Failure matrix

| Failure | Default response | Resume condition |
| --- | --- | --- |
| WebView crash | Recreate WebView, inspect current page | Page identity dan session state terverifikasi |
| App termination | Load persisted checkpoint | Checkpoint valid dan media reference masih ada |
| Network failure | Retry dengan bounded backoff | Network kembali dan state belum berubah |
| Image download failure | Mark asset failed, retry sesuai policy | Download result success dan validation lulus |
| Image upload failure | Inspect DOM, retry terbatas | Input dan uploaded evidence cocok |
| Page reload | Re-inspect page | Page/state evidence cocok dengan checkpoint |
| Session expiration | Pause untuk login manual | Auth/session indicator terverifikasi |
| Unexpected UI state | Pause manual takeover | User action selesai dan expected evidence ada |
| Automation timeout | Classify, retry atau pause | State inspection memberi bukti cukup |

## Checkpoint

`AutomationCheckpoint` adalah data yang dapat disimpan melalui `PublishingSessionStore` dan log. Checkpoint tidak menyimpan credential. Checkpoint harus invalidated atau ditandai stale jika article revision berubah, profile version berubah secara incompatible, atau asset local reference hilang. Resume wajib menginspeksi halaman aktual sebelum melanjutkan dari phase lama.

## Manual takeover

Pause event harus menyimpan session id, alasan, expected evidence, dan state terakhir. UI publishing menampilkan alasan dalam bahasa pengguna, bukan detail selector mentah sebagai satu-satunya informasi. Setelah manual action, sistem menerima evidence dari browser bridge dan hanya melanjutkan jika evidence memenuhi expected condition.

## Idempotency

Setiap action yang mengubah editor harus memiliki detection untuk keadaan already completed. Misalnya upload tidak boleh mengirim file kedua hanya karena retry setelah crash; sistem harus lebih dulu mencari preview atau metadata yang cocok. Submit adalah action berisiko tinggi dan memerlukan final review, explicit user intent, serta post-submit verification.

## Cleanup dan retention

Draft, revision, logs, checkpoints, dan temporary media memiliki lifecycle berbeda. Recovery tidak boleh menghapus file yang sedang direferensikan sesi aktif. Cleanup dapat berjalan setelah session completed atau expired sesuai retention policy, tetapi harus bersifat observable dan dapat melaporkan item yang gagal dibersihkan.

## Belum diimplementasikan

Repository adapter Room, recovery coordinator, cleanup worker, dan UI manual takeover belum aktif. Fondasi hanya menetapkan contracts dan data shape agar implementasi berikutnya tidak harus mengubah workflow model.
