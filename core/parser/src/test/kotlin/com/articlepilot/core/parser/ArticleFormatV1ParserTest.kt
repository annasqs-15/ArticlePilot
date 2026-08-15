package com.articlepilot.core.parser

import com.articlepilot.core.model.ImageBlock
import com.articlepilot.core.model.TextBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArticleFormatV1ParserTest {
    private val parser = ArticleFormatV1Parser()

    @Test
    fun manifest_cases_match_expected_success_or_diagnostic_codes() {
        val manifest = Json.parseToJsonElement(resource("manifest.json")).jsonObject
        val cases = manifest.getValue("cases").jsonArray

        cases.forEach { caseElement ->
            val case = caseElement.jsonObject
            val path = case.getValue("path").jsonPrimitive.content
            val expectedOutcome = case.getValue("outcome").jsonPrimitive.content
            val expectedCodes = case.getValue("diagnosticCodes").jsonArray.map { it.jsonPrimitive.content }
            val result = parser.parse(resource(path))

            when (expectedOutcome) {
                "success" -> {
                    val success = success(result, path)
                    assertEquals("1.0", success.sourceFormatVersion, path)
                }
                "failure" -> {
                    val failure = assertIs<ParseResult.Failure>(result, path)
                    assertEquals(expectedCodes, failure.diagnostics.map { it.code }, path)
                }
                else -> error("Unknown fixture outcome '$expectedOutcome' for $path")
            }
        }
    }

    @Test
    fun parser_maps_article_metadata_and_cover_semantically() {
        val result = success(parser.parse(resource("valid/with-cover.articlepilot")), "with-cover")
        val article = result.article

        assertEquals("Artikel dengan Cover", article.metadata.title)
        assertEquals(null, article.metadata.excerpt)
        assertEquals(null, article.metadata.category)
        assertEquals(emptyList(), article.metadata.tags)
        assertEquals("https://cdn.example.com/cover.webp?width=1200&quality=85", article.cover?.downloadUrl)
        assertEquals("https://source.example.com/cover", article.cover?.sourceUrl)
        assertEquals("Example Source", article.cover?.sourceName)
        assertEquals("A. Writer", article.cover?.credit)
        assertEquals("Pemandangan pada pagi hari.", article.cover?.caption)
        assertEquals(listOf("section-1"), article.sections.map { it.id })
        assertEquals("Pembuka", article.sections.single().heading)
        assertEquals("Cover berada di luar urutan block section.", (article.sections.single().blocks.single() as TextBlock).text)
    }

    @Test
    fun parser_maps_full_metadata_and_image_fields_without_conflating_urls() {
        val result = success(parser.parse(resource("valid/indonesian-text.articlepilot")), "indonesian-text")
        val article = result.article
        val section = article.sections.single()
        val image = assertIs<ImageBlock>(section.blocks[1])

        assertEquals("Tips Mengatur Waktu di Tengah Kesibukan", article.metadata.title)
        assertEquals("Beberapa langkah sederhana dapat membantu menjaga pekerjaan tetap teratur.", article.metadata.excerpt)
        assertEquals("Produktivitas", article.metadata.category)
        assertEquals(listOf("bahasa Indonesia"), article.metadata.tags)
        assertEquals("Susun prioritas harian", section.heading)
        assertEquals("Mulailah dengan mencatat pekerjaan yang paling penting. Jangan lupa menyisihkan waktu untuk beristirahat agar keputusan tetap jernih.", (section.blocks[0] as TextBlock).text)
        assertEquals("https://cdn.example.com/indonesia.jpg", image.asset.downloadUrl)
        assertEquals("https://source.example.com/indonesia", image.asset.sourceUrl)
        assertEquals("Redaksi Example", image.asset.credit)
        assertEquals("Catatan prioritas di atas meja kerja.", image.asset.caption)
        assertEquals(null, image.asset.sourceName)
    }

    @Test
    fun parser_preserves_multiline_unicode_and_indonesian_content_exactly() {
        val multiline = success(parser.parse(resource("valid/multiline-text.articlepilot")), "multiline-text")
        assertEquals(
            "Paragraf pertama memiliki dua kalimat.\n\nParagraf kedua tetap berada di dalam text block yang sama.\nBaris ini melanjutkan paragraf kedua.",
            (multiline.article.sections.single().blocks.single() as TextBlock).text,
        )

        val unicode = success(parser.parse(resource("valid/unicode-text.articlepilot")), "unicode-text")
        assertEquals("Café, naïve, 東京, dan 한국어", unicode.article.metadata.title)
        assertEquals("Unicode text: café — 東京 — 한국어 — العربية — Ελληνικά.", (unicode.article.sections.single().blocks.single() as TextBlock).text)
    }

    @Test
    fun parser_preserves_section_and_block_order_and_generates_deterministic_ids() {
        val interleaved = success(parser.parse(resource("valid/interleaved-text-image-text.articlepilot")), "interleaved")
        val blocks = interleaved.article.sections.single().blocks

        assertEquals(listOf("section-1-block-1", "section-1-block-2", "section-1-block-3"), blocks.map { it.id })
        assertIs<TextBlock>(blocks[0])
        assertIs<ImageBlock>(blocks[1])
        assertIs<TextBlock>(blocks[2])
        assertEquals("Teks sebelum gambar.", (blocks[0] as TextBlock).text)
        assertEquals("https://cdn.example.com/photo.jpg?fit=crop&v=4", (blocks[1] as ImageBlock).asset.downloadUrl)
        assertEquals("Teks setelah gambar.", (blocks[2] as TextBlock).text)

        val sections = success(parser.parse(resource("valid/multiple-sections.articlepilot")), "multiple-sections").article.sections
        assertEquals(listOf("section-1", "section-2", "section-3"), sections.map { it.id })
        assertEquals(listOf("Bagian Pertama", "Bagian Kedua", "Bagian Ketiga"), sections.map { it.heading })
    }

    @Test
    fun parser_applies_text_escaping_only_at_column_one() {
        val source = """
            @article version: 1.0
            @title
            Escaping
            @section
            @text
            \@end-text
            \\literal-leading-backslash
            inline \q remains ordinary content
            @end-text
        """.trimIndent()

        val result = success(parser.parse(source), "escaping")
        assertEquals(
            "@end-text\n\\literal-leading-backslash\ninline \\q remains ordinary content",
            (result.article.sections.single().blocks.single() as TextBlock).text,
        )
    }

    @Test
    fun parser_accepts_bom_leading_blank_lines_and_crlf_without_changing_structure() {
        val source = "\uFEFF\r\n\r\n@article version: 1.0\r\n@title\r\nPortable\r\n@section\r\n@text\r\nIsi\r\n@end-text"
        val result = success(parser.parse(source), "bom-crlf")

        assertEquals("Portable", result.article.metadata.title)
        assertEquals("Isi", (result.article.sections.single().blocks.single() as TextBlock).text)
    }

    @Test
    fun parser_reports_source_locations_and_paths_for_invalid_input() {
        val invalidUrl = assertIs<ParseResult.Failure>(parser.parse(resource("invalid/invalid-url.articlepilot")))
        val urlDiagnostic = invalidUrl.diagnostics.single { it.code == "INVALID_URL" }
        assertEquals(6, urlDiagnostic.line)
        assertEquals("sections[0].blocks[0].asset.downloadUrl", urlDiagnostic.path)
        assertNotNull(urlDiagnostic.column)

        val invalidEscape = assertIs<ParseResult.Failure>(parser.parse(resource("invalid/invalid-escaping.articlepilot")))
        val escapeDiagnostic = invalidEscape.diagnostics.single()
        assertEquals("INVALID_ESCAPING", escapeDiagnostic.code)
        assertEquals(6, escapeDiagnostic.line)
        assertEquals("sections[0]", escapeDiagnostic.path)

        val missingVersion = assertIs<ParseResult.Failure>(parser.parse(resource("invalid/missing-version.articlepilot")))
        assertEquals(1, missingVersion.diagnostics.single().line)
        assertEquals("MISSING_VERSION", missingVersion.diagnostics.single().code)
    }

    @Test
    fun parser_rejects_malformed_version_and_metadata_after_section() {
        val malformedVersion = assertIs<ParseResult.Failure>(parser.parse("@article version: 1.0 extra\n"))
        assertEquals(listOf("MALFORMED_VERSION"), malformedVersion.diagnostics.map { it.code })

        val metadataAfterSection = assertIs<ParseResult.Failure>(
            parser.parse(
                """
                @article version: 1.0
                @title
                Valid title
                @section
                @text
                Body
                @end-text
                @excerpt
                Not allowed here
                """.trimIndent(),
            ),
        )
        assertEquals(listOf("MALFORMED_DIRECTIVE"), metadataAfterSection.diagnostics.map { it.code })
        assertEquals("metadata.excerpt", metadataAfterSection.diagnostics.single().path)
    }

    @Test
    fun parser_does_not_download_or_execute_image_urls() {
        val result = success(parser.parse(resource("valid/urls-with-query-strings.articlepilot")), "urls-with-query-strings")
        val image = assertIs<ImageBlock>(result.article.sections.single().blocks.single()).asset

        assertTrue(image.localFileReference == null)
        assertEquals("https://images.example.com/photo.webp?width=1600&height=900&quality=85#hero", image.downloadUrl)
        assertEquals("https://source.example.com/gallery/item?id=42&view=full", image.sourceUrl)
    }

    private fun success(result: ParseResult, context: String): ParseResult.Success = when (result) {
        is ParseResult.Success -> result
        is ParseResult.Failure -> error("$context diagnostics=${result.diagnostics}")
    }

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResourceAsStream("/$path")) { "Missing fixture resource: $path" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
}
