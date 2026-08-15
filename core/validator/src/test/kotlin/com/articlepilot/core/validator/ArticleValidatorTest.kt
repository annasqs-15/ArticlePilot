package com.articlepilot.core.validator

import com.articlepilot.core.model.Article
import com.articlepilot.core.model.ArticleBlock
import com.articlepilot.core.model.ArticleMetadata
import com.articlepilot.core.model.ArticleSection
import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageAssetId
import com.articlepilot.core.model.ImageBlock
import com.articlepilot.core.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleValidatorTest {
    private val engine = ArticleValidationEngine()
    private val genericPolicy = GenericArticleValidationPolicy

    @Test
    fun normal_article_with_multiple_sections_and_ordered_blocks_is_valid() {
        val article = article(
            title = "Panduan Produktiv dalam Bahasa Indonesia",
            sections = listOf(
                section(
                    id = "section-0",
                    heading = "Mulai dari prioritas",
                    text("section-0-block-0", "Tentukan satu tujuan yang realistis."),
                    image("section-0-block-1", validAsset("asset-0", caption = "Catatan prioritas.")),
                    text("section-0-block-2", "Kemudian kerjakan tanpa berpindah konteks."),
                ),
                section(
                    id = "section-1",
                    heading = "Jaga ritme",
                    text("section-1-block-0", "Istirahat singkat membantu menjaga fokus."),
                ),
            ),
        )

        val result = engine.validate(article, genericPolicy)

        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun unicode_and_indonesian_text_are_valid_without_network_access() {
        val article = article(
            title = "Kopi, fokus, dan pagi yang tenang — édisi khusus",
            sections = listOf(
                section(
                    id = "section-0",
                    text("section-0-block-0", "Pagi yang tenang ☕ membantu membaca dengan fokus."),
                    image(
                        "section-0-block-1",
                        validAsset(
                            id = "asset-unicode",
                            downloadUrl = "https://invalid.example.test/image.jpg?query=unicode&v=1#hero",
                        ),
                    ),
                ),
            ),
        )

        val result = engine.validate(article, genericPolicy)

        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun image_only_and_text_only_sections_are_valid_publishable_content() {
        val imageOnly = article(
            title = "Image article",
            sections = listOf(
                section(
                    id = "section-0",
                    image("section-0-block-0", validAsset("asset-image-only")),
                ),
            ),
        )
        val textOnly = article(
            title = "Text article",
            sections = listOf(
                section(
                    id = "section-0",
                    text("section-0-block-0", "Text-only content remains publishable."),
                ),
            ),
        )

        assertTrue(engine.validate(imageOnly, genericPolicy).isValid)
        assertTrue(engine.validate(textOnly, genericPolicy).isValid)
    }

    @Test
    fun blank_title_is_an_error_at_metadata_path() {
        val result = engine.validate(
            article(title = "  ", sections = listOf(validTextSection())),
            genericPolicy,
        )

        assertEquals(listOf("EMPTY_TITLE"), result.issues.map { it.code })
        assertEquals(listOf("metadata.title"), result.issues.map { it.path })
        assertFalse(result.isValid)
    }

    @Test
    fun article_without_sections_is_empty_article() {
        val result = engine.validate(article(title = "Title"), genericPolicy)

        assertEquals(listOf("EMPTY_ARTICLE"), result.issues.map { it.code })
        assertEquals(listOf("sections"), result.issues.map { it.path })
        assertFalse(result.isValid)
    }

    @Test
    fun section_without_blocks_is_invalid_but_heading_is_not_required() {
        val result = engine.validate(
            article(
                title = "Title",
                sections = listOf(ArticleSection(id = "section-0")),
            ),
            genericPolicy,
        )

        assertEquals(listOf("EMPTY_SECTION"), result.issues.map { it.code })
        assertEquals(listOf("sections[0]"), result.issues.map { it.path })
        assertFalse(result.isValid)
    }

    @Test
    fun blank_text_block_and_blank_heading_are_invalid() {
        val result = engine.validate(
            article(
                title = "Title",
                sections = listOf(
                    section(
                        id = "section-0",
                        heading = " \t",
                        text("section-0-block-0", "\n  "),
                    ),
                ),
            ),
            genericPolicy,
        )

        assertEquals(listOf("EMPTY_HEADING", "EMPTY_TEXT"), result.issues.map { it.code })
        assertEquals(
            listOf("sections[0].heading", "sections[0].blocks[0]"),
            result.issues.map { it.path },
        )
        assertFalse(result.isValid)
    }

    @Test
    fun missing_download_url_is_generic_image_error() {
        val result = engine.validate(
            article(
                title = "Title",
                sections = listOf(
                    section(
                        id = "section-0",
                        image(
                            "section-0-block-0",
                            ImageAsset(id = ImageAssetId("asset-0"), downloadUrl = ""),
                        ),
                    ),
                ),
            ),
            genericPolicy,
        )

        assertEquals(listOf("MISSING_IMAGE_DOWNLOAD_URL"), result.issues.map { it.code })
        assertEquals(
            listOf("sections[0].blocks[0].asset.downloadUrl"),
            result.issues.map { it.path },
        )
        assertFalse(result.isValid)
    }

    @Test
    fun generic_policy_keeps_optional_attribution_optional() {
        val result = engine.validate(
            article(
                title = "Title",
                sections = listOf(
                    section(
                        id = "section-0",
                        image(
                            "section-0-block-0",
                            validAsset("asset-0", caption = null, sourceUrl = null),
                        ),
                    ),
                ),
            ),
            genericPolicy,
        )

        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun selected_policy_can_require_source_url_and_caption() {
        val policy = AttributionPolicy(requireSourceUrl = true, requireCaption = true)
        val result = engine.validate(
            article(
                title = "Title",
                sections = listOf(
                    section(
                        id = "section-0",
                        image(
                            "section-0-block-0",
                            validAsset("asset-0", caption = null, sourceUrl = null),
                        ),
                    ),
                ),
            ),
            policy,
        )

        assertEquals(listOf("MISSING_IMAGE_SOURCE_URL", "MISSING_IMAGE_CAPTION"), result.issues.map { it.code })
        assertEquals(
            listOf(
                "sections[0].blocks[0].asset.sourceUrl",
                "sections[0].blocks[0].asset.caption",
            ),
            result.issues.map { it.path },
        )
        assertFalse(result.isValid)
    }

    @Test
    fun policy_can_require_media_facts_without_fabricating_them() {
        val policy = MediaFactsPolicy(
            requireMimeType = true,
            allowedMimeTypes = setOf("image/jpeg"),
            requireFileSizeBytes = true,
            maxFileSizeBytes = 100_000,
            requireDimensions = true,
        )
        val result = engine.validate(
            article(
                title = "Title",
                sections = listOf(
                    section(
                        id = "section-0",
                        image("section-0-block-0", validAsset("asset-0")),
                    ),
                ),
            ),
            policy,
        )

        assertEquals(
            listOf("MISSING_IMAGE_MIME_TYPE", "MISSING_IMAGE_FILE_SIZE", "MISSING_IMAGE_DIMENSIONS"),
            result.issues.map { it.code },
        )
        assertFalse(result.isValid)
    }

    @Test
    fun policy_validates_supplied_media_facts_without_downloading() {
        val policy = MediaFactsPolicy(
            allowedMimeTypes = setOf("image/jpeg"),
            maxFileSizeBytes = 100_000,
        )
        val asset = validAsset("asset-0").copy(
            mimeType = "image/png",
            fileSizeBytes = 100_001,
            width = 0,
            height = 800,
        )
        val result = engine.validate(
            article(
                title = "Title",
                sections = listOf(section("section-0", image("section-0-block-0", asset))),
            ),
            policy,
        )

        assertEquals(
            listOf("INVALID_IMAGE_MIME_TYPE", "IMAGE_FILE_SIZE_EXCEEDS_LIMIT", "INVALID_IMAGE_DIMENSIONS"),
            result.issues.map { it.code },
        )
        assertEquals(
            listOf(
                "sections[0].blocks[0].asset.mimeType",
                "sections[0].blocks[0].asset.fileSizeBytes",
                "sections[0].blocks[0].asset.dimensions",
            ),
            result.issues.map { it.path },
        )
        assertFalse(result.isValid)
    }

    @Test
    fun blank_optional_image_metadata_is_an_internal_consistency_error() {
        val result = engine.validate(
            article(
                title = "Title",
                sections = listOf(
                    section(
                        id = "section-0",
                        image(
                            "section-0-block-0",
                            validAsset(
                                id = "asset-0",
                                sourceUrl = "  ",
                                sourceName = "\t",
                                credit = " ",
                                caption = "\n",
                            ),
                        ),
                    ),
                ),
            ),
            genericPolicy,
        )

        assertEquals(
            listOf("BLANK_IMAGE_METADATA", "BLANK_IMAGE_METADATA", "BLANK_IMAGE_METADATA", "BLANK_IMAGE_METADATA"),
            result.issues.map { it.code },
        )
        assertEquals(
            listOf(
                "sections[0].blocks[0].asset.sourceUrl",
                "sections[0].blocks[0].asset.caption",
                "sections[0].blocks[0].asset.sourceName",
                "sections[0].blocks[0].asset.credit",
            ),
            result.issues.map { it.path },
        )
        assertFalse(result.isValid)
    }

    @Test
    fun multiple_errors_are_returned_in_document_order() {
        val result = engine.validate(
            Article(
                formatVersion = "1.0",
                metadata = ArticleMetadata(title = ""),
                cover = ImageAsset(id = ImageAssetId("cover"), downloadUrl = ""),
                sections = listOf(
                    ArticleSection(id = "section-0"),
                    section(
                        id = "section-1",
                        text("section-1-block-0", " "),
                        image(
                            "section-1-block-1",
                            ImageAsset(id = ImageAssetId("asset-1"), downloadUrl = ""),
                        ),
                    ),
                ),
            ),
            genericPolicy,
        )

        assertEquals(
            listOf(
                "EMPTY_TITLE",
                "MISSING_IMAGE_DOWNLOAD_URL",
                "EMPTY_SECTION",
                "EMPTY_TEXT",
                "MISSING_IMAGE_DOWNLOAD_URL",
            ),
            result.issues.map { it.code },
        )
        assertEquals(
            listOf(
                "metadata.title",
                "cover.downloadUrl",
                "sections[0]",
                "sections[1].blocks[0]",
                "sections[1].blocks[1].asset.downloadUrl",
            ),
            result.issues.map { it.path },
        )
        assertFalse(result.isValid)
    }

    @Test
    fun warning_and_info_do_not_make_article_invalid() {
        val policy = object : ValidationPolicy {
            override val id: String = "advisory-policy"
            override val version: String = "1"

            override fun validateArticle(article: Article): List<ValidationIssue> = listOf(
                ValidationIssue(
                    code = "ARTICLE_ADVISORY",
                    severity = ValidationSeverity.WARNING,
                    message = "Article has an advisory warning.",
                    path = "metadata",
                ),
                ValidationIssue(
                    code = "ARTICLE_INFO",
                    severity = ValidationSeverity.INFO,
                    message = "Article passed advisory inspection.",
                    path = "metadata",
                ),
            )
        }

        val result = engine.validate(article(title = "Title", sections = listOf(validTextSection())), policy)

        assertTrue(result.isValid)
        assertEquals(
            listOf(ValidationSeverity.WARNING, ValidationSeverity.INFO),
            result.issues.map { it.severity },
        )
    }

    private fun article(
        title: String,
        sections: List<ArticleSection> = emptyList(),
        cover: ImageAsset? = null,
    ): Article = Article(
        formatVersion = "1.0",
        metadata = ArticleMetadata(title = title),
        cover = cover,
        sections = sections,
    )

    private fun validTextSection(): ArticleSection = section(
        id = "section-0",
        text("section-0-block-0", "Valid text."),
    )

    private fun section(
        id: String,
        vararg blocks: ArticleBlock,
    ): ArticleSection = ArticleSection(id = id, blocks = blocks.toList())

    private fun section(
        id: String,
        heading: String,
        vararg blocks: ArticleBlock,
    ): ArticleSection = ArticleSection(id = id, heading = heading, blocks = blocks.toList())

    private fun text(id: String, value: String): TextBlock = TextBlock(id = id, text = value)

    private fun image(id: String, asset: ImageAsset): ImageBlock = ImageBlock(id = id, asset = asset)

    private fun validAsset(
        id: String,
        downloadUrl: String = "https://example.com/$id.jpg",
        sourceUrl: String? = "https://source.example.com/$id",
        sourceName: String? = "Example Source",
        credit: String? = "Example Credit",
        caption: String? = "Example caption",
    ): ImageAsset = ImageAsset(
        id = ImageAssetId(id),
        downloadUrl = downloadUrl,
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        credit = credit,
        caption = caption,
    )

    private class AttributionPolicy(
        requireSourceUrl: Boolean,
        requireCaption: Boolean,
    ) : PlatformValidationPolicy {
        override val id: String = "test-attribution"
        override val version: String = "1"
        override val imageRequirements = ImageValidationRequirements(
            requireSourceUrl = requireSourceUrl,
            requireCaption = requireCaption,
        )
    }

    private class MediaFactsPolicy(
        requireMimeType: Boolean = false,
        allowedMimeTypes: Set<String> = emptySet(),
        requireFileSizeBytes: Boolean = false,
        maxFileSizeBytes: Long? = null,
        requireDimensions: Boolean = false,
    ) : PlatformValidationPolicy {
        override val id: String = "test-media-facts"
        override val version: String = "1"
        override val imageRequirements = ImageValidationRequirements(
            requireMimeType = requireMimeType,
            allowedMimeTypes = allowedMimeTypes,
            requireFileSizeBytes = requireFileSizeBytes,
            maxFileSizeBytes = maxFileSizeBytes,
            requireDimensions = requireDimensions,
        )
    }
}
