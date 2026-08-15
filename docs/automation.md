# Browser Automation

## Tujuan

Automation Core menerjemahkan `Article` dan checkpoint menjadi operasi browser yang dapat diamati. Automation tidak boleh menganggap perintah yang dikirim sebagai bukti keberhasilan. Setiap state harus memiliki entry condition, action boundary, success evidence, failure classification, retry policy, dan checkpoint yang dapat dipersistenkan.

## State machine

Urutan konseptualnya adalah:

```text
START → OPEN_SITE → CHECK_SESSION → OPEN_EDITOR → FILL_METADATA
     → UPLOAD_COVER → CREATE_SECTION → WRITE_SECTION → UPLOAD_IMAGE
     → FILL_IMAGE_METADATA → NEXT_SECTION → FINAL_REVIEW → SUBMIT
     → VERIFY → COMPLETE
```

Urutan ini bukan linear script. State machine harus menginspeksi halaman saat resume dan memilih transition berdasarkan checkpoint serta evidence terbaru. Page reload, WebView restart, dan app termination dapat membuat state visual berbeda dari state terakhir yang tersimpan.

| State | Entry condition | Contoh success evidence |
| --- | --- | --- |
| `OPEN_SITE` | Session belum memiliki halaman target | URL dan page marker cocok |
| `CHECK_SESSION` | Halaman telah termuat | Login/editor indicator terdeteksi |
| `FILL_METADATA` | Editor dan field semantic tersedia | Field value terverifikasi |
| `UPLOAD_IMAGE` | Input file teridentifikasi | Preview/file metadata muncul di DOM |
| `FINAL_REVIEW` | Semua block telah diproses | Jumlah block dan warning cocok |
| `SUBMIT` | Review lulus dan user mengizinkan | Hanya dispatch; belum sukses |
| `VERIFY` | Submit telah dilakukan | Result page/status marker terdeteksi |
| `COMPLETE` | Result final telah diverifikasi | Session ditandai completed |

## Selector profile

IDN Times-specific selector dan workflow assumption berada di `automation:profiles` serta `automation:selectors`. Selector menggunakan semantic role, accessible name, test id, atau CSS yang diperoleh dari inspeksi terkontrol. Selector catalog harus memiliki version dan fixture evidence. Koordinat layar tidak menjadi fallback utama karena tidak memberi makna semantik dan rapuh terhadap layout.

## Bridge dan origin

WebView bridge harus memvalidasi origin, command schema, lifecycle attachment, dan response evidence. JavaScript yang dievaluasi harus dibatasi pada operasi yang diperlukan. Message dengan command atau payload yang tidak dikenal harus ditolak. Credential dan token tidak boleh masuk log.

## Retry dan timeout

Retry hanya boleh dilakukan untuk failure yang diklasifikasikan recoverable. Backoff, maksimum attempt, dan timeout harus menjadi policy; `wait(2000)` bukan strategi verifikasi. Setelah retry habis, state menjadi paused atau failed dengan alasan dan evidence terakhir.

## Manual takeover

Jika selector tidak ditemukan, halaman tidak dikenal, session expired, atau challenge keamanan muncul, automation harus pause. Pengguna dapat melakukan tindakan yang diizinkan secara manual. Resume hanya boleh dilakukan setelah state machine menerima evidence yang sesuai. ArticlePilot tidak mengotomasi atau membypass CAPTCHA, anti-bot challenge, authentication security, atau mekanisme perlindungan serupa.

## Lingkungan pengujian

Unit test state machine memakai fake browser contract dan synthetic evidence. Integration test memakai local HTML fixtures dengan selector catalog version tertentu. Live IDN Times hanya untuk smoke verification terkontrol setelah profile disetujui; test suite normal tidak bergantung pada situs live.

## Belum diimplementasikan

`UnimplementedAutomationRunner` sengaja mengembalikan pause. Implementasi produksi baru boleh menggantikannya setelah parser, persistence checkpoint, WebView bridge, selector profile, manual takeover UX, dan verification harness tersedia.
