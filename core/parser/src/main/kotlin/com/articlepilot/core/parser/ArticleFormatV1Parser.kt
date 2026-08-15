package com.articlepilot.core.parser

import com.articlepilot.core.model.Article
import com.articlepilot.core.model.ArticleBlock
import com.articlepilot.core.model.ArticleMetadata
import com.articlepilot.core.model.ArticleSection
import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageAssetId
import com.articlepilot.core.model.ImageBlock
import com.articlepilot.core.model.TextBlock
import java.net.URI
import java.net.URISyntaxException

/**
 * Production parser for the frozen ArticlePilot Article Format v1.0 contract.
 *
 * The parser is deliberately independent from Android, persistence, networking,
 * WebView, and platform automation. It only converts trusted-in-memory text into
 * the core Article model or deterministic diagnostics.
 */
class ArticleFormatV1Parser : ArticleParser {
    override fun parse(source: String): ParseResult = ParserContext(normalize(source)).parse()

    private companion object {
        const val VERSION_LINE = "@article version: 1.0"
        const val END_TEXT = "@end-text"
        const val END_IMAGE = "@end-image"
        const val END_COVER = "@end-cover"

        fun normalize(source: String): List<String> {
            val withoutBom = if (source.startsWith('\uFEFF')) source.drop(1) else source
            return withoutBom
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
        }
    }

    private class ParserContext(private val lines: List<String>) {
        private val diagnostics = mutableListOf<ParseDiagnostic>()
        private var index = 0
        private var title: String? = null
        private var excerpt: String? = null
        private var category: String? = null
        private val tags = mutableListOf<String>()
        private val seenTags = mutableSetOf<String>()
        private var cover: ImageDraft? = null
        private val sections = mutableListOf<SectionDraft>()
        private var hasSeenSection = false

        fun parse(): ParseResult {
            val firstContentLine = lines.indexOfFirst { !it.isBlank() }
            if (firstContentLine < 0) {
                diagnostic(null, "EMPTY_INPUT", "The document contains no non-blank content.")
                return failure()
            }

            index = firstContentLine
            val versionLine = lines[index]
            when {
                versionLine == VERSION_LINE -> index++
                versionLine.startsWith("@article version: ") && versionLine.substringAfter("@article version: ").matches(Regex("\\d+\\.\\d+")) -> {
                    diagnostic(index + 1, "UNSUPPORTED_VERSION", "Only ArticlePilot format version 1.0 is supported.")
                    return failure()
                }
                versionLine.startsWith("@article") -> {
                    diagnostic(index + 1, "MALFORMED_VERSION", "The version declaration must use @article version: <major>.<minor>.")
                    return failure()
                }
                else -> {
                    diagnostic(index + 1, "MISSING_VERSION", "The document must begin with @article version: 1.0.")
                    return failure()
                }
            }

            while (index < lines.size) {
                val line = lines[index]
                when {
                    line.isBlank() -> index++
                    line == "@section" -> parseSection()
                    line == "@cover" -> parseCover()
                    line == "@title" -> parseScalar("title")
                    line == "@excerpt" -> parseScalar("excerpt")
                    line == "@category" -> parseScalar("category")
                    line == "@tag" -> parseTag()
                    line == "@text" -> {
                        diagnostic(index + 1, "BLOCK_OUTSIDE_SECTION", "Text blocks are allowed only inside a section.", path = "document")
                        parseTextBlock("document")
                    }
                    line == "@image" -> {
                        diagnostic(index + 1, "BLOCK_OUTSIDE_SECTION", "Image blocks are allowed only inside a section.", path = "document")
                        index++
                        parseImageProperties(
                            endDirective = END_IMAGE,
                            unterminatedCode = "UNTERMINATED_IMAGE_BLOCK",
                            malformedCode = "MALFORMED_IMAGE",
                            path = "document",
                        )
                    }
                    line.startsWith("@") -> parseUnknownDirective("top level")
                    else -> {
                        diagnostic(index + 1, "MALFORMED_DIRECTIVE", "Unexpected content outside a section.", path = "document")
                        index++
                    }
                }
            }

            if (title == null) {
                diagnostic(null, "MISSING_REQUIRED_FIELD", "Article title is required.", path = "metadata.title")
            }

            return if (diagnostics.isNotEmpty()) failure() else success()
        }

