# ArticlePilot Article Format

## Status spesifikasi

Dokumen ini mendeskripsikan **draft format `1.0-draft`** untuk pertukaran artikel antara AI atau alat eksternal dan ArticlePilot. Format ini belum dianggap final. Parser production tidak boleh menganggap contoh di bawah sebagai kontrak stabil sebelum grammar, escaping, whitespace, duplicate directive, dan backward compatibility disepakati.

> Format input adalah boundary versioned. Internal `Article` model harus tetap menjadi representasi kanonik setelah parsing.

## Contoh konseptual

```text
@article

@title
Judul artikel

@excerpt
Ringkasan artikel.

@cover
url: https://cdn.example/cover.webp
source: https://source.example/story
credit: Nama fotografer
caption: Deskripsi cover

@section

@heading
Bagian pertama

@text
Paragraf pertama.

@image
url: https://cdn.example/image.webp
source: https://source.example/source
credit: Nama pemilik
caption: Keterangan gambar

@text
Paragraf berikutnya.
```

Contoh tersebut bersifat informatif, bukan janji bahwa parser saat ini menerima input tersebut.

## Konsep grammar

| Directive | Target model | Cardinality konseptual |
| --- | --- | --- |
| `@article` | Dokumen root dan format version | Satu |
| `@title` | `ArticleMetadata.title` | Satu, required |
| `@excerpt` | `ArticleMetadata.excerpt` | Nol atau satu |
| `@cover` | `Article.cover` | Nol atau satu |
| `@section` | `Article.sections[]` | Nol atau lebih |
| `@heading` | `ArticleSection.heading` | Nol atau satu per section |
| `@text` | `TextBlock` | Nol atau lebih |
| `@image` | `ImageBlock.asset` | Nol atau lebih |
| `url` | `ImageAsset.downloadUrl` atau `sourceUrl` sesuai field | Dipisahkan secara eksplisit |
| `source` | `ImageAsset.sourceUrl` | Opsional sesuai policy |
| `credit` | `ImageAsset.credit` | Opsional sesuai policy |
| `caption` | `ImageAsset.caption` | Opsional sesuai policy |

Download URL dan source URL harus selalu dipertahankan sebagai konsep terpisah. Parser tidak boleh mengisi source URL dari download URL secara diam-diam.

## Parser contract

`core:parser` mengekspos `ArticleParser`, `ParseResult.Success`, `ParseResult.Failure`, dan `ParseDiagnostic`. Diagnostic memiliki line, code, serta message agar UI dapat menunjuk kesalahan pada input. `ArticleFormatRegistry` memilih parser berdasarkan version sehingga migration atau parser baru tidak mengubah internal model.

Parser production harus menangani setidaknya: directive yang tidak dikenal, field wajib yang hilang, block di luar section, duplicate singleton field, URL invalid, property tanpa value, text kosong, escaping delimiter, dan input kosong. Setiap kondisi harus menghasilkan diagnostic atau policy-defined warning; tidak ada error yang boleh dibuang diam-diam.

## Evolusi format

Revisi minor dapat menambah directive opsional dengan default eksplisit. Revisi major dapat memerlukan parser baru dan migration step. Setiap perubahan harus menambah fixture valid, fixture invalid, expected model, dan expected diagnostics. Model internal hanya berubah jika kemampuan domain baru memang diperlukan, bukan karena perubahan nama directive eksternal.

## Keputusan yang belum final

Whitespace normalization, multiline metadata, escaping, format version declaration, dan dukungan block type masa depan belum dibekukan. Parser tidak boleh menyimpulkan semantik dari line position selain yang dinyatakan grammar version terkait.
