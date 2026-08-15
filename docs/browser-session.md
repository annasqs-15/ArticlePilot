# Browser Session Foundation

## Tujuan dan batas

Modul browser ArticlePilot menyediakan sesi Android WebView yang dapat diamati untuk membuka lingkungan contributor IDN Times. Fondasi ini berhenti pada akses dan verifikasi state halaman; **tidak ada pengisian artikel, upload, penyimpanan draft, submit, atau polling moderasi**. Jika evidence tidak cukup, state diklasifikasikan sebagai `UNKNOWN` dan sistem tidak melanjutkan ke operasi editorial.

> Status `AUTHENTICATED` hanya boleh muncul setelah evidence halaman yang eksplisit dan terverifikasi. Navigasi berhasil, HTTP 200, atau judul halaman saja tidak dianggap sebagai bukti autentikasi.

## Arsitektur

`BrowserSession` adalah kontrak Kotlin/JVM yang mengekspos `StateFlow<BrowserSessionState>`, `open`, `reload`, dan `inspect`. Kontrak ini tidak mengetahui Android View, selector IDN Times, atau kredensial. `AndroidBrowserSession` menjadi adapter Android yang menggabungkan event lifecycle WebView dengan `IdnTimesAuthenticationClassifier`.

| Komponen | Tanggung jawab | Batas keamanan |
| --- | --- | --- |
| `BrowserSession` | Kontrak state, operasi navigasi, inspeksi | Tidak menerima password, cookie, token, atau header |
| `AndroidBrowserSession` | State machine sesi dan evidence reduction | Tidak menganggap dispatch navigasi sebagai autentikasi |
| `AndroidWebViewHost` | Konfigurasi/lifecycle WebView, navigation client, inspeksi DOM terbatas | Hanya origin HTTPS yang diizinkan; interface JavaScript arbitrer tidak diekspos |
| `IdnTimesBrowserProfile` | Entry point, origin policy, dan marker yang didukung evidence | Marker authenticated/editor tetap kosong sampai observasi current tersedia |
| `SafeInspectionBrowserBridge` | Inspeksi sanitized | Lookup selector dan mutation selalu pause |
| `ManualLoginSessionBootstrapper` | Bootstrap tanpa credential material | Hanya membuka alur login manual di WebView |

## Lifecycle WebView

`AndroidWebViewHost.attach` hanya dipanggil pada main thread dan mengkonfigurasi JavaScript/DOM storage karena target adalah situs web aplikasi yang membutuhkan keduanya. File/content access dinonaktifkan, multiple windows dibatasi, dan `WebViewClient` menangani navigasi, error, TLS error, Safe Browsing, serta render-process termination.

`detach` menghentikan loading, mengganti client, menghapus child view, dan memanggil `destroy`. `AndroidBrowserSession.dispose` membatalkan inspeksi tertunda, menerbitkan `DISPOSED`, lalu melepas host. Compose hanya memiliki lifecycle container melalui `AndroidView`; Compose tidak menjalankan JavaScript atau selector.

## Session bootstrap dan secure storage

Saat ini ArticlePilot **tidak menyimpan session material secara persisten**. Reconnaissance belum membuktikan format session IDN Times yang aman dan valid untuk disuntikkan ke Android WebView; JWT-like value tidak diperlakukan sebagai cookie. Karena tidak ada credential/cookie/token yang diterima oleh kontrak `SessionBootstrapper`, tidak ada rahasia yang masuk ke SharedPreferences, Room, BuildConfig, log, atau file proyek.

`ManualLoginSessionBootstrapper` mengembalikan izin untuk membuka sesi WebView biasa. Pengguna dapat login sendiri apabila halaman memintanya, sedangkan ArticlePilot hanya melakukan inspeksi state sesudahnya. Jika kelak mekanisme session bootstrap reusable diperlukan, implementation baru wajib menggunakan Android Keystore-backed storage atau memilih session-only handling dan mendokumentasikan threat model-nya.

## Authentication detection

Classifier menggabungkan URL, title yang sudah dibatasi panjangnya, dan sanitized DOM signals. Marker login `Masuk/Daftar` hanya dipakai untuk mendeteksi public/login state; judul `Manage Article Community | IDN Times` tidak cukup. Challenge marker memiliki prioritas tertinggi, lalu session expired, authenticated marker, dan login marker. Profile current sengaja belum mengisi authenticated/editor markers karena evidence reconnaissance menunjukkan public shell dan API `401`, bukan editor authenticated.