        private fun parseScalar(field: String) {
            val directiveLine = index + 1
            val path = "metadata.$field"
            if (hasSeenSection || cover != null) {
                diagnostic(directiveLine, "MALFORMED_DIRECTIVE", "$field must appear before the cover or first section.", path = path)
            }
            index++
            val valueLine = lines.getOrNull(index)
            if (valueLine == null || valueLine.isBlank() || valueLine.startsWith("@")) {
                diagnostic(directiveLine, "MISSING_REQUIRED_FIELD", "$field requires a non-blank value line.", path = path)
                return
            }

            val value = trimAscii(valueLine)
            val valueLineNumber = index + 1
            index++
            if (value.isEmpty()) {
                diagnostic(valueLineNumber, "MALFORMED_VALUE", "$field cannot be blank.", path = path)
                return
            }

            when (field) {
                "title" -> {
                    if (title != null) {
                        diagnostic(directiveLine, "DUPLICATE_SINGLETON", "Article title may appear only once.", path = path)
                    } else {
                        title = value
                    }
                }
                "excerpt" -> {
                    if (excerpt != null) {
                        diagnostic(directiveLine, "DUPLICATE_SINGLETON", "Article excerpt may appear only once.", path = path)
                    } else {
                        excerpt = value
                    }
                }
                "category" -> {
                    if (category != null) {
                        diagnostic(directiveLine, "DUPLICATE_SINGLETON", "Article category may appear only once.", path = path)
                    } else {
                        category = value
                    }
                }
            }
        }

        private fun parseTag() {
            val directiveLine = index + 1
            if (hasSeenSection || cover != null) {
                diagnostic(directiveLine, "MALFORMED_DIRECTIVE", "tag must appear before the cover or first section.", path = "metadata.tags")
            }
            index++
            val valueLine = lines.getOrNull(index)
            if (valueLine == null || valueLine.isBlank() || valueLine.startsWith("@")) {
                diagnostic(directiveLine, "MISSING_REQUIRED_FIELD", "tag requires a non-blank value line.", path = "metadata.tags")
                return
            }

            val value = trimAscii(valueLine)
            val valueLineNumber = index + 1
            index++
            if (value.isEmpty()) {
                diagnostic(valueLineNumber, "MALFORMED_VALUE", "tag cannot be blank.", path = "metadata.tags")
            } else if (!seenTags.add(value)) {
                diagnostic(directiveLine, "DUPLICATE_TAG", "The same tag may not appear more than once.", path = "metadata.tags")
            } else {
                tags += value
            }
        }

        private fun parseCover() {
            val directiveLine = index + 1
            if (hasSeenSection) {
                diagnostic(directiveLine, "MALFORMED_DIRECTIVE", "Cover must appear before the first section.", path = "cover")
            } else if (cover != null) {
                diagnostic(directiveLine, "DUPLICATE_SINGLETON", "Cover may appear only once.", path = "cover")
            }

            index++
            val parsed = parseImageProperties(
                endDirective = END_COVER,
                unterminatedCode = "UNTERMINATED_COVER_BLOCK",
                malformedCode = "MALFORMED_IMAGE",
                path = "cover",
            )
            if (cover == null && parsed != null) cover = parsed
        }

