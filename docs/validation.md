# ArticlePilot Validation Architecture

## Tujuan dan boundary

Validation engine menerima objek `Article` yang sudah terbentuk dari parser. Parser menjawab apakah teks dapat diinterpretasikan secara struktural; validator menjawab apakah model tersebut cukup bermakna dan siap diteruskan ke tahap berikutnya. Pemisahan ini mencegah syntax error, seperti property image tanpa delimiter colon, berpindah menjadi issue semantik yang salah tempat.

`ArticleValidationEngine` adalah komponen pure Kotlin di `core:validator`. Engine tidak membaca source text, tidak melakukan parsing ulang, tidak mengunduh atau membuka URL, tidak memeriksa byte image, dan tidak berinteraksi dengan WebView atau IDN Times. Seluruh hasil bersifat lokal, deterministik, dan dapat diuji melalui JVM.

> Validator tidak boleh menganggap sebuah aksi media atau browser berhasil hanya karena URL tersedia atau model telah diperiksa. Ketersediaan file, dimensi, MIME, dan status upload adalah tanggung jawab layer berikutnya.

## Alur arsitektur

```text
parsed Article
      |
      v
ArticleValidationEngine
      |
      +--> generic Article/section/block/image checks
      |
      +--> ValidationPolicy hooks
      |
      v
ValidationResult { issues, isValid }
      |
      v
media preparation / preview / future platform workflow
```

`ValidationPolicy` memiliki `id` dan `version`, serta menyediakan `imageRequirements` dan hook terurut untuk aturan article, section, block, dan image. `GenericArticleValidationPolicy` tidak mengetahui IDN Times. Implementasi platform harus berada di adapter/profile terpisah dan tidak boleh mengubah core model menjadi kelas IDN Times-specific.

## Aturan generic yang diimplementasikan

Aturan berikut berasal dari model ArticlePilot dan Article Format v1.0. Aturan yang membutuhkan fakta platform atau file image nyata tidak dipaksakan di layer ini.

| Area | Aturan | Diagnostic |
| --- | --- | --- |
| Article metadata | `metadata.title` tidak boleh blank | `EMPTY_TITLE` |
| Article structure | Article harus memiliki section | `EMPTY_ARTICLE` |
| Section | Section harus memiliki minimal satu text atau image block | `EMPTY_SECTION` |
| Section heading | Heading yang ada tidak boleh whitespace-only | `EMPTY_HEADING` |
| Text block | Text harus memiliki karakter non-whitespace | `EMPTY_TEXT` |
| Image asset | `downloadUrl` harus tersedia dan tidak blank | `MISSING_IMAGE_DOWNLOAD_URL` |
| Image metadata | `sourceUrl`, `caption`, `sourceName`, atau `credit` yang disuplai tidak boleh blank | `BLANK_IMAGE_METADATA` |
| Policy image requirement | Source URL dapat diwajibkan oleh policy | `MISSING_IMAGE_SOURCE_URL` |
| Policy image requirement | Caption dapat diwajibkan oleh policy | `MISSING_IMAGE_CAPTION` |
| Policy media fact | MIME type dapat diwajibkan atau dibatasi oleh policy jika fakta sudah tersedia | `MISSING_IMAGE_MIME_TYPE`, `INVALID_IMAGE_MIME_TYPE` |
| Policy media fact | File size dapat diwajibkan atau dibatasi oleh policy jika fakta sudah tersedia | `MISSING_IMAGE_FILE_SIZE`, `INVALID_IMAGE_FILE_SIZE`, `IMAGE_FILE_SIZE_EXCEEDS_LIMIT` |
| Policy media fact | Dimensions dapat diwajibkan oleh policy jika fakta sudah tersedia | `MISSING_IMAGE_DIMENSIONS`, `INVALID_IMAGE_DIMENSIONS` |

Heading adalah attribute section dan tidak diperlakukan sebagai block. Dengan demikian, text-only section dan image-only section valid selama block-nya memiliki isi yang bermakna. Section dengan heading tanpa block tetap invalid karena heading bukan publishable content.

