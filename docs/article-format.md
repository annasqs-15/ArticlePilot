# ArticlePilot Article Format v1.0

**Status: FINALIZED CONTRACT**

This document is the authoritative specification for **ArticlePilot Article Format v1.0**. It defines the external text contract between an article generator and ArticlePilot. It is deliberately independent of IDN Times DOM structure, browser selectors, automation state, credentials, and publishing instructions.

A v1.0 document is intended to be easy for an AI to generate, easy for a person to copy and edit, deterministic to parse, and extensible without coupling the external syntax to the internal publishing implementation.

## 1. Design goals

The format uses a small set of column-one directives, one-line metadata fields, explicit block terminators where multiline content is possible, and explicit image property names. It avoids indentation-sensitive structure, comma-separated lists, implicit paragraph termination, and overloaded `url` properties.

The format represents one article document. It supports article metadata, an optional cover image, an arbitrary number of sections, and an ordered sequence of text and image blocks in each section. Section headings are section attributes, not interleavable blocks. Future block types are reserved by the versioning rules but are not part of v1.0.

The parser must produce a structured `Article` model or deterministic diagnostics. It must never silently discard a line, property, block, image, or metadata field.

## 2. Encoding and line model

A document is Unicode text encoded as UTF-8. A UTF-8 byte-order mark at the beginning of the input is accepted and ignored. Line endings are normalized before parsing: CRLF and CR become LF. A line is the sequence of characters between LF separators; the final line need not end with LF.

Outside a multiline text block, blank lines are ignored. Blank lines are not meaningful separators and must not change the model. Inside a text block, every line—including blank lines—is content and is preserved after line-ending normalization.

Structural directives and image properties must start in column 1. Indentation before a directive or property is not allowed. Tabs and spaces inside metadata values, image property values, and text content are ordinary characters subject to the rules below.

Leading and trailing ASCII spaces and tabs are removed from one-line metadata values and image property values. Internal whitespace is preserved. A value that becomes empty after this trimming is missing and produces a diagnostic. Text block content is not trimmed.

A document may begin or end with any number of blank lines. The first non-blank line must be the version declaration.

## 3. Version declaration and document envelope

The exact first non-blank line is:

```text
@article version: 1.0
```

The directive name, lowercase spelling, single ASCII spaces, colon, and version token are all significant. The version token is machine-readable and is not inferred from a filename or application setting.

There is no required `@end-article` line. End of input closes the article after all open structures have been closed validly. Explicit terminators are required for `@text`, `@cover`, and `@image` because those structures can contain multiple lines.

Only version `1.0` is supported by this specification. A document declaring another syntactically valid version produces `UNSUPPORTED_VERSION`; it must not be parsed using the v1.0 grammar as a best effort.

## 4. Complete syntax

The following syntax is normative. `LF` means a normalized line ending. Angle-bracket terms are value placeholders, not literal characters.

### 4.1 Article metadata

Metadata directives are allowed only after the version declaration and before the cover or first section. Every one-line metadata directive consumes exactly the next physical line as its value.

```text
@title
<title>

@excerpt
<excerpt>

@category
<category>

@tag
<tag>
```

`@title`, `@excerpt`, `@category`, and `@tag` must be followed immediately by a value line. The value line may contain punctuation and spaces but must not be empty after outer whitespace trimming. `@title` and `@excerpt` are single-line fields; multiline article prose belongs in `@text`.

`@tag` is repeatable. The order of tags is preserved in the parsed model. Exact duplicate tag values are a deterministic `DUPLICATE_TAG` error rather than being silently deduplicated.

### 4.2 Cover image

The cover is optional and may appear at most once after article metadata and before the first section.

```text
@cover
download-url: <absolute-url>
source-url: <absolute-url>
source-name: <source-name>
credit: <credit>
caption: <caption>
@end-cover
```

