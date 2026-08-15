package com.articlepilot.core.parser

import com.articlepilot.core.model.Article

interface ArticleParser {
    fun parse(source: String): ParseResult
}

sealed interface ParseResult {
    data class Success(
        val article: Article,
        val sourceFormatVersion: String,
    ) : ParseResult

    data class Failure(
        val diagnostics: List<ParseDiagnostic>,
    ) : ParseResult
}

data class ParseDiagnostic(
    val line: Int?,
    val code: String,
    val message: String,
    val column: Int? = null,
    val path: String? = null,
)

interface ArticleFormatRegistry {
    fun parserFor(version: String): ArticleParser?
}

object ArticleFormatVersions {
    const val CURRENT = "1.0"
    val SUPPORTED: Set<String> = setOf(CURRENT)
}