Validator tidak mengulang validasi grammar parser. Misalnya, `download-url` dengan URL yang salah syntax adalah tanggung jawab parser pada saat dokumen eksternal dibaca. Untuk objek `Article` yang dibuat langsung oleh kode, validator hanya memastikan URL download tidak kosong; validasi URI dan validasi bytes dilakukan pada boundary yang sesuai.

## Empty-content decisions

Keputusan berikut sengaja membedakan syntax validity dan semantic readiness.

| Kondisi | Generic result | Alasan |
| --- | --- | --- |
| Article tanpa section | Invalid, `EMPTY_ARTICLE` | Tidak ada unit publishable yang dapat diteruskan |
| Article dengan title tetapi tanpa section | Invalid, `EMPTY_ARTICLE` | Metadata saja bukan artikel publishable |
| Section tanpa block | Invalid, `EMPTY_SECTION` | Format mengizinkan bentuk ini secara syntax, tetapi tidak bermakna untuk tahap berikutnya |
| Section hanya dengan blank text | Invalid, `EMPTY_TEXT` | Tidak ada konten yang dapat ditampilkan |
| Section hanya dengan image valid | Valid secara generic | Image adalah block publishable; kewajiban caption/source dapat datang dari policy |
| Section hanya dengan text valid | Valid secara generic | Text adalah block publishable |
| Heading kosong | Invalid, `EMPTY_HEADING` | Heading yang hadir harus bermakna; heading tetap optional |
| Image tanpa `downloadUrl` | Invalid, `MISSING_IMAGE_DOWNLOAD_URL` | Media pipeline tidak memiliki input yang dapat diunduh |
| Image tanpa source/caption | Valid generic, atau invalid menurut policy | Attribution requirements tidak ditebak di generic core |
| Image dengan metadata string blank | Invalid, `BLANK_IMAGE_METADATA` | Field yang disuplai tidak boleh menyamarkan metadata kosong |

## ValidationResult dan diagnostics

`ValidationResult` tidak berupa Boolean. Ia menyimpan seluruh `ValidationIssue` yang ditemukan dan menghitung `isValid` sebagai `true` hanya ketika tidak ada issue dengan severity `ERROR`. Karena itu, `WARNING` dan `INFO` dapat ditampilkan kepada pengguna tanpa memblokir tahap berikutnya.

Setiap issue memiliki `code`, `severity`, `message`, dan optional `path`. Path mengikuti struktur model dan menggunakan indeks zero-based untuk collection:

```text
metadata.title
cover.downloadUrl
sections[0]
sections[0].heading
sections[0].blocks[1]
sections[0].blocks[1].asset.sourceUrl
```

Kode generic yang diperkenalkan atau digunakan oleh engine saat ini adalah:

| Code | Severity default | Makna |
| --- | --- | --- |
| `EMPTY_TITLE` | `ERROR` | Title blank atau hanya whitespace |
| `EMPTY_ARTICLE` | `ERROR` | Article tidak memiliki section |
| `EMPTY_SECTION` | `ERROR` | Section tidak memiliki block |
| `EMPTY_HEADING` | `ERROR` | Heading yang hadir hanya whitespace |
| `EMPTY_TEXT` | `ERROR` | Text block hanya whitespace |
| `MISSING_IMAGE_DOWNLOAD_URL` | `ERROR` | Image asset tidak memiliki URL download bermakna |
| `MISSING_IMAGE_SOURCE_URL` | `ERROR` | Policy terpilih mewajibkan source URL tetapi field tidak tersedia |
| `MISSING_IMAGE_CAPTION` | `ERROR` | Policy terpilih mewajibkan caption tetapi field tidak tersedia |
| `BLANK_IMAGE_METADATA` | `ERROR` | `Optional image metadata hadir tetapi blank` |
| `MISSING_IMAGE_MIME_TYPE` | `ERROR` | Policy membutuhkan MIME type hasil media inspection |
| `INVALID_IMAGE_MIME_TYPE` | `ERROR` | MIME type yang tersedia tidak diizinkan policy |
| `MISSING_IMAGE_FILE_SIZE` | `ERROR` | Policy membutuhkan ukuran file hasil media inspection |
| `INVALID_IMAGE_FILE_SIZE` | `ERROR` | Ukuran file yang tersedia bernilai negatif |
| `IMAGE_FILE_SIZE_EXCEEDS_LIMIT` | `ERROR` | Ukuran file melampaui batas policy |
| `MISSING_IMAGE_DIMENSIONS` | `ERROR` | Policy membutuhkan dimensi hasil media inspection |
| `INVALID_IMAGE_DIMENSIONS` | `ERROR` | Width atau height yang tersedia tidak positif |