Only `download-url` is required by the format. `source-url`, `source-name`, `credit`, and `caption` are optional at the syntax layer. A platform validation policy may require attribution fields before publishing. Property order is arbitrary, but each known property may occur at most once. The block must contain at least one property and must contain `download-url` before `@end-cover`.

### 4.3 Section and section heading

A document may contain zero or more sections. A section begins with an exact `@section` line and extends until the next `@section` line or end of input. The section identifier is generated deterministically from its one-based order (`section-1`, `section-2`, and so on); identifiers are not part of the external syntax.

An optional heading may appear once immediately after `@section` and before the first content block:

```text
@section
@heading
<section-heading>
```

The heading is a one-line section attribute. A missing heading is valid at the format layer; a platform policy may later issue a warning or error. A second `@heading`, or a heading after a content block, produces a deterministic diagnostic.

### 4.4 Text block

A text block is explicitly terminated:

```text
@text
<zero-or-more-content-lines>
@end-text
```

All lines between `@text` and the exact column-one line `@end-text` are content. Blank lines are preserved. Multiple paragraphs may therefore be represented inside one text block by placing a blank line between them. A text block must contain at least one non-whitespace character; otherwise the parser reports `EMPTY_TEXT_BLOCK`.

A line equal to `@end-text` cannot be written literally without escaping. The escaping rules in Section 6 apply. If end of input occurs before `@end-text`, the parser reports `UNTERMINATED_TEXT_BLOCK`.

### 4.5 Image block

An image block is explicitly terminated and uses names that cannot be confused with one another:

```text
@image
download-url: <absolute-url>
source-url: <absolute-url>
source-name: <source-name>
credit: <credit>
caption: <caption>
@end-image
```

`download-url` is required. The other properties are optional at the syntax layer and may be required by a validation policy. Only the five property names shown above are recognized in v1.0. A property may appear once only. The property key is the exact lowercase token before the first colon; the first colon is the delimiter, so colons, query strings, fragments, equals signs, ampersands, and percent-encoded characters in a URL value do not affect parsing.

Each `@image` creates one `ImageBlock` in the exact position in which it appears. Image blocks are associated with the section that contains them. A cover is not a section block.

## 5. Normative grammar

The grammar below is expressed in an EBNF-like notation. Lexical URL and value constraints are defined in Sections 6–10.

```text
DOCUMENT        = BLANK*, VERSION, BLANK*, HEADER_ITEM*, COVER?, SECTION* , BLANK* ;
VERSION         = "@article version: 1.0", LF? ;
HEADER_ITEM     = TITLE | EXCERPT | CATEGORY | TAG | BLANK ;
TITLE           = "@title", LF, NON_BLANK_VALUE, LF? ;
EXCERPT         = "@excerpt", LF, NON_BLANK_VALUE, LF? ;
CATEGORY        = "@category", LF, NON_BLANK_VALUE, LF? ;
TAG             = "@tag", LF, NON_BLANK_VALUE, LF? ;
COVER           = "@cover", LF, IMAGE_PROPERTY+, "@end-cover", LF? ;
SECTION         = "@section", LF?, HEADING?, BLOCK* ;
HEADING         = "@heading", LF, NON_BLANK_VALUE, LF? ;
BLOCK           = TEXT_BLOCK | IMAGE_BLOCK | BLANK ;
TEXT_BLOCK      = "@text", LF, TEXT_LINE*, "@end-text", LF? ;
IMAGE_BLOCK     = "@image", LF, IMAGE_PROPERTY+, "@end-image", LF? ;
IMAGE_PROPERTY  = PROPERTY_LINE, LF? ;
PROPERTY_LINE   = PROPERTY_KEY, ":", HSPACE*, PROPERTY_VALUE ;
PROPERTY_KEY    = "download-url" | "source-url" | "source-name" | "credit" | "caption" ;
BLANK           = HSPACE*, LF ;
NON_BLANK_VALUE = VALUE_LINE where trim(VALUE_LINE) is not empty ;
TEXT_LINE       = any line except an unescaped "@end-text" line ;
```

