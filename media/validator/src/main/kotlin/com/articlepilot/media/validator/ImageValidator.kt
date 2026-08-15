package com.articlepilot.media.validator

import com.articlepilot.core.model.ImageAsset
import com.articlepilot.media.downloader.LocalMediaFile

interface ImageValidator {
    suspend fun validate(asset: ImageAsset, file: LocalMediaFile, policy: ImageValidationPolicy): ImageValidationResult
}

interface ImageValidationPolicy {
    val id: String
    val version: String
    val allowedMimeTypes: Set<String>
    val maxFileSizeBytes: Long?
    val minWidth: Int?
    val minHeight: Int?
}

data class ImageValidationResult(
    val isValid: Boolean,
    val issues: List<ImageValidationIssue>,
)

data class ImageValidationIssue(
    val code: String,
    val message: String,
    val severity: ImageIssueSeverity,
)

enum class ImageIssueSeverity {
    ERROR,
    WARNING,
}