| State | Makna | Tindakan |
| --- | --- | --- |
| `AUTHENTICATING` | Navigasi sedang berjalan atau inspeksi belum selesai | Tunggu evidence; tidak ada aksi editorial |
| `AUTHENTICATED` | Marker authenticated yang disetujui profile teramati | Hanya tampilkan state; publishing automation tetap disabled |
| `LOGIN_REQUIRED` | Public login marker teramati | Manual login fallback |
| `SESSION_EXPIRED` | Marker expiry teramati | Manual login/re-authentication |
| `SECURITY_CHALLENGE` | CAPTCHA, human verification, atau security challenge teramati | Pause; jangan bypass |
| `UNKNOWN` | Evidence belum cukup atau conflicting | Pause dan minta review |
| `ERROR` | Network/WebView/security failure | Tampilkan failure dan tawarkan reload/manual recovery |

## Allowed-origin policy

`AllowedOriginPolicy` hanya menerima URL HTTPS dengan host dan port standar 443 yang sama persis dengan origin profile. Origin current adalah `https://community.idntimes.com`; URL dengan HTTP, host suffix berbahaya, user-info, fragment, port lain, atau domain lain ditolak. `shouldOverrideUrlLoading` juga menolak navigasi utama keluar dari policy. Kebijakan dapat diperluas hanya melalui profile yang direview, bukan dari URL input arbitrer.

## Manual login fallback

UI Browser menyediakan WebView normal, tombol membuka entry point, reload, dan status yang disanitasi. ArticlePilot tidak mengotomatisasi credential entry, tidak membaca field password, tidak menyimpan login input, dan tidak melewati CAPTCHA/MFA/OTP. Sesudah pengguna menyelesaikan login secara manual, pengguna dapat menekan `Reload` sehingga session melakukan inspeksi ulang. Jika hasil tetap `UNKNOWN`, sistem tidak mengklaim authenticated.

## Browser inspection dan research mode

Inspection hanya mengembalikan URL, title terbatas, page category, navigation state, authentication state, dan nama evidence yang sudah disanitasi. Script internal hanya menghitung keberadaan marker publik/challenge dari `document.body.innerText` yang dipotong; tidak mengembalikan cookies, authorization header, token, private form values, atau account data. Bridge mutation sengaja mengembalikan `Paused`, sehingga belum ada arbitrary JavaScript execution atau production selector action.

Screen menampilkan current URL, title, page, authentication state, navigation state, evidence names, dan failure reason. Mode ini adalah research/debug surface untuk menangkap evidence berikutnya, bukan automation console.

## Kegagalan dan recovery boundary

Network error, TLS failure, origin rejection, render-process crash, disposed session, dan inspection failure diterbitkan sebagai state eksplisit. Reload mengembalikan state ke `AUTHENTICATING`; crash menghasilkan `ERROR` dengan `WEBVIEW_CRASH`. Durable resume untuk publishing belum diaktifkan karena editor authenticated dan idempotency semantics belum diverifikasi. Reopen setelah process death harus dimulai dari manual session flow dan inspeksi baru.

## Known limitations

Current authenticated editor belum berhasil diverifikasi dalam reconnaissance sehingga profile belum memiliki marker authenticated/editor yang production-ready. Tidak ada selector yang dipromosikan, tidak ada cookie/session bootstrap reusable, belum ada instrumentation test berbasis device, dan belum ada publishing automation. HTTP subresource `401` belum diekstrak dari WebView sebagai evidence; integrasi berikutnya perlu menambahkan network observation yang tidak membocorkan payload, bila memang diperlukan.

## Future automation boundary

Setelah sanitized authenticated editor snapshot direview, langkah berikutnya adalah membuat local HTML fixtures, menyusun profile version, dan memvalidasi selector semantic dengan read-back evidence. Hanya setelah itu mutation bridge, checkpoint, dan automation state transitions dapat ditambahkan. Submit, CAPTCHA bypass, credential automation, dan koordinat layar tetap dilarang.

## References

[1]: https://developer.android.com/develop/ui/views/layout/webapps/webview "Android WebView documentation"

[2]: https://developer.android.com/reference/android/webkit/WebViewClient "Android WebViewClient reference"

[3]: https://developer.android.com/privacy-and-security/keystore "Android Keystore system"

[4]: ./idn-times-authenticated-reconnaissance.md "ArticlePilot authenticated reconnaissance evidence"