The grammar describes structure; diagnostics still apply when a line is lexically malformed, a directive occurs in the wrong context, or a required property is missing.

## 6. Escaping rules

Escaping is intentionally narrow. It exists only inside `@text` blocks so a user can write a literal line that would otherwise be the text terminator. Metadata and image property values do not use backslash escaping; a backslash there is an ordinary character and may subsequently make a URL invalid if the URL rules reject it.

At column 1 inside `@text`:

| Source line | Parsed text line | Meaning |
| --- | --- | --- |
| `\@end-text` | `@end-text` | Literal terminator text |
| `\\hello` | `\hello` | Literal leading backslash |
| `\q` | invalid | Unknown escape |

Only `\@` and `\\` are valid leading text escapes. An isolated backslash at the end of a text line, or a backslash followed by any other character at column 1, produces `INVALID_ESCAPING`. Backslashes elsewhere in a text line are preserved. Escaping does not remove or add line endings.

There is no escaping mechanism for a one-line metadata value. A value needing a line break must be represented as a text block, not encoded as an escaped newline.

## 7. URL rules

`download-url` and `source-url` values are separate fields and are never inferred from one another. Each URL must be an absolute URI with an `http` or `https` scheme, a non-empty authority/host, no ASCII whitespace, and no control characters. Query strings, fragments, ports, colons, equals signs, ampersands, percent encoding, parentheses, commas, and other URI characters are allowed when valid for the URI.

A literal space is invalid; it must be percent-encoded. A URL value is trimmed only at its outer ASCII whitespace. URL validation is syntax validation, not a network request. Redirects, MIME type, file size, dimensions, and reachability belong to the media pipeline and validation policy.

## 8. Article-level metadata rules

The v1.0 metadata set is intentionally limited to fields that are platform-independent and represented by the Article model. `category` is a single optional value. `tag` is a repeatable optional value. A cover is represented separately from section images.

| Field | Required | Syntax | Allowed values and validation | Internal mapping |
| --- | --- | --- | --- | --- |
| `title` | Yes | One non-blank line after `@title` | Trimmed outer whitespace; internal punctuation/spaces preserved; no newline | `Article.metadata.title` |
| `excerpt` | No | One non-blank line after `@excerpt` | Same scalar rules as title | `Article.metadata.excerpt` |
| `category` | No | One non-blank line after `@category` | Non-blank Unicode scalar; no fixed enumeration in core v1.0 | `Article.metadata.category` |
| `tag` | No, repeatable | One non-blank line after each `@tag` | Non-blank Unicode scalar; exact duplicates are errors | `Article.metadata.tags[]` |
| `cover` | No | `@cover` property block | `download-url` required; other image metadata optional at syntax layer | `Article.cover` |

Category values are not enumerated in the external format because category vocabularies belong to a platform profile, not to the article interchange boundary. The v1.0 format does not include author identity, credentials, publication status, browser selectors, scheduling, or platform-specific IDs.

## 9. Section and block rules

Sections are ordered by appearance and may be arbitrarily many. A section may have zero or one heading followed by zero or more blocks. The heading must precede the first block. A section with no blocks is syntactically valid but should produce a validator issue because it does not contain publishable content.

Text and image blocks preserve exact relative order. The parser generates deterministic internal IDs from position: `section-N` for sections and `section-N-block-M` for blocks. IDs are not supplied by the external document, so inserting a block may change later generated IDs; persistence must treat the parsed revision as the source of truth rather than assuming external IDs are stable.

The v1.0 block set is:

| Block | External representation | Internal representation |
| --- | --- | --- |
| Section heading | `@heading` one-line attribute | `ArticleSection.heading` |
| Text | `@text` … `@end-text` | `TextBlock` |
| Image | `@image` … `@end-image` | `ImageBlock` with `ImageAsset` |

