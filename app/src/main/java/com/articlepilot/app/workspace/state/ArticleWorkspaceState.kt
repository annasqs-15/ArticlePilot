package com.articlepilot.app.workspace.state

import com.articlepilot.core.model.Article
import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageProcessingStatus
import com.articlepilot.core.parser.ParseDiagnostic
import com.articlepilot.core.validator.ValidationIssue
import com.articlepilot.core.validator.ValidationResult
import com.articlepilot.core.validator.ValidationSeverity
import com.articlepilot.media.processor.MediaPipelineState
import com.articlepilot.media.validator.ImageValidationIssue

/** The user-facing lifecycle of one Article Workspace session. */
enum class WorkspacePhase {
    EMPTY,
    IMPORTING,
    INVALID,
    PARSED,
    PROCESSING_MEDIA,
    FAILED,
    CANCELLED,
    READY,
}

enum class MediaRole {
    COVER,
    CONTENT,
}

data class WorkspaceMediaItem(
    val asset: ImageAsset,
    val location: String,
    val role: MediaRole,
    val pipelineState: MediaPipelineState = initialPipelineState(asset),
    val message: String? = null,
    val validationIssues: List<ImageValidationIssue> = emptyList(),
) {
    val isReady: Boolean
        get() = asset.processingStatus == ImageProcessingStatus.READY &&
            pipelineState == MediaPipelineState.READY &&
            !asset.localFileReference.isNullOrBlank()

    companion object {
        private fun initialPipelineState(asset: ImageAsset): MediaPipelineState = when {
            asset.processingStatus == ImageProcessingStatus.READY -> MediaPipelineState.READY
            asset.processingStatus == ImageProcessingStatus.FAILED -> MediaPipelineState.FAILED
            else -> MediaPipelineState.NOT_STARTED
        }
    }
}

data class ArticleWorkspaceUiState(
    val sourceText: String = "",
    val phase: WorkspacePhase = WorkspacePhase.EMPTY,
    val article: Article? = null,
    val parseDiagnostics: List<ParseDiagnostic> = emptyList(),
    val validationResult: ValidationResult = ValidationResult(emptyList()),
    val media: List<WorkspaceMediaItem> = emptyList(),
    val activeOperationMessage: String? = null,
    val isOperationCancellable: Boolean = false,
) {
    val validationIssues: List<ValidationIssue>
        get() = validationResult.issues

    val hasBlockingValidationErrors: Boolean
        get() = validationResult.issues.any { it.severity == ValidationSeverity.ERROR }

    val allMediaReady: Boolean
        get() = media.all(WorkspaceMediaItem::isReady)

    val isLocallyReady: Boolean
        get() = phase == WorkspacePhase.READY &&
            article != null &&
            parseDiagnostics.isEmpty() &&
            !hasBlockingValidationErrors &&
            allMediaReady
}
