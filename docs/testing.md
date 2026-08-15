# Testing Strategy

## Tujuan

Testing ArticlePilot harus memberi keyakinan pada translator dan execution engine tanpa membuat setiap unit test bergantung pada WebView atau situs IDN Times yang hidup. Test yang deterministik berjalan di JVM; Android dan browser integration test hanya digunakan untuk boundary yang memang membutuhkan platform.

## Lapisan test

| Lapisan | Target | Infrastruktur |
| --- | --- | --- |
| Unit model | Article, ImageAsset, block ordering, serialization shape | Kotlin/JVM |
| Parser fixtures | Syntax version, valid/invalid input, diagnostics | Kotlin/JVM |
| Validator | Generic policy dan platform policy | Kotlin/JVM |
| Media unit | MIME, size, dimension, retry classification | Kotlin/JVM dengan fake files |
| Media integration | Downloader, decoder, processor, cleanup | Android/instrumented atau controlled temp storage |
| State machine | Entry, transition, evidence, retry, pause | Kotlin/JVM dengan fake events |
| Persistence | Room migration, draft/revision/session/log | Android/in-memory Room |
| Recovery | Crash/reload/expired session/resume | Kotlin/JVM contract tests |
| Browser bridge | Message schema, origin, DOM evidence | Android WebView fixture |
| UI/integration | Import → preview → publishing UX | Compose UI test |
| Live smoke | Profile compatibility only | Manual, gated, tidak di PR default |

## Aturan fixture

Setiap format version memiliki fixture valid dan invalid. Expected output menyimpan Article terstruktur atau diagnostics lengkap, bukan hanya boolean. Browser fixture adalah HTML lokal dengan marker semantic yang stabil; fixture tidak boleh menggunakan koordinat. Selector profile harus diuji terhadap fixture version-nya dan ditolak jika evidence wajib tidak ditemukan.

## Failure-path coverage

Test wajib mencakup missing title, empty text, missing download URL, missing source URL ketika policy mewajibkan, missing caption, invalid MIME, invalid dimensions, file size limit, network retry exhaustion, upload failure, page reload, session expiration, selector not found, timeout, dan manual takeover resume.

## Test isolation

Unit test tidak memerlukan credential atau network eksternal. Downloader menggunakan controlled response/fake transport. Persistence memakai database sementara. WebView integration hanya memuat fixture lokal. Test live IDN Times tidak boleh menjadi dependency build biasa karena halaman, session, dan policy dapat berubah.

## Static checks dan CI

CI menjalankan Gradle wrapper validation, unit tests, lint, dan debug assemble pada pull request serta push. Warnings harus ditinjau, terutama serialization, WebView lifecycle, thread confinement, dan resource cleanup. Build harus gagal bila test atau lint gagal.

## Status tahap pertama

Test kontrak model, parser result, validation result, image validation result, browser session failure, checkpoint, dan recovery boundary telah ditambahkan. Parser Article Format v1.0 kini diimplementasikan sebagai pure Kotlin component dan diuji melalui seluruh manifest fixture valid/invalid, structured output assertions, ordering, multiline text, Unicode, escaping, URL separation, serta diagnostic line/column/path. Pipeline media, Room, bridge, dan state transition production belum tersedia; test untuk boundary tersebut akan ditambah bersamaan dengan implementasinya, bukan dipalsukan melalui demo.

## Referensi

[1]: https://developer.android.com/training/testing "Android testing documentation"
[2]: https://developer.android.com/develop/ui/compose/testing "Compose testing documentation"
[3]: https://developer.android.com/training/data-storage/room/testing-db "Testing Room databases"