        private fun parseSection() {
            hasSeenSection = true
            val sectionNumber = sections.size + 1
            index++
            val draft = SectionDraft()

            while (index < lines.size) {
                val line = lines[index]
                when {
                    line.isBlank() -> index++
                    line == "@section" -> break
                    line == "@heading" -> parseHeading(draft)
                    line == "@title" -> parseScalar("title")
                    line == "@excerpt" -> parseScalar("excerpt")
                    line == "@category" -> parseScalar("category")
                    line == "@tag" -> parseTag()
                    line == "@cover" -> parseCover()
                    line == "@text" -> {
                        val text = parseTextBlock("sections[${sectionNumber - 1}]")
                        if (text != null) draft.blocks += TextDraft(text)
                    }
                    line == "@image" -> {
                        val blockNumber = draft.blocks.size + 1
                        index++
                        val image = parseImageProperties(
                            endDirective = END_IMAGE,
                            unterminatedCode = "UNTERMINATED_IMAGE_BLOCK",
                            malformedCode = "MALFORMED_IMAGE",
                            path = "sections[${sectionNumber - 1}].blocks[${blockNumber - 1}].asset",
                        )
                        if (image != null) draft.blocks += ImageDraftBlock(image)
                    }
                    line.startsWith("@") -> parseUnknownDirective("section")
                    else -> {
                        diagnostic(index + 1, "MALFORMED_DIRECTIVE", "Unexpected content in section; use an explicit block directive.", path = "sections[${sectionNumber - 1}]")
                        index++
                    }
                }
            }
            sections += draft
        }

        private fun parseHeading(draft: SectionDraft) {
            val directiveLine = index + 1
            if (draft.heading != null) {
                diagnostic(directiveLine, "DUPLICATE_HEADING", "A section may contain only one heading.", path = sectionPath("heading"))
            }
            if (draft.blocks.isNotEmpty()) {
                diagnostic(directiveLine, "HEADING_AFTER_BLOCK", "A section heading must appear before its first block.", path = sectionPath("heading"))
            }

            index++
            val valueLine = lines.getOrNull(index)
            if (valueLine == null || valueLine.isBlank() || valueLine.startsWith("@")) {
                diagnostic(directiveLine, "MISSING_REQUIRED_FIELD", "heading requires a non-blank value line.", path = sectionPath("heading"))
                return
            }

            val value = trimAscii(valueLine)
            index++
            if (value.isEmpty()) {
                diagnostic(index, "MALFORMED_VALUE", "heading cannot be blank.", path = sectionPath("heading"))
            } else if (draft.heading == null) {
                draft.heading = value
            }
        }

        private fun parseTextBlock(pathPrefix: String): String? {
            val directiveLine = index + 1
            index++
            val content = mutableListOf<String>()
            var terminated = false

            while (index < lines.size) {
                val line = lines[index]
                when {
                    line == END_TEXT -> {
                        index++
                        terminated = true
                        break
                    }
                    line == "\\@end-text" -> {
                        content += "@end-text"
                        index++
                    }
                    line.startsWith("\\") && line.length > 1 && line[1] == '\\' -> {
                        content += line.drop(1)
                        index++
                    }
                    line.startsWith("\\") -> {
                        diagnostic(index + 1, "INVALID_ESCAPING", "Only \\@ and \\\\ are valid leading text escapes.", path = pathPrefix)
                        content += line
                        index++
                    }
                    else -> {
                        content += line
                        index++
                    }
                }
            }

            if (!terminated) {
                diagnostic(directiveLine, "UNTERMINATED_TEXT_BLOCK", "Text block must end with @end-text.", path = pathPrefix)
            }
            val text = content.joinToString("\n")
            if (text.isBlank()) {
                diagnostic(directiveLine, "EMPTY_TEXT_BLOCK", "Text block must contain non-whitespace content.", path = pathPrefix)
                return null
            }
            return text
        }

