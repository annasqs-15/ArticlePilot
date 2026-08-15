package com.articlepilot.media.processor

import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageAssetId
import com.articlepilot.core.model.ImageProcessingStatus
import com.articlepilot.core.model.ImageValidationStatus
import com.articlepilot.media.downloader.DownloadDestination
import com.articlepilot.media.downloader.DownloadFailureReason
import com.articlepilot.media.downloader.DownloadResult
import com.articlepilot.media.downloader.ImageDownloader
import com.articlepilot.media.inspection.MediaFormat
import com.articlepilot.media.inspection.MediaInspectionResult
import com.articlepilot.media.inspection.MediaInspector
import com.articlepilot.media.inspection.MediaMetadata
import com.articlepilot.media.storage.FileSystemMediaStorage
import com.articlepilot.media.storage.LocalMediaFile
import com.articlepilot.media.validator.GenericImageValidationPolicy
import com.articlepilot.media.validator.ImageIssueSeverity
import com.articlepilot.media.validator.ImageValidationIssue
import com.articlepilot.media.validator.ImageValidationResult
import com.articlepilot.media.validator.ImageValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaPipelineTest {
    @Test
    fun `successful asset is promoted to ready and observer sees verified transitions`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-pipeline-ready")
        val storage = FileSystemMediaStorage(root)
        val observer = RecordingObserver()
        val result = pipeline(storage, observer, ScriptedDownloader("png-bytes"))
            .prepare(asset())

        val ready = assertIs<MediaPipelineResult.Ready>(result)
        assertEquals(ImageProcessingStatus.READY, ready.asset.processingStatus)
        assertEquals(ImageValidationStatus.VALID, ready.asset.validationStatus)
        assertFalse(ready.file.isTemporary)
        assertTrue(Files.exists(java.nio.file.Path.of(ready.file.path)))
        assertEquals(
            listOf(
                MediaPipelineState.NOT_STARTED,
                MediaPipelineState.DOWNLOADING,
                MediaPipelineState.DOWNLOADED,
                MediaPipelineState.INSPECTING,
                MediaPipelineState.VALIDATING,
                MediaPipelineState.READY,
            ),
            observer.states,
        )
    }

    @Test
    fun `validation failure cleans temporary file and retains explicit issues`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-pipeline-validation")
        val storage = FileSystemMediaStorage(root)
        val validationIssue = ImageValidationIssue(
            code = "UNSUPPORTED_MIME_TYPE",
            message = "PNG is not allowed by policy.",
            severity = ImageIssueSeverity.ERROR,
        )
        val result = pipeline(
            storage = storage,
            downloader = ScriptedDownloader("invalid-by-policy"),
            validator = ScriptedValidator(ImageValidationResult(false, listOf(validationIssue))),
        ).prepare(asset())

        val failure = assertIs<MediaPipelineResult.Failure>(result)
        assertEquals(MediaPipelineFailureReason.VALIDATION, failure.reason)
        assertEquals(MediaPipelineState.CLEANED, failure.snapshot.state)
        assertEquals(listOf("UNSUPPORTED_MIME_TYPE"), failure.validationIssues.map { it.code })
        assertEquals(0L, temporaryFileCount(root))
    }

    @Test
    fun `download failure is returned without pretending inspection or validation succeeded`() = runBlocking {
        val storage = FileSystemMediaStorage(Files.createTempDirectory("articlepilot-pipeline-download"))
        val result = pipeline(
            storage = storage,
            downloader = FailingDownloader(),
        ).prepare(asset())

        val failure = assertIs<MediaPipelineResult.Failure>(result)
        assertEquals(MediaPipelineFailureReason.DOWNLOAD, failure.reason)
        assertEquals(MediaPipelineState.FAILED, failure.snapshot.state)
        assertTrue(failure.message.contains("HTTP 503"))
    }

    @Test
    fun `pipeline cancellation propagates and cleans created temporary file`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-pipeline-cancel")
        val storage = FileSystemMediaStorage(root)
        val downloader = object : ImageDownloader {
            override suspend fun download(asset: ImageAsset, destination: DownloadDestination): DownloadResult {
                destination.create(asset)
                delay(Long.MAX_VALUE)
                error("unreachable")
            }
        }
        var cancelled = false
        try {
            withTimeout(50) {
                pipeline(storage = storage, downloader = downloader).prepare(asset())
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(0L, temporaryFileCount(root))
    }

    private fun pipeline(
        storage: FileSystemMediaStorage,
        observer: MediaPipelineObserver = NoopMediaPipelineObserver,
        downloader: ImageDownloader,
        validator: ImageValidator = ScriptedValidator(ImageValidationResult(true, emptyList())),
    ) = MediaPipeline(
        downloader = downloader,
        storage = storage,
        inspector = ScriptedInspector(),
        validator = validator,
        observer = observer,
        clock = { 1234L },
    )

    private fun asset() = ImageAsset(
        id = ImageAssetId("asset-1"),
        downloadUrl = "https://example.test/asset.png",
    )

    private fun temporaryFileCount(root: java.nio.file.Path): Long =
        root.resolve("temporary").let { directory ->
            if (!Files.exists(directory)) 0 else Files.list(directory).use { it.count() }
        }

    private class RecordingObserver : MediaPipelineObserver {
        val states = mutableListOf<MediaPipelineState>()
        override suspend fun onStateChanged(snapshot: MediaPipelineSnapshot) {
            states += snapshot.state
        }
    }

    private class ScriptedDownloader(
        private val body: String,
    ) : ImageDownloader {
        override suspend fun download(asset: ImageAsset, destination: DownloadDestination): DownloadResult {
            val file = destination.create(asset)
            Files.writeString(java.nio.file.Path.of(file.path), body)
            return DownloadResult.Success(asset, file, asset.downloadUrl, attempts = 1, responseMimeType = "image/png")
        }
    }

    private class FailingDownloader : ImageDownloader {
        override suspend fun download(asset: ImageAsset, destination: DownloadDestination): DownloadResult =
            DownloadResult.Failure(
                asset = asset,
                reason = DownloadFailureReason.HTTP_ERROR,
                message = "HTTP 503 from source",
                canRetry = true,
                attempts = 3,
            )
    }

    private class ScriptedInspector : MediaInspector {
        override suspend fun inspect(
            file: LocalMediaFile,
            policy: com.articlepilot.media.inspection.InspectionPolicy,
        ): MediaInspectionResult = MediaInspectionResult.Success(
            MediaMetadata(
                file = file,
                detectedMimeType = "image/png",
                declaredMimeType = "image/png",
                width = 2,
                height = 2,
                sizeBytes = Files.size(java.nio.file.Path.of(file.path)),
                format = MediaFormat.PNG,
                decodeVerified = true,
            ),
        )
    }

    private class ScriptedValidator(
        private val result: ImageValidationResult,
    ) : ImageValidator {
        override suspend fun validate(
            asset: ImageAsset,
            file: LocalMediaFile,
            policy: com.articlepilot.media.validator.ImageValidationPolicy,
        ): ImageValidationResult = result

        override fun validateMetadata(
            asset: ImageAsset,
            metadata: MediaMetadata,
            policy: com.articlepilot.media.validator.ImageValidationPolicy,
        ): ImageValidationResult = result.copy(metadata = metadata)
    }
}
