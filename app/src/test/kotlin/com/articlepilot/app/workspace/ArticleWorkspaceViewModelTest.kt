package com.articlepilot.app.workspace

import com.articlepilot.app.workspace.application.ArticleWorkspaceViewModel
import com.articlepilot.app.workspace.application.MediaPipelineFactory
import com.articlepilot.app.workspace.state.WorkspacePhase
import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.parser.ArticleFormatV1Parser
import com.articlepilot.core.validator.ArticleValidationEngine
import com.articlepilot.media.downloader.DownloadDestination
import com.articlepilot.media.downloader.DownloadResult
import com.articlepilot.media.downloader.ImageDownloader
import com.articlepilot.media.inspection.InspectionPolicy
import com.articlepilot.media.inspection.MediaFormat
import com.articlepilot.media.inspection.MediaInspectionResult
import com.articlepilot.media.inspection.MediaInspector
import com.articlepilot.media.inspection.MediaMetadata
import com.articlepilot.media.processor.MediaPipeline
import com.articlepilot.media.processor.MediaPipelineObserver
import com.articlepilot.media.storage.LocalMediaFile
import com.articlepilot.media.storage.MediaStorage
import com.articlepilot.media.storage.StorageFailureReason
import com.articlepilot.media.storage.StorageResult
import com.articlepilot.media.storage.StoredMedia
import com.articlepilot.media.validator.ImageValidationPolicy
import com.articlepilot.media.validator.ImageValidationResult
import com.articlepilot.media.validator.ImageValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleWorkspaceViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        rootsHolder.forEach(::deleteRecursively)
        rootsHolder.clear()
    }

    @Test
    fun `empty input becomes invalid with diagnostics and remains preserved`() = runTest {
        val viewModel = viewModel()

        viewModel.updateSource("   ")
        viewModel.parse()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(WorkspacePhase.INVALID, state.phase)
        assertEquals("   ", state.sourceText)
        assertTrue(state.parseDiagnostics.isNotEmpty())
    }

    @Test
    fun `parse failure preserves source and exposes diagnostics`() = runTest {
        val viewModel = viewModel()
        val source = "@article version: 1.0\n@title\n"

        viewModel.updateSource(source)
        viewModel.parse()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(WorkspacePhase.INVALID, state.phase)
        assertEquals(source, state.sourceText)
        assertTrue(state.parseDiagnostics.isNotEmpty())
        assertTrue(state.parseDiagnostics.all { it.code.isNotBlank() && it.message.isNotBlank() })
    }

    @Test
    fun `parse success validates structure and reaches local ready when no media exists`() = runTest {
        val viewModel = viewModel()

        viewModel.updateSource(validTextSource())
        viewModel.parse()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(WorkspacePhase.READY, state.phase)
        assertNotNull(state.article)
        assertEquals("Workspace test", state.article?.metadata?.title)
        assertTrue(state.validationIssues.isEmpty())
        assertTrue(state.isLocallyReady)
    }

    @Test
    fun `blocking validation prevents media processing`() = runTest {
        val viewModel = viewModel(validationPolicy = RequiresCaptionPolicy)

        viewModel.updateSource(validImageSource(caption = null))
        viewModel.parse()
        advanceUntilIdle()
        viewModel.processMedia()
        advanceUntilIdle()

        val parsed = viewModel.uiState.value
        assertEquals(WorkspacePhase.PARSED, parsed.phase)
        assertTrue(parsed.media.isNotEmpty())
        assertTrue(parsed.hasBlockingValidationErrors)
        assertFalse(parsed.media.single().isReady)
    }

    @Test
    fun `media success updates asset facts and reaches ready`() = runTest {
        val factory = ScriptedMediaPipelineFactory(valid = true)
        val viewModel = viewModel(factory)

        viewModel.updateSource(validImageSource(caption = "Preview caption"))
        viewModel.parse()
        advanceUntilIdle()
        viewModel.processMedia()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(WorkspacePhase.READY, state.phase)
        assertTrue(state.media.single().isReady)
        assertNotNull(state.media.single().asset.localFileReference)
        assertEquals("image/png", state.media.single().asset.mimeType)
        assertTrue(state.isLocallyReady)
    }

    @Test
    fun `media failure remains visible and retry can recover`() = runTest {
        val factory = ScriptedMediaPipelineFactory(valid = false)
        val viewModel = viewModel(factory)

        viewModel.updateSource(validImageSource(caption = "Retry caption"))
        viewModel.parse()
        advanceUntilIdle()
        viewModel.processMedia()
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertEquals(WorkspacePhase.FAILED, failed.phase)
        assertFalse(failed.media.single().isReady)
        assertTrue(failed.media.single().message.orEmpty().isNotBlank())

        factory.valid = true
        viewModel.processMedia(retryOnlyFailed = true)
        advanceUntilIdle()

        val recovered = viewModel.uiState.value
        assertEquals(WorkspacePhase.READY, recovered.phase)
        assertTrue(recovered.media.single().isReady)
    }

    private fun viewModel(
        factory: MediaPipelineFactory = ScriptedMediaPipelineFactory(valid = true),
        validationPolicy: com.articlepilot.core.validator.ValidationPolicy = com.articlepilot.core.validator.GenericArticleValidationPolicy,
    ) = ArticleWorkspaceViewModel(
        parser = ArticleFormatV1Parser(),
        validator = ArticleValidationEngine(),
        mediaPipelineFactory = factory,
        validationPolicy = validationPolicy,
        workerDispatcher = dispatcher,
    )

    private fun validTextSource() = """
        @article version: 1.0
        @title
        Workspace test
        @section
        @heading
        Introduction
        @text
        A deterministic paragraph for the Article Workspace.
        @end-text
    """.trimIndent()

    private fun validImageSource(caption: String?) = buildString {
        appendLine("@article version: 1.0")
        appendLine("@title")
        appendLine("Workspace image test")
        appendLine("@section")
        appendLine("@image")
        appendLine("download-url: https://example.test/image.png")
        if (caption != null) appendLine("caption: $caption")
        appendLine("@end-image")
    }

    private fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { path ->
                runCatching { Files.deleteIfExists(path) }
            }
        }
    }

    private object RequiresCaptionPolicy : com.articlepilot.core.validator.ValidationPolicy {
        override val id: String = "test-requires-caption"
        override val version: String = "1"
        override val imageRequirements = com.articlepilot.core.validator.ImageValidationRequirements(requireCaption = true)
    }

    private class ScriptedMediaPipelineFactory(
        var valid: Boolean,
    ) : MediaPipelineFactory {
        private val root = Files.createTempDirectory("articlepilot-workspace-media")

        init {
            rootsHolder.add(root)
        }

        override fun create(observer: MediaPipelineObserver): MediaPipeline {
            val storage = ScriptedStorage(root)
            val inspector = ScriptedInspector()
            return MediaPipeline(
                downloader = ScriptedDownloader(),
                storage = storage,
                inspector = inspector,
                validator = ScriptedValidator(valid),
                observer = observer,
                clock = { 1_000L },
            )
        }

        private class ScriptedStorage(private val root: Path) : MediaStorage {
            private val temporaryRoot = root.resolve("temporary")
            private val readyRoot = root.resolve("ready")

            override suspend fun createTemporary(asset: ImageAsset): Result<LocalMediaFile> = runCatching {
                Files.createDirectories(temporaryRoot)
                LocalMediaFile(
                    path = Files.createTempFile(temporaryRoot, "${asset.id.value}-", ".part").toString(),
                    isTemporary = true,
                )
            }

            override suspend fun promote(file: LocalMediaFile, asset: ImageAsset): Result<StoredMedia> = runCatching {
                Files.createDirectories(readyRoot)
                val target = readyRoot.resolve("${asset.id.value}.media")
                Files.move(Path.of(file.path), target)
                val promoted = file.copy(path = target.toString(), isTemporary = false, sizeBytes = Files.size(target))
                StoredMedia(file = promoted, assetId = asset.id.value)
            }

            override suspend fun delete(file: LocalMediaFile): StorageResult {
                return if (Files.deleteIfExists(Path.of(file.path))) {
                    StorageResult.Success()
                } else {
                    StorageResult.Failure(StorageFailureReason.NOT_FOUND, "Missing test file")
                }
            }

            override suspend fun exists(file: LocalMediaFile): Boolean = Files.exists(Path.of(file.path))
        }

        private class ScriptedDownloader : ImageDownloader {
            override suspend fun download(asset: ImageAsset, destination: DownloadDestination): DownloadResult {
                val file = destination.create(asset)
                Files.write(Path.of(file.path), "png-bytes".toByteArray())
                return DownloadResult.Success(
                    asset = asset,
                    file = file,
                    finalUrl = asset.downloadUrl,
                    attempts = 1,
                    responseMimeType = "image/png",
                )
            }
        }

        private class ScriptedInspector : MediaInspector {
            override suspend fun inspect(file: LocalMediaFile, policy: InspectionPolicy): MediaInspectionResult =
                MediaInspectionResult.Success(
                    MediaMetadata(
                        file = file,
                        detectedMimeType = "image/png",
                        declaredMimeType = "image/png",
                        width = 2,
                        height = 2,
                        sizeBytes = Files.size(Path.of(file.path)),
                        format = MediaFormat.PNG,
                        decodeVerified = true,
                    ),
                )
        }

        private class ScriptedValidator(private val valid: Boolean) : ImageValidator {
            override suspend fun validate(
                asset: ImageAsset,
                file: LocalMediaFile,
                policy: ImageValidationPolicy,
            ): ImageValidationResult = ImageValidationResult(
                isValid = valid,
                issues = if (valid) emptyList() else listOf(
                    com.articlepilot.media.validator.ImageValidationIssue(
                        code = "SCRIPTED_MEDIA_FAILURE",
                        message = "Deterministic media validation failure.",
                        severity = com.articlepilot.media.validator.ImageIssueSeverity.ERROR,
                    ),
                ),
            )

            override fun validateMetadata(
                asset: ImageAsset,
                metadata: MediaMetadata,
                policy: ImageValidationPolicy,
            ): ImageValidationResult = ImageValidationResult(
                isValid = valid,
                issues = if (valid) emptyList() else listOf(
                    com.articlepilot.media.validator.ImageValidationIssue(
                        code = "SCRIPTED_MEDIA_FAILURE",
                        message = "Deterministic media validation failure.",
                        severity = com.articlepilot.media.validator.ImageIssueSeverity.ERROR,
                    ),
                ),
                metadata = metadata,
            )
        }
    }

    private companion object {
        val rootsHolder = mutableListOf<Path>()
    }
}