        private fun parseImageProperties(
            endDirective: String,
            unterminatedCode: String,
            malformedCode: String,
            path: String,
        ): ImageDraft? {
            val properties = linkedMapOf<String, String>()
            var terminated = false
            var sawDownloadProperty = false
            var sawMalformedProperty = false
            var sawPropertyWithoutValue = false
            var sawAnyProperty = false
            val propertyLines = mutableMapOf<String, Int>()
            val propertyColumns = mutableMapOf<String, Int>()

            while (index < lines.size) {
                val lineNumber = index + 1
                val line = lines[index]
                when {
                    line == endDirective -> {
                        index++
                        terminated = true
                        break
                    }
                    line.isBlank() -> {
                        diagnostic(lineNumber, "MALFORMED_PROPERTY", "Image properties cannot be blank.", path = path)
                        sawMalformedProperty = true
                        index++
                    }
                    line.startsWith("@") -> {
                        diagnostic(lineNumber, malformedCode, "Image block has an unexpected directive; expected $endDirective.", path = path)
                        index++
                    }
                    else -> {
                        sawAnyProperty = true
                        val colon = line.indexOf(':')
                        if (colon <= 0) {
                            diagnostic(lineNumber, "MALFORMED_PROPERTY", "Image property must use key: value syntax.", path = path)
                            sawMalformedProperty = true
                            index++
                            continue
                        }

                        val key = line.substring(0, colon).trim()
                        if (key in IMAGE_PROPERTIES) {
                            propertyLines.putIfAbsent(key, lineNumber)
                            propertyColumns.putIfAbsent(key, firstValueColumn(line, colon) ?: (colon + 2))
                        }
                        val rawValue = line.substring(colon + 1)
                        val value = trimAscii(rawValue)
                        val propertyPath = "$path.$key"
                        val valueColumn = firstValueColumn(line, colon)

                        if (key.any { it == ' ' || it == '\t' }) {
                            diagnostic(lineNumber, "MALFORMED_PROPERTY", "Image property key must be a single lowercase token before the colon.", valueColumn, propertyPath)
                            sawMalformedProperty = true
                        } else if (key !in IMAGE_PROPERTIES) {
                            diagnostic(lineNumber, "UNKNOWN_PROPERTY", "Unknown image property '$key'.", valueColumn, propertyPath)
                        } else if (properties.containsKey(key)) {
                            diagnostic(lineNumber, "DUPLICATE_SINGLETON", "Image property '$key' may appear only once.", valueColumn, propertyPath)
                        } else if (value.isEmpty()) {
                            diagnostic(lineNumber, "PROPERTY_WITHOUT_VALUE", "Image property '$key' requires a value.", valueColumn, propertyPath)
                            sawPropertyWithoutValue = true
                        } else {
                            properties[key] = value
                            if (key == "download-url") sawDownloadProperty = true
                        }
                        index++
                    }
                }
            }

            if (!terminated) {
                diagnostic(index.coerceAtMost(lines.size).coerceAtLeast(1), unterminatedCode, "Image block must end with $endDirective.", path = path)
            }

            val downloadUrl = properties["download-url"]
            if (downloadUrl == null) {
                if (!sawMalformedProperty || properties.isNotEmpty()) {
                    diagnostic(index.coerceAtMost(lines.size).coerceAtLeast(1), "MISSING_IMAGE_URL", "Image block requires download-url.", path = "$path.downloadUrl")
                }
                if (!sawMalformedProperty && !sawPropertyWithoutValue && !sawDownloadProperty && sawAnyProperty) {
                    diagnostic(index.coerceAtMost(lines.size).coerceAtLeast(1), malformedCode, "Image block is missing its required download-url property.", path = path)
                }
            } else if (!isValidHttpUrl(downloadUrl)) {
                val urlLine = propertyLines["download-url"]
                diagnostic(urlLine ?: index, "INVALID_URL", "download-url must be an absolute HTTP(S) URL.", column = propertyColumns["download-url"], path = "$path.downloadUrl")
            }

            val sourceUrl = properties["source-url"]
            if (sourceUrl != null && !isValidHttpUrl(sourceUrl)) {
                val urlLine = propertyLines["source-url"]
                diagnostic(urlLine ?: index, "INVALID_URL", "source-url must be an absolute HTTP(S) URL.", column = propertyColumns["source-url"], path = "$path.sourceUrl")
            }

            if (downloadUrl == null) return null
            return ImageDraft(
                downloadUrl = downloadUrl,
                sourceUrl = sourceUrl,
                sourceName = properties["source-name"],
                credit = properties["credit"],
                caption = properties["caption"],
            )
        }

