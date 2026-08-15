package com.articlepilot.media.processor

import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageProcessingStatus
import com.articlepilot.core.model.ImageValidationStatus
import com.articlepilot.media.downloader.DownloadDestination
import com.articlepilot.media.downloader.DownloadResult
import com.articlepilot.media.downloader.HttpDownloadPolicy
import com.articlepilot.media.downloader.ImageDownloader
import com.articlepilot.media.inspection.InspectionPolicy
import com.articlepilot.media.inspection.MediaInspectionResult
import com.articlepilot.media.inspection.MediaInspector
import com.articlepilot.media.inspection.MediaMetadata
import com.articlepilot.media.storage.LocalMediaFile
import com.articlepilot.media.storage.MediaStorage
import com.articlepilot.media.storage.StorageResult
import com.articlepilot.media.validator.DefaultImageValidator
import com.articlepilot.media.validator.GenericImageValidationPolicy
import com.articlepilot.media.validator.ImageIssueSeverity
import com.articlepilot.media.validator.ImageValidationIssue
import com.articlepilot.media.validator.ImageValidationPolicy
import com.articlepilot.media.validator.ImageValidationResult
import com.articlepilot.media.validator.ImageValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface MediaPipelineObserver {
    suspend fun onStateChanged(snapshot: MediaPipelineSnapshot)
}

object NoopMediaPipelineObserver : MediaPipelineObserver {
    override suspend fun onStateChanged(snapshot: MediaPipelineSnapshot) = Unit
}

enum class MediaPipelineState {
    NOT_STARTED,
    DOWNLOADING,
    DOWNLOADED,
    INSPECTING,
    VALIDATING,
    READY,
    FAILED,
    CANCELLED,
    CLEANED,
}

data class MediaPipelineSnapshot(
    val assetId: String,
    val state: MediaPipelineState,
    val attempt: Int = 0,
    val message: String? = null,
    val updatedAtEpochMillis: Long,
)

data class MediaPipelinePolicy(
    val download: HttpDownloadPolicy = HttpDownloadPolicy(),
    val inspection: InspectionPolicy = InspectionPolicy(),
    val validation: ImageValidationPolicy = GenericImageValidationPolicy(),
    val retainFailedTemporaryFiles: Boolean = false,
)

sealed interface MediaPipelineResult {
    data class Ready(
        val asset: ImageAsset,
        val metadata: MediaMetadata,
        val file: LocalMediaFile,
        val snapshot: MediaPipelineSnapshot,
    ) : MediaPipelineResult

    data class Failure(
        val asset: ImageAsset,
        val reason: MediaPipelineFailureReason,
        val message: String,
        val snapshot: MediaPipelineSnapshot,
        val validationIssues: List<ImageValidationIssue> = emptyList(),
        val cleanupFailure: String? = null,
    ) : MediaPipelineResult
}

enum class MediaPipelineFailureReason {
    DOWNLOAD,
    INSPECTION,
    VALIDATION,
    PROMOTION,
    CLEANUP,
}

/**
 * Orchestrates one image asset. It never performs article-wide ordering or browser upload;
 * callers own the block association and persist snapshots if they need resume semantics.
 */
