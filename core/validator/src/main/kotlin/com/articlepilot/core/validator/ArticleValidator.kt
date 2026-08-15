package com.articlepilot.core.validator

import com.articlepilot.core.model.Article
import com.articlepilot.core.model.ArticleBlock
import com.articlepilot.core.model.ArticleSection
import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageBlock
import com.articlepilot.core.model.TextBlock

/**
 * Validation entry point for an already structured Article.
 *
 * This layer deliberately does not parse source text, download media, inspect image
 * bytes, access WebView, or make network requests. Parser diagnostics remain parser
 * concerns; this component evaluates the semantic readiness of the resulting model.
 */
interface ArticleValidator {
    fun validate(article: Article, policy: ValidationPolicy): ValidationResult
}

/**
 * Platform-independent requirements and extension hooks for policy-specific rules.
 *
 * Hooks are called by [ArticleValidationEngine] in document order. Implementations
 * must return diagnostics in a stable order and must not depend on hash-map iteration.
 */
interface ValidationPolicy {
    val id: String
    val version: String

    val imageRequirements: ImageValidationRequirements
        get() = ImageValidationRequirements()

    fun validateArticle(article: Article): List<ValidationIssue> = emptyList()

    fun validateSection(
        article: Article,
        section: ArticleSection,
        sectionIndex: Int,
        path: String,
    ): List<ValidationIssue> = emptyList()

    fun validateBlock(
        article: Article,
        section: ArticleSection,
        block: ArticleBlock,
        sectionIndex: Int,
        blockIndex: Int,
        path: String,
    ): List<ValidationIssue> = emptyList()

    fun validateImage(
        article: Article,
        asset: ImageAsset,
        path: String,
    ): List<ValidationIssue> = emptyList()
}

data class ImageValidationRequirements(
    val requireSourceUrl: Boolean = false,
    val requireCaption: Boolean = false,
    val requireMimeType: Boolean = false,
    val allowedMimeTypes: Set<String> = emptySet(),
    val requireFileSizeBytes: Boolean = false,
    val maxFileSizeBytes: Long? = null,
    val requireDimensions: Boolean = false,
)

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

/** The core v1.0 policy: syntax-level optional attribution remains optional. */
object GenericArticleValidationPolicy : ValidationPolicy {
    override val id: String = "generic-article"
    override val version: String = "1"
}

/** IDN Times-specific policy belongs in a separate adapter/profile module. */
interface PlatformValidationPolicy : ValidationPolicy

/**
 * Deterministic generic Article validator with policy extension points.
 *
 * The traversal order is metadata, article-level policy, cover, then sections and
 * their blocks. This makes diagnostics suitable for a future UI and independent of
 * collection/hash iteration order.
 */
class ArticleValidationEngine : ArticleValidator {
    override fun validate(article: Article, policy: ValidationPolicy): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        validateArticleMetadata(article, issues)
        issues += policy.validateArticle(article)

        article.cover?.let { cover ->
            val path = "cover"
            validateImage(cover, path, policy, article, issues)
        }

        if (article.sections.isEmpty()) {
            issues += issue(
                code = "EMPTY_ARTICLE",
                message = "Article must contain at least one section with publishable content.",
                path = "sections",
            )
        }

        article.sections.forEachIndexed { sectionIndex, section ->
            val sectionPath = "sections[$sectionIndex]"
            validateSection(article, section, sectionIndex, sectionPath, policy, issues)
        }

