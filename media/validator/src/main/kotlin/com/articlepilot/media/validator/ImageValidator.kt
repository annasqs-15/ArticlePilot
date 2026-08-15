package com.articlepilot.media.validator

import com.articlepilot.core.model.ImageAsset
import com.articlepilot.media.inspection.MediaInspectionFailureReason
import com.articlepilot.media.inspection.MediaInspectionResult
import com.articlepilot.media.inspection.MediaInspector
import com.articlepilot.media.inspection.MediaMetadata
import com.articlepilot.media.inspection.MediaInspectionSeverity
import com.articlepilot.media.storage.LocalMediaFile

interface ImageValidator {
    suspend fun validate(asset: ImageAsset, file: LocalMediaFile, policy: ImageValidationPolicy): ImageValidationResult
    fun validateMetadata(asset: ImageAsset, metadata: MediaMetadata, policy: ImageValidationPolicy): ImageValidationResult
}

interface ImageValidationPolicy {
    val id: String
    val version: String
    val allowedMimeTypes: Set<String>
    val maxFileSizeBytes: Long?
    val minWidth: Int?
    val maxWidth: Int?
    val minHeight: Int?
    val maxHeight: Int?
    val requireDecodeVerification: Boolean
    val rejectMimeMismatch: Boolean
}

data class GenericImageValidationPolicy(
    override val id: String = "generic-image",
    override val version: String = "1",
    override val allowedMimeTypes: Set<String> = setOf("image/jpeg", "image/png", "image/gif", "image/webp"),
    override val maxFileSizeBytes: Long? = 10L * 1024L * 1024L,
    override val minWidth: Int? = 1,
    override val maxWidth: Int? = null,
    override val minHeight: Int? = 1,
    override val maxHeight: Int? = null,
    override val requireDecodeVerification: Boolean = true,
    override val rejectMimeMismatch: Boolean = true,
) : ImageValidationPolicy

data class ImageValidationResult(
    val isValid: Boolean,
    val issues: List<ImageValidationIssue>,
    val metadata: MediaMetadata? = null,
) {
    val file: LocalMediaFile?
        get() = metadata?.file
}

data class ImageValidationIssue(
    val code: String,
    val message: String,
    val severity: ImageIssueSeverity,
    val path: String? = null,
)

enum class ImageIssueSeverity {
    ERROR,
    WARNING,
}

class DefaultImageValidator(
    private val inspector: MediaInspector,
) : ImageValidator {
    override suspend fun validate(
        asset: ImageAsset,
        file: LocalMediaFile,
        policy: ImageValidationPolicy,
    ): ImageValidationResult {
        return when (val inspected = inspector.inspect(file)) {
            is MediaInspectionResult.Failure -> ImageValidationResult(
                isValid = false,
                issues = listOf(
                    ImageValidationIssue(
                        code = inspected.reason.issueCode,
                        message = inspected.message,
                        severity = ImageIssueSeverity.ERROR,
                        path = "image[${asset.id.value}]",
                    ),
                ),
            )

            is MediaInspectionResult.Success -> validateMetadata(asset, inspected.metadata, policy)
        }
    }

    override fun validateMetadata(
        asset: ImageAsset,
        metadata: MediaMetadata,
        policy: ImageValidationPolicy,
    ): ImageValidationResult {
        val issues = mutableListOf<ImageValidationIssue>()
        val path = "image[${asset.id.value}]"
        val detectedMime = metadata.detectedMimeType.lowercase()

        metadata.issues.forEach { issue ->
            val severity = when {
                issue.code == "MIME_MISMATCH" -> if (policy.rejectMimeMismatch) ImageIssueSeverity.ERROR else ImageIssueSeverity.WARNING
                issue.severity == MediaInspectionSeverity.WARNING -> ImageIssueSeverity.WARNING
                else -> ImageIssueSeverity.ERROR
            }
            issues += ImageValidationIssue(issue.code, issue.message, severity, "$path.mimeType")
        }

        if (policy.allowedMimeTypes.isNotEmpty() && detectedMime !in policy.allowedMimeTypes.map(String::lowercase).toSet()) {
            issues += ImageValidationIssue(
                code = "UNSUPPORTED_MIME_TYPE",
                message = "Detected MIME type $detectedMime is not allowed by policy ${policy.id}.",
                severity = ImageIssueSeverity.ERROR,
                path = "$path.mimeType",
            )
        }

        val size = metadata.sizeBytes
        if (size <= 0) {
            issues += ImageValidationIssue(
                code = "INVALID_FILE_SIZE",
                message = "Image file size must be positive.",
                severity = ImageIssueSeverity.ERROR,
                path = "$path.fileSizeBytes",
            )
        }
        val maxFileSizeBytes = policy.maxFileSizeBytes
        if (maxFileSizeBytes != null && size > maxFileSizeBytes) {
            issues += ImageValidationIssue(
                code = "FILE_SIZE_EXCEEDS_LIMIT",
                message = "Image file size $size exceeds policy maximum $maxFileSizeBytes.",
                severity = ImageIssueSeverity.ERROR,
                path = "$path.fileSizeBytes",
            )
        }

        validateDimension("width", metadata.width, policy.minWidth, policy.maxWidth, path, issues)
        validateDimension("height", metadata.height, policy.minHeight, policy.maxHeight, path, issues)

        if (policy.requireDecodeVerification && !metadata.decodeVerified) {
            issues += ImageValidationIssue(
                code = "DECODE_NOT_VERIFIED",
                message = "Image metadata was not verified by a full decoder.",
                severity = ImageIssueSeverity.ERROR,
                path = path,
            )
        }

        return ImageValidationResult(
            isValid = issues.none { it.severity == ImageIssueSeverity.ERROR },
            issues = issues.toList(),
            metadata = metadata,
        )
    }

    private fun validateDimension(
        name: String,
        value: Int,
        minimum: Int?,
        maximum: Int?,
        path: String,
        issues: MutableList<ImageValidationIssue>,
    ) {
        if (value <= 0) {
            issues += ImageValidationIssue(
                code = "INVALID_DIMENSION",
                message = "Image $name must be positive.",
                severity = ImageIssueSeverity.ERROR,
                path = "$path.$name",
            )
        }
        if (minimum != null && value < minimum) {
            issues += ImageValidationIssue(
                code = "DIMENSION_BELOW_MINIMUM",
                message = "Image $name $value is below minimum $minimum.",
                severity = ImageIssueSeverity.ERROR,
                path = "$path.$name",
            )
        }
        if (maximum != null && value > maximum) {
            issues += ImageValidationIssue(
                code = "DIMENSION_ABOVE_MAXIMUM",
                message = "Image $name $value exceeds maximum $maximum.",
                severity = ImageIssueSeverity.ERROR,
                path = "$path.$name",
            )
        }
    }

    private val MediaInspectionFailureReason.issueCode: String
        get() = when (this) {
            MediaInspectionFailureReason.FILE_NOT_FOUND -> "FILE_NOT_FOUND"
            MediaInspectionFailureReason.EMPTY_FILE -> "EMPTY_FILE"
            MediaInspectionFailureReason.READ_FAILED -> "READ_FAILED"
            MediaInspectionFailureReason.INVALID_IMAGE -> "INVALID_IMAGE"
            MediaInspectionFailureReason.UNSUPPORTED_FORMAT -> "UNSUPPORTED_FORMAT"
            MediaInspectionFailureReason.DECODE_FAILED -> "DECODE_FAILED"
            MediaInspectionFailureReason.DIMENSION_INVALID -> "INVALID_DIMENSION"
        }
}