A heading is deliberately a section attribute in v1.0, not a block. A future inline or interleavable heading block would require an explicit format version change and a corresponding internal model change.

## 10. Image rules

The image property names are deliberately explicit:

| Property | Required in v1.0 syntax | Internal field | Rule |
| --- | --- | --- | --- |
| `download-url` | Yes | `ImageAsset.downloadUrl` | Absolute HTTP(S) URL; used only for obtaining bytes |
| `source-url` | No | `ImageAsset.sourceUrl` | Absolute HTTP(S) attribution/source URL when present |
| `source-name` | No | `ImageAsset.sourceName` | Non-blank source label when present |
| `credit` | No | `ImageAsset.credit` | Non-blank attribution text when present |
| `caption` | No | `ImageAsset.caption` | Non-blank descriptive caption when present |

The parser must reject a missing `download-url`, a duplicate property, an unknown property, an empty property value, or an invalid URL. It must not copy `download-url` into `source-url`, use `source-url` as a fallback download location, or merge the two fields.

Image MIME type, dimensions, byte size, local file reference, processing status, and validation status are not represented in the external v1.0 text. They are produced by the media pipeline after parsing and are therefore not fields that an AI content generator should fabricate.

## 11. Context and ordering validation

The following ordering rules are deterministic:

1. The version declaration is first.
2. Article metadata directives occur before the cover and sections.
3. The cover occurs at most once and before the first section.
4. A section begins with `@section` and continues until the next section or end of input.
5. `@heading` is allowed only once per section and only before its first block.
6. `@text` and `@image` are allowed only inside a section.
7. Image properties are allowed only between `@image`/`@cover` and their matching terminator.
8. A terminator must match the currently open block type.
9. Singleton metadata (`title`, `excerpt`, `category`, `cover`) may appear once only.
10. `tag` may repeat, but exact duplicates are errors.

## 12. Diagnostics

Diagnostics are deterministic records with a machine-readable code, human-readable message, one-based line when available, and optional column/path when the parser can identify them. A parser must continue collecting independent diagnostics where safe, but it must not manufacture an Article success result when a structural error prevents a reliable model.

| Code | Condition |
| --- | --- |
| `EMPTY_INPUT` | No non-blank content exists |
| `MISSING_VERSION` | First non-blank line is absent or is not the version declaration |
| `UNSUPPORTED_VERSION` | Version declaration is syntactically present but not `1.0` |
| `MALFORMED_VERSION` | `@article` line has an invalid version shape |
| `UNKNOWN_DIRECTIVE` | Column-one directive is not defined by v1.0 |
| `MALFORMED_DIRECTIVE` | A directive has invalid trailing content or cannot be lexed |
| `MISSING_REQUIRED_FIELD` | A required one-line value or required image property is absent |
| `DUPLICATE_SINGLETON` | `title`, `excerpt`, `category`, or `cover` occurs more than once |
| `DUPLICATE_TAG` | The same exact tag occurs more than once |
| `INVALID_URL` | URL is not an absolute valid HTTP(S) URI under Section 7 |
| `PROPERTY_WITHOUT_VALUE` | A recognized property has no non-blank value |
| `MALFORMED_PROPERTY` | A property line has no delimiter colon or an invalid key shape |
| `UNKNOWN_PROPERTY` | An image block contains a property not defined in v1.0 |
| `BLOCK_OUTSIDE_SECTION` | `@text` or `@image` appears before any section |
| `MALFORMED_IMAGE` | Image structure is empty, duplicated, mismatched, or otherwise invalid |
| `MISSING_IMAGE_URL` | An image or cover closes without `download-url` |
| `MALFORMED_SECTION` | A section directive or section-level structure is invalid |
| `DUPLICATE_HEADING` | A section contains more than one heading |
| `HEADING_AFTER_BLOCK` | A section heading occurs after content has started |
| `EMPTY_TEXT_BLOCK` | A text block contains no non-whitespace character |
| `UNTERMINATED_TEXT_BLOCK` | EOF occurs before `@end-text` |
| `UNTERMINATED_IMAGE_BLOCK` | EOF occurs before `@end-image` |
| `UNTERMINATED_COVER_BLOCK` | EOF occurs before `@end-cover` |
| `INVALID_ESCAPING` | A text line uses an unsupported leading backslash escape |
| `MALFORMED_VALUE` | A required scalar value line is missing or structurally invalid |

