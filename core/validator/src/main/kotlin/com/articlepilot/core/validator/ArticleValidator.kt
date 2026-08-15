package com.articlepilot.core.validator

import com.articlepilot.core.model.Article

interface ArticleValidator {
    fun validate(article: Article, policy: ValidationPolicy): ValidationResult
}

interface ValidationPolicy {
    val id: String
    val version: String
}

data class ValidationResult(
    val issues: List<ValidationIssue>,
) {
    val isValid: Boolean
        get() = issues.none { it.severity == ValidationSeverity.ERROR }
}

data class ValidationIssue(
    val code: String,
    val severity: ValidationSeverity,
    val message: String,
    val path: String? = null,
)

enum class ValidationSeverity {
    ERROR,
    WARNING,
    INFO,
}

object GenericArticleValidationPolicy : ValidationPolicy {
    override val id: String = "generic-article"
    override val version: String = "1"
}

/** IDN Times-specific policy belongs in a separate adapter/profile module. */
interface PlatformValidationPolicy : ValidationPolicy