Policy boleh menambahkan kode stabilnya sendiri melalui hooks, tetapi harus menjaga `id`, `version`, ordering, path, dan severity yang terdokumentasi. Generic engine tidak mengubah atau mengklasifikasikan ulang issue dari policy.

## Deterministic ordering

Engine menambahkan diagnostics dalam urutan traversal tetap:

1. article metadata;
2. issue dari article policy;
3. cover dan image policy checks;
4. section pertama hingga terakhir;
5. section-level checks dan policy hook;
6. setiap block sesuai urutan model;
7. image checks sebelum block policy hook.

Traversal menggunakan `List.forEachIndexed`; tidak ada ketergantungan pada hash map atau urutan object reflection. Beberapa issue dalam satu article dikembalikan sekaligus agar UI dapat menampilkan daftar perbaikan dalam satu review.

## Severity dan policy

`ERROR` memblokir `ValidationResult.isValid`. `WARNING` dan `INFO` tidak memblokir, tetapi tetap merupakan bagian dari hasil dan harus tersedia untuk UI/logging. Generic policy saat ini tidak menghasilkan warning maupun info secara otomatis karena tidak ada threshold atau aturan kualitas editorial yang disepakati.

Policy platform dapat memakai `ImageValidationRequirements` untuk menambahkan kewajiban yang memang dipilih oleh platform profile. Contract saat ini dapat merepresentasikan kebutuhan source URL, caption, MIME type, allowed MIME set, file size, maximum file size, dan dimensions. Untuk MIME, ukuran, serta dimensi, engine hanya memvalidasi fakta yang sudah ada pada `ImageAsset` atau mengembalikan issue `MISSING_*`; engine tidak mengunduh, mendecode, atau mengarang nilai. Relationship section/image dan aturan platform lain tetap berada pada policy hook.

## Future media dependencies

Layer media akan menyediakan fakta yang belum dimiliki oleh parsed `Article`, termasuk:

- apakah `downloadUrl` dapat dijangkau dan berhasil diunduh;
- HTTP response, MIME aktual, dan ukuran file;
- dimensi hasil decode;
- file lokal sementara dan lifecycle cleanup;
- status processing/compression;
- retry exhaustion dan error transport.

Media validator dapat menerapkan policy byte-level setelah file ada. Validation engine article-level tidak memanggil downloader, tidak melakukan HTTP request, dan tidak mengubah `ImageAsset.validationStatus` hanya berdasarkan string metadata.

## Model review

Task ini tidak mengubah model domain. `Article`, `ArticleSection`, `TextBlock`, `ImageBlock`, dan `ImageAsset` sudah menyediakan semua data yang dibutuhkan generic semantic checks. `ValidationPolicy` menggunakan extension hooks dan `ImageValidationRequirements` untuk aturan platform tanpa menambahkan field IDN Times atau nilai image palsu ke model.

## Testing

Test JVM di `core:validator` mencakup artikel normal, multiple sections, text/image/text ordering, Unicode/Indonesia, image-only dan text-only sections, blank title, empty article, empty section, blank heading/text, missing download URL, generic optional attribution, policy-required source/caption, blank metadata consistency, multiple ordered diagnostics, serta WARNING/INFO behavior. Test tidak membutuhkan credential, network, WebView, file image, atau IDN Times.

Parser tests tetap berada di `core:parser`; parser syntax errors tidak dipindahkan ke validator. Media image inspection dan platform profile tests akan ditambahkan pada task berikutnya hanya setelah boundary datanya tersedia.