class MediaPipeline(
    private val downloader: ImageDownloader,
    private val storage: MediaStorage,
    private val inspector: MediaInspector,
    private val validator: ImageValidator,
    private val observer: MediaPipelineObserver = NoopMediaPipelineObserver,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun prepare(
        asset: ImageAsset,
        policy: MediaPipelinePolicy = MediaPipelinePolicy(),
    ): MediaPipelineResult {
        var snapshot = snapshot(asset, MediaPipelineState.NOT_STARTED)
        emit(snapshot)
        var temporaryFile: LocalMediaFile? = null

        try {
            snapshot = transition(asset, MediaPipelineState.DOWNLOADING)
            val destination = DownloadDestination {
                storage.createTemporary(it).getOrElse { error ->
                    throw IllegalStateException("Unable to create temporary media file: ${error.message}")
                }.also { created -> temporaryFile = created }
            }
            val downloaded = downloader.download(asset, destination)
            if (downloaded is DownloadResult.Failure) {
                snapshot = transition(asset, MediaPipelineState.FAILED, downloaded.message, downloaded.attempts)
                return MediaPipelineResult.Failure(
                    asset = asset.copy(processingStatus = ImageProcessingStatus.FAILED),
                    reason = MediaPipelineFailureReason.DOWNLOAD,
                    message = downloaded.message,
                    snapshot = snapshot,
                )
            }
            downloaded as DownloadResult.Success
            temporaryFile = downloaded.file
            snapshot = transition(asset, MediaPipelineState.DOWNLOADED, attempt = downloaded.attempts)

            snapshot = transition(asset, MediaPipelineState.INSPECTING, attempt = downloaded.attempts)
            val inspected = inspector.inspect(downloaded.file, policy.inspection)
            if (inspected is MediaInspectionResult.Failure) {
                val cleanup = cleanup(downloaded.file, policy)
                snapshot = transitionAfterCleanup(asset, cleanup, inspected.message, downloaded.attempts)
                return MediaPipelineResult.Failure(
                    asset = asset.copy(processingStatus = ImageProcessingStatus.FAILED),
                    reason = MediaPipelineFailureReason.INSPECTION,
                    message = inspected.message,
                    snapshot = snapshot,
                    cleanupFailure = cleanup.failureMessage,
                )
            }
            inspected as MediaInspectionResult.Success

            snapshot = transition(asset, MediaPipelineState.VALIDATING, attempt = downloaded.attempts)
            val validation = validator.validateMetadata(asset, inspected.metadata, policy.validation)
            if (!validation.isValid) {
                val message = validation.issues.joinToString("; ") { it.message }
                val cleanup = cleanup(downloaded.file, policy)
                snapshot = transitionAfterCleanup(asset, cleanup, message, downloaded.attempts)
                return MediaPipelineResult.Failure(
                    asset = asset.copy(processingStatus = ImageProcessingStatus.FAILED, validationStatus = ImageValidationStatus.INVALID),
                    reason = MediaPipelineFailureReason.VALIDATION,
                    message = message,
                    snapshot = snapshot,
                    validationIssues = validation.issues,
                    cleanupFailure = cleanup.failureMessage,
                )
            }

            val promoted = storage.promote(downloaded.file, asset).getOrElse { error ->
                val cleanup = cleanup(downloaded.file, policy)
                val cleanupMessage = cleanup.failureMessage?.let { " Cleanup also failed: $it" }
                snapshot = transition(asset, MediaPipelineState.FAILED, "${error.message ?: "Promotion failed."}$cleanupMessage", downloaded.attempts)
                return MediaPipelineResult.Failure(
                    asset = asset.copy(processingStatus = ImageProcessingStatus.FAILED),
                    reason = MediaPipelineFailureReason.PROMOTION,
                    message = error.message ?: "Promotion failed.",
                    snapshot = snapshot,
                    cleanupFailure = cleanup.failureMessage,
                )
            }
            temporaryFile = null
            snapshot = transition(asset, MediaPipelineState.READY, attempt = downloaded.attempts)
            val readyAsset = asset.copy(
                localFileReference = promoted.file.path,
                mimeType = inspected.metadata.detectedMimeType,
                width = inspected.metadata.width,
                height = inspected.metadata.height,
                fileSizeBytes = inspected.metadata.sizeBytes,
                processingStatus = ImageProcessingStatus.READY,
                validationStatus = ImageValidationStatus.VALID,
            )
            return MediaPipelineResult.Ready(
                asset = readyAsset,
                metadata = inspected.metadata.copy(file = promoted.file),
                file = promoted.file,
                snapshot = snapshot,
            )
        } catch (error: CancellationException) {
            val current = transition(asset, MediaPipelineState.CANCELLED, error.message)
            val file = temporaryFile
            if (file != null && !policy.retainFailedTemporaryFiles) {
                val cleanup = withContext(NonCancellable) { storage.delete(file) }
                if (cleanup is StorageResult.Success) {
                    transition(asset, MediaPipelineState.CLEANED, "Cancelled operation cleaned temporary file.")
                } else {
                    transition(asset, MediaPipelineState.FAILED, "Cancellation cleanup failed.")
                }
            } else {
                emit(current)
            }
            throw error
        } catch (error: Exception) {
            val file = temporaryFile
            val cleanup = if (file != null && !policy.retainFailedTemporaryFiles) cleanup(file, policy) else CleanupOutcome()
            snapshot = transitionAfterCleanup(asset, cleanup, error.message ?: error::class.simpleName, snapshot.attempt)
            return MediaPipelineResult.Failure(
                asset = asset.copy(processingStatus = ImageProcessingStatus.FAILED),
                reason = MediaPipelineFailureReason.DOWNLOAD,
                message = error.message ?: "Media pipeline failed.",
                snapshot = snapshot,
                cleanupFailure = cleanup.failureMessage,
            )
        }
    }

    private suspend fun cleanup(file: LocalMediaFile, policy: MediaPipelinePolicy): CleanupOutcome {
        if (policy.retainFailedTemporaryFiles) return CleanupOutcome()
        return when (val deleted = storage.delete(file)) {
            is StorageResult.Success -> CleanupOutcome(cleaned = true)
            is StorageResult.Failure -> CleanupOutcome(failureMessage = deleted.message)
        }
    }

    private suspend fun transitionAfterCleanup(
        asset: ImageAsset,
        cleanup: CleanupOutcome,
        message: String?,
        attempt: Int,
    ): MediaPipelineSnapshot {
        return if (cleanup.failureMessage == null) {
            transition(asset, MediaPipelineState.CLEANED, message, attempt)
        } else {
            transition(asset, MediaPipelineState.FAILED, "$message Cleanup failed: ${cleanup.failureMessage}", attempt)
        }
    }

    private suspend fun transition(
        asset: ImageAsset,
        state: MediaPipelineState,
        message: String? = null,
        attempt: Int = 0,
    ): MediaPipelineSnapshot = snapshot(asset, state, message, attempt).also { emit(it) }

    private fun snapshot(
        asset: ImageAsset,
        state: MediaPipelineState,
        message: String? = null,
        attempt: Int = 0,
    ) = MediaPipelineSnapshot(asset.id.value, state, attempt, message, clock())

    private suspend fun emit(snapshot: MediaPipelineSnapshot) {
        runCatching { observer.onStateChanged(snapshot) }
    }

    private data class CleanupOutcome(
        val cleaned: Boolean = false,
        val failureMessage: String? = null,
    )
}
