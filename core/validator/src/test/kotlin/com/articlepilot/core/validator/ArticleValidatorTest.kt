package com.articlepilot.core.validator

import com.articlepilot.core.model.Article
import com.articlepilot.core.model.ArticleMetadata
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleValidatorTest {
    @Test
    fun validation_result_is_invalid_when_an_error_exists() {
        val result = ValidationResult(
            issues = listOf(
                ValidationIssue(
                    code = "MISSING_TITLE",
                    severity = ValidationSeverity.ERROR,
                    message = "Title is required",
                    path = "metadata.title",
                ),
            ),
        )

        assertFalse(result.isValid)
    }

    @Test
    fun validation_result_allows_warnings() {
        val result = ValidationResult(
            issues = listOf(
                ValidationIssue(
                    code = "SHORT_EXCERPT",
                    severity = ValidationSeverity.WARNING,
                    message = "Excerpt may be too short",
                ),
            ),
        )

        assertTrue(result.isValid)
    }

    @Suppress("UNUSED_VARIABLE")
    private fun domainArticleForPolicyContract(): Article {
        return Article(formatVersion = "1.0", metadata = ArticleMetadata(title = "Title"))
    }
}
