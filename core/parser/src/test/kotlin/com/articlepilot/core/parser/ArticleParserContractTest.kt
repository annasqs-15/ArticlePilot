package com.articlepilot.core.parser

import com.articlepilot.core.model.Article
import com.articlepilot.core.model.ArticleMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleParserContractTest {
    @Test
    fun parser_result_success_carries_structured_article_and_version() {
        val parser = object : ArticleParser {
            override fun parse(source: String): ParseResult = ParseResult.Success(
                article = Article(
                    formatVersion = "1.0",
                    metadata = ArticleMetadata(title = source),
                ),
                sourceFormatVersion = "1.0",
            )
        }

        val result = parser.parse("Title") as ParseResult.Success

        assertEquals("Title", result.article.metadata.title)
        assertEquals("1.0", result.sourceFormatVersion)
    }

    @Test
    fun parser_result_failure_preserves_diagnostics() {
        val result = ParseResult.Failure(
            diagnostics = listOf(ParseDiagnostic(line = 4, code = "MISSING_TITLE", message = "Title is required")),
        )

        assertTrue(result.diagnostics.single().message.contains("required"))
        assertEquals(4, result.diagnostics.single().line)
    }
}
