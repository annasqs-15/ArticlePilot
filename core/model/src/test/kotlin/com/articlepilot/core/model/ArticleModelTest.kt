package com.articlepilot.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArticleModelTest {
    @Test
    fun imageAsset_keeps_download_and_source_urls_separate() {
        val asset = ImageAsset(
            id = ImageAssetId("image-1"),
            downloadUrl = "https://cdn.example/image.webp",
            sourceUrl = "https://source.example/story",
        )

        assertEquals("https://cdn.example/image.webp", asset.downloadUrl)
        assertEquals("https://source.example/story", asset.sourceUrl)
    }

    @Test
    fun article_preserves_ordered_sections_and_blocks() {
        val article = Article(
            formatVersion = "1.0-draft",
            metadata = ArticleMetadata(title = "Title"),
            sections = listOf(
                ArticleSection(
                    id = "section-1",
                    blocks = listOf(TextBlock(id = "block-1", text = "Paragraph")),
                ),
            ),
        )

        assertEquals("section-1", article.sections.single().id)
        assertNotNull(article.sections.single().blocks.single() as TextBlock)
    }
}