A diagnostic line refers to the one-based normalized input line. A diagnostic column, when available, is one-based. A path may use values such as `metadata.title`, `sections[0].blocks[1].asset.downloadUrl`.

## 13. Unknown and future directives

Unknown directives are errors in v1.0, not silently ignored content. This prevents a document from appearing valid while losing an image, quote, list, embed, or other future block. A future format version may define new directives and a migration path. A v1.0 parser must not guess the meaning of `@quote`, `@list`, `@embed`, `@video`, `@category-list`, or any other unknown directive.

Future block types should follow the same explicit start/content/end pattern and be added under a new minor or major version according to compatibility impact. A new optional metadata field may be introduced in a backward-compatible minor version only if a v1.0 parser can reject it clearly and a migration policy exists. Existing v1.0 documents must retain their meaning unchanged.

## 14. Complete valid example

```text
@article version: 1.0

@title
5 Cara Menjaga Fokus Saat Bekerja dari Rumah

@excerpt
Kebiasaan sederhana ini membantu menjaga fokus tanpa mengabaikan waktu istirahat.

@category
Lifestyle

@tag
produktivitas
@tag
kerja dari rumah
@tag
kesehatan mental

@cover
download-url: https://cdn.example.com/images/cover.webp?width=1600&quality=85
source-url: https://source.example.com/articles/fokus
source-name: Source Example
credit: Jane Doe
caption: Meja kerja dengan cahaya pagi.
@end-cover

@section
@heading
Mulai dengan target yang realistis
@text
Target yang terlalu banyak membuat pekerjaan terasa tidak terarah.

Mulailah dengan satu atau dua prioritas yang dapat diselesaikan hari ini.
@end-text
@image
download-url: https://cdn.example.com/images/planning.jpg?fit=crop&v=2#hero
source-url: https://source.example.com/images/planning
source-name: Source Example Images
credit: John Doe
caption: Catatan prioritas untuk memulai hari.
@end-image
@text
Setelah prioritas ditentukan, singkirkan notifikasi yang tidak diperlukan selama blok kerja.
@end-text

@section
@heading
Jadwalkan jeda
@text
Jeda singkat membantu memisahkan satu sesi kerja dari sesi berikutnya.
@end-text
@text
Gunakan waktu jeda untuk berdiri dan beristirahat dari layar.
@end-text

@section
@text
Bagian tanpa heading tetap valid pada format v1.0, tetapi platform validation policy dapat menolaknya jika heading diwajibkan.
@end-text
```

## 15. Complete invalid examples

### Missing version

```text
@title
Judul tanpa deklarasi versi
```

Expected diagnostic: `MISSING_VERSION` at line 1.

### Unsupported version

```text
@article version: 2.0
@title
Judul versi mendatang
```

Expected diagnostic: `UNSUPPORTED_VERSION` at line 1. The parser must not parse this as v1.0.

### Missing title

```text
@article version: 1.0
@section
@text
Isi tanpa judul.
@end-text
```

Expected diagnostic: `MISSING_REQUIRED_FIELD` for `metadata.title`.

### Duplicate title

```text
@article version: 1.0
@title
Judul pertama
@title
Judul kedua
```

Expected diagnostic: `DUPLICATE_SINGLETON` at the second `@title`.

### Malformed image and missing download URL

```text
@article version: 1.0
@title
Artikel gambar
@section
@image
source-url: https://source.example/item
caption: Tanpa URL unduhan
@end-image
```

Expected diagnostics: `MISSING_IMAGE_URL` and `MALFORMED_IMAGE` for the image block.