        return ValidationResult(issues = issues.toList())
    }

    private fun validateArticleMetadata(
        article: Article,
        issues: MutableList<ValidationIssue>,
    ) {
        if (article.metadata.title.isBlank()) {
            issues += issue(
                code = "EMPTY_TITLE",
                message = "Article title must contain non-whitespace content.",
                path = "metadata.title",
            )
        }
    }

    private fun validateSection(
        article: Article,
        section: ArticleSection,
        sectionIndex: Int,
        sectionPath: String,
        policy: ValidationPolicy,
        issues: MutableList<ValidationIssue>,
    ) {
        if (section.blocks.isEmpty()) {
            issues += issue(
                code = "EMPTY_SECTION",
                message = "Section must contain at least one text or image block.",
                path = sectionPath,
            )
        }

        val heading = section.heading
        if (heading != null && heading.isBlank()) {
            issues += issue(
                code = "EMPTY_HEADING",
                message = "Section heading must contain non-whitespace content when present.",
                path = "$sectionPath.heading",
            )
        }

        issues += policy.validateSection(article, section, sectionIndex, sectionPath)

        section.blocks.forEachIndexed { blockIndex, block ->
            val blockPath = "$sectionPath.blocks[$blockIndex]"
            when (block) {
                is TextBlock -> validateTextBlock(block, blockPath, issues)
                is ImageBlock -> validateImage(block.asset, "$blockPath.asset", policy, article, issues)
            }
            issues += policy.validateBlock(article, section, block, sectionIndex, blockIndex, blockPath)
        }
    }

    private fun validateTextBlock(
        block: TextBlock,
        path: String,
        issues: MutableList<ValidationIssue>,
    ) {
        if (block.text.isBlank()) {
            issues += issue(
                code = "EMPTY_TEXT",
                message = "Text block must contain non-whitespace content.",
                path = path,
            )
        }
    }

    private fun validateImage(
        asset: ImageAsset,
        path: String,
        policy: ValidationPolicy,
        article: Article,
        issues: MutableList<ValidationIssue>,
    ) {
        if (asset.downloadUrl.isBlank()) {
            issues += issue(
                code = "MISSING_IMAGE_DOWNLOAD_URL",
                message = "Image asset requires a non-blank download URL.",
                path = "$path.downloadUrl",
            )
        }

        val requirements = policy.imageRequirements
        val sourceUrl = asset.sourceUrl
        if (sourceUrl.isNullOrBlank()) {
            if (requirements.requireSourceUrl) {
                issues += issue(
                    code = "MISSING_IMAGE_SOURCE_URL",
                    message = "The selected validation policy requires an image source URL.",
                    path = "$path.sourceUrl",
                )
            } else if (sourceUrl != null) {
                issues += issue(
                    code = "BLANK_IMAGE_METADATA",
                    message = "Image source URL must not be blank when supplied.",
                    path = "$path.sourceUrl",
                )
            }
        }

        val caption = asset.caption
        if (caption.isNullOrBlank()) {
            if (requirements.requireCaption) {
                issues += issue(
                    code = "MISSING_IMAGE_CAPTION",
                    message = "The selected validation policy requires an image caption.",
                    path = "$path.caption",
                )
            } else if (caption != null) {
                issues += issue(
                    code = "BLANK_IMAGE_METADATA",
                    message = "Image caption must not be blank when supplied.",
                    path = "$path.caption",
                )
            }
        }

        validateOptionalImageMetadata(asset.sourceName, "$path.sourceName", "source name", issues)
        validateOptionalImageMetadata(asset.credit, "$path.credit", "credit", issues)
        validateImageFacts(asset, path, requirements, issues)
        issues += policy.validateImage(article, asset, path)
    }

    private fun validateImageFacts(
        asset: ImageAsset,
        path: String,
        requirements: ImageValidationRequirements,
        issues: MutableList<ValidationIssue>,
    ) {
        val mimeType = asset.mimeType
        if (requirements.requireMimeType && mimeType.isNullOrBlank()) {
            issues += issue(
                code = "MISSING_IMAGE_MIME_TYPE",
                message = "The selected validation policy requires an image MIME type from media inspection.",
                path = "$path.mimeType",
            )
        }
        if (mimeType != null && requirements.allowedMimeTypes.isNotEmpty() && mimeType !in requirements.allowedMimeTypes) {
            issues += issue(
                code = "INVALID_IMAGE_MIME_TYPE",
                message = "Image MIME type is not allowed by the selected validation policy.",
                path = "$path.mimeType",
            )
        }

        val fileSizeBytes = asset.fileSizeBytes
        if (requirements.requireFileSizeBytes && fileSizeBytes == null) {
            issues += issue(
                code = "MISSING_IMAGE_FILE_SIZE",
                message = "The selected validation policy requires file size from media inspection.",
                path = "$path.fileSizeBytes",
            )
        }
        if (fileSizeBytes != null && fileSizeBytes < 0) {
            issues += issue(
                code = "INVALID_IMAGE_FILE_SIZE",
                message = "Image file size cannot be negative.",
                path = "$path.fileSizeBytes",
            )
        }
        val maxFileSizeBytes = requirements.maxFileSizeBytes
        if (maxFileSizeBytes != null && fileSizeBytes != null && fileSizeBytes > maxFileSizeBytes) {
            issues += issue(
                code = "IMAGE_FILE_SIZE_EXCEEDS_LIMIT",
                message = "Image file size exceeds the selected validation policy limit.",
                path = "$path.fileSizeBytes",
            )
        }

        val width = asset.width
        val height = asset.height
        if (requirements.requireDimensions && (width == null || height == null)) {
            issues += issue(
                code = "MISSING_IMAGE_DIMENSIONS",
                message = "The selected validation policy requires dimensions from media inspection.",
                path = "$path.dimensions",
            )
        }
        if ((width != null && width <= 0) || (height != null && height <= 0)) {
            issues += issue(
                code = "INVALID_IMAGE_DIMENSIONS",
                message = "Image width and height must be positive when supplied.",
                path = "$path.dimensions",
            )
        }
    }

    private fun validateOptionalImageMetadata(
        value: String?,
        path: String,
        label: String,
        issues: MutableList<ValidationIssue>,
    ) {
        if (value != null && value.isBlank()) {
            issues += issue(
                code = "BLANK_IMAGE_METADATA",
                message = "Image $label must not be blank when supplied.",
                path = path,
            )
        }
    }

    private fun issue(
        code: String,
        message: String,
        path: String,
        severity: ValidationSeverity = ValidationSeverity.ERROR,
    ): ValidationIssue = ValidationIssue(
        code = code,
        severity = severity,
        message = message,
        path = path,
    )
}