        private fun parseUnknownDirective(context: String) {
            val lineNumber = index + 1
            val directive = lines[index].substringBefore(' ').ifEmpty { lines[index] }
            diagnostic(lineNumber, "UNKNOWN_DIRECTIVE", "Unknown directive '$directive' in $context.", path = "document")
            index++
            // Consume the non-directive payload of an unknown future block so one
            // unsupported construct does not create cascading nonsense diagnostics.
            while (index < lines.size && lines[index].isNotBlank() && !lines[index].startsWith("@")) index++
        }

        private fun success(): ParseResult.Success {
            val articleSections = sections.mapIndexed { sectionIndex, section ->
                ArticleSection(
                    id = "section-${sectionIndex + 1}",
                    heading = section.heading,
                    blocks = section.blocks.mapIndexed { blockIndex, block ->
                        val blockId = "section-${sectionIndex + 1}-block-${blockIndex + 1}"
                        when (block) {
                            is TextDraft -> TextBlock(id = blockId, text = block.text)
                            is ImageDraftBlock -> ImageBlock(
                                id = blockId,
                                asset = block.image.toAsset(blockId),
                            )
                        }
                    },
                )
            }
            val metadata = ArticleMetadata(
                title = requireNotNull(title),
                excerpt = excerpt,
                category = category,
                tags = tags.toList(),
            )
            return ParseResult.Success(
                article = Article(
                    formatVersion = ArticleFormatVersions.CURRENT,
                    metadata = metadata,
                    cover = cover?.toAsset("cover"),
                    sections = articleSections,
                ),
                sourceFormatVersion = ArticleFormatVersions.CURRENT,
            )
        }

        private fun failure(): ParseResult.Failure = ParseResult.Failure(diagnostics.toList())

        private fun diagnostic(
            line: Int?,
            code: String,
            message: String,
            column: Int? = null,
            path: String? = null,
        ) {
            diagnostics += ParseDiagnostic(line = line, code = code, message = message, column = column, path = path)
        }

        private fun sectionPath(suffix: String): String {
            val sectionIndex = sections.size
            return "sections[$sectionIndex].$suffix"
        }

        private fun trimAscii(value: String): String = value.trim(' ', '\t')

        private fun firstValueColumn(line: String, colon: Int): Int? {
            val offset = line.substring(colon + 1).indexOfFirst { it != ' ' && it != '\t' }
            return if (offset < 0) colon + 2 else colon + 2 + offset
        }

        private fun isValidHttpUrl(value: String): Boolean {
            if (value.any { it.code <= 0x20 || it.code == 0x7f }) return false
            return try {
                val uri = URI(value)
                val scheme = uri.scheme?.lowercase()
                !uri.isOpaque && scheme in setOf("http", "https") && !uri.rawAuthority.isNullOrEmpty()
            } catch (_: URISyntaxException) {
                false
            }
        }

        private data class SectionDraft(
            var heading: String? = null,
            val blocks: MutableList<BlockDraft> = mutableListOf(),
        )

        private sealed interface BlockDraft
        private data class TextDraft(val text: String) : BlockDraft
        private data class ImageDraftBlock(val image: ImageDraft) : BlockDraft

        private data class ImageDraft(
            val downloadUrl: String,
            val sourceUrl: String? = null,
            val sourceName: String? = null,
            val credit: String? = null,
            val caption: String? = null,
        ) {
            fun toAsset(id: String): ImageAsset = ImageAsset(
                id = ImageAssetId(id),
                downloadUrl = downloadUrl,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                credit = credit,
                caption = caption,
            )
        }

        private companion object {
            val IMAGE_PROPERTIES = setOf("download-url", "source-url", "source-name", "credit", "caption")
        }
    }
}