### Invalid URL

```text
@article version: 1.0
@title
Artikel URL
@section
@image
download-url: https://cdn.example/image with space.jpg
@end-image
```

Expected diagnostic: `INVALID_URL` at the `download-url` value.

### Unknown directive

```text
@article version: 1.0
@title
Artikel masa depan
@section
@quote
Kutipan yang belum tersedia di v1.0.
```

Expected diagnostic: `UNKNOWN_DIRECTIVE` at `@quote`.

### Block outside a section

```text
@article version: 1.0
@title
Artikel salah konteks
@text
Blok ini belum memiliki section.
@end-text
```

Expected diagnostic: `BLOCK_OUTSIDE_SECTION` at `@text`.

### Unterminated text block

```text
@article version: 1.0
@title
Artikel terpotong
@section
@text
Isi tidak memiliki terminator.
```

Expected diagnostic: `UNTERMINATED_TEXT_BLOCK` at the opening `@text` or EOF line.

### Invalid escaping

```text
@article version: 1.0
@title
Artikel escape
@section
@text
Baris dengan escape \q yang tidak didefinisikan.
@end-text
```

Expected diagnostic: `INVALID_ESCAPING` at the invalid leading escape only when the backslash is at column 1; a backslash in the middle of a text line is ordinary content. To make the invalid leading case explicit, the content line would be `\q` at column 1.

### Malformed property

```text
@article version: 1.0
@title
Artikel property
@section
@image
download-url https://cdn.example/image.jpg
@end-image
```

Expected diagnostic: `MALFORMED_PROPERTY` because the property delimiter colon is missing.

## 16. Versioning and migration

The current parser registry identifies the supported version as `1.0`. A document must declare its version; there is no implicit legacy version. A future parser may support multiple versions concurrently through `ArticleFormatRegistry`.

A backward-compatible minor version may add optional syntax only when it has an explicit parser, fixture set, and migration policy. A major version is required when existing meaning, terminators, escaping, required fields, or model mapping changes. The application should preserve the source format version with each imported revision so re-parsing and migration remain auditable.

Migration must be explicit. It must report lossy conversions, preserve unknown source data when possible, and never silently map `download-url` to `source-url` or discard unsupported blocks. A v1.0 document remains valid with the same meaning in every future implementation that claims v1.0 support.

## 17. Domain model review

The v1.0 format maps cleanly to the existing extensible model with two narrowly scoped additions:

1. `ArticleMetadata.category: String?` represents the optional scalar category.
2. `ArticleMetadata.tags: List<String>` represents repeatable ordered tags.

`ArticleSection.heading` represents the v1.0 section heading attribute. `TextBlock` and `ImageBlock` preserve order. `ImageAsset.downloadUrl` and `ImageAsset.sourceUrl` remain separate, while `sourceName`, `credit`, and `caption` map directly to image metadata. Generated section/block IDs are internal and deterministic from position.

No parser implementation, UI, WebView integration, IDN Times selector, or browser automation instruction is part of this task.

## 18. Normative implementation checklist

A production v1.0 parser must:

- require the exact machine-readable version declaration;
- normalize line endings and ignore only out-of-block blank lines;
- enforce column-one structural lines;
- parse one-line metadata without ambiguous indentation;
- use explicit `download-url` and `source-url` names;
- require explicit image and text terminators;
- preserve text content and block order;
- generate deterministic section and block IDs;
- reject malformed, duplicate, unknown, unsupported, and unterminated structures with diagnostics;
- validate absolute HTTP(S) URLs without performing network access;
- return diagnostics with line and, when available, column/path;
- avoid interpreting future directives as v1.0 content.

## References

[1]: https://www.rfc-editor.org/rfc/rfc3986 "RFC 3986: Uniform Resource Identifier"
[2]: https://www.unicode.org/versions/Unicode15.0.0/ "Unicode Standard, Version 15.0"
