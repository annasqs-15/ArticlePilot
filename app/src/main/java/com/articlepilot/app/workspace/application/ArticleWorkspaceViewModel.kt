package com.articlepilot.app.workspace.application

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.articlepilot.core.model.Article
import com.articlepilot.core.model.ArticleBlock
import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageBlock
import com.articlepilot.core.parser.ArticleParser
import com.articlepilot.core.parser.ParseResult
import com.articlepilot.core.validator.ArticleValidator
import com.articlepilot.core.validator.GenericArticleValidationPolicy
import com.articlepilot.core.validator.ValidationPolicy
import com.articlepilot.core.validator.ValidationResult
import com.articlepilot.core.validator.ValidationSeverity
import com.articlepilot.media.processor.MediaPipelineObserver
import com.articlepilot.media.processor.MediaPipelineResult
import com.articlepilot.media.processor.MediaPipelineSnapshot
import com.articlepilot.media.processor.MediaPipelineState
import com.articlepilot.app.workspace.state.ArticleWorkspaceUiState
import com.articlepilot.app.workspace.state.MediaRole
import com.articlepilot.app.workspace.state.WorkspaceMediaItem
import com.articlepilot.app.workspace.state.WorkspacePhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Application orchestration boundary consumed by Compose UI. */
class ArticleWorkspaceViewModel(
    private val parser: ArticleParser,
    private val validator: ArticleValidator,
    private val mediaPipelineFactory: MediaPipelineFactory,
    private val validationPolicy: ValidationPolicy = GenericArticleValidationPolicy,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArticleWorkspaceUiState())
    val uiState: StateFlow<ArticleWorkspaceUiState> = _uiState.asStateFlow()

    private var operationJob: Job? = null

    fun updateSource(source: String) {
        cancelOperationSilently()
        _uiState.value = ArticleWorkspaceUiState(
            sourceText = source,
                            phase = WorkspacePhase.EMPTY,

        )
    }

    fun clear() {
        cancelOperationSilently()
        _uiState.value = ArticleWorkspaceUiState()
    }

    fun parse() {
        val source = _uiState.value.sourceText
        cancelOperationSilently()
        operationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = WorkspacePhase.IMPORTING,
                    parseDiagnostics = emptyList(),
                    activeOperationMessage = "Parsing ArticlePilot document…",
                    isOperationCancellable = true,
                )
            }
            val result = withContext(workerDispatcher) { parser.parse(source) }
            when (result) {
                is ParseResult.Failure -> _uiState.update {
                    it.copy(
                        phase = WorkspacePhase.INVALID,
                        article = null,
                        parseDiagnostics = result.diagnostics,
                        validationResult = ValidationResult(emptyList()),
                        media = emptyList(),
                        activeOperationMessage = "Parser menemukan ${result.diagnostics.size} diagnostic.",
                        isOperationCancellable = false,
                    )
                }

                is ParseResult.Success -> {
                    val validation = withContext(workerDispatcher) {
                        validator.validate(result.article, validationPolicy)
                    }
                    val media = collectMediaItems(result.article)
                    val nextPhase = if (!validation.hasBlockingErrors() && media.isEmpty()) {
                        WorkspacePhase.READY
                    } else {
                        WorkspacePhase.PARSED
                    }
                    _uiState.update {
                        it.copy(
                            phase = nextPhase,
                            article = result.article,
                            parseDiagnostics = emptyList(),
                            validationResult = validation,
                            media = media,
                            activeOperationMessage = null,
                            isOperationCancellable = false,
                        )
                    }
                }
            }
            operationJob = null
        }
    }

    fun processMedia(retryOnlyFailed: Boolean = false) {
        val current = _uiState.value
        val article = current.article ?: return
        if (current.hasBlockingValidationErrors) return

        val targets = current.media.filter { item ->
            !item.isReady && (!retryOnlyFailed || item.pipelineState in FAILED_RETRYABLE_STATES)
        }
        if (targets.isEmpty()) {
            updateFinalPhase()
            return
        }

        cancelOperationSilently()
        operationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = WorkspacePhase.PROCESSING_MEDIA,
                    activeOperationMessage = "Memproses ${targets.size} media…",
                    isOperationCancellable = true,
                )
            }
            try {
                targets.forEachIndexed { index, item ->
                    ensureActive()
                    _uiState.update {
                        it.copy(activeOperationMessage = "Memproses media ${index + 1} dari ${targets.size}…")
                    }
                    val observer = object : MediaPipelineObserver {
                        override suspend fun onStateChanged(snapshot: MediaPipelineSnapshot) {
                            updateMediaSnapshot(snapshot)
                        }
                    }
                    val result = withContext(workerDispatcher) {
                        mediaPipelineFactory.create(observer).prepare(item.asset)
                    }
                    when (result) {
                        is MediaPipelineResult.Ready -> applyReadyResult(item, result)
                        is MediaPipelineResult.Failure -> applyFailureResult(item, result)
                    }
                }
                updateFinalPhase()
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        phase = WorkspacePhase.CANCELLED,
                        activeOperationMessage = "Pemrosesan media dibatalkan.",
                        isOperationCancellable = false,
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        phase = WorkspacePhase.FAILED,
                        activeOperationMessage = error.message ?: "Pemrosesan media gagal.",
                        isOperationCancellable = false,
                    )
                }
            } finally {
                operationJob = null
            }
        }
    }

    fun cancelMediaProcessing() {
        operationJob?.cancel()
    }

    private fun updateMediaSnapshot(snapshot: MediaPipelineSnapshot) {
        _uiState.update { state ->
            state.copy(
                media = state.media.map { item ->
                    if (item.asset.id.value != snapshot.assetId) item
                    else item.copy(
                        pipelineState = snapshot.state,
                        message = snapshot.message,
                    )
                },
            )
        }
    }

    private fun applyReadyResult(
        item: WorkspaceMediaItem,
        result: MediaPipelineResult.Ready,
    ) {
        _uiState.update { state ->
            val updatedArticle = state.article?.replaceAsset(result.asset)
            state.copy(
                article = updatedArticle,
                media = state.media.map { current ->
                    if (current.asset.id == item.asset.id) {
                        current.copy(
                            asset = result.asset,
                            pipelineState = MediaPipelineState.READY,
                            message = "Media siap digunakan secara lokal.",
                            validationIssues = emptyList(),
                        )
                    } else current
                },
            )
        }
    }

    private fun applyFailureResult(
        item: WorkspaceMediaItem,
        result: MediaPipelineResult.Failure,
    ) {
        _uiState.update { state ->
            state.copy(
                phase = WorkspacePhase.FAILED,
                media = state.media.map { current ->
                    if (current.asset.id == item.asset.id) {
                        current.copy(
                            asset = result.asset,
                            pipelineState = result.snapshot.state,
                            message = result.message,
                            validationIssues = result.validationIssues,
                        )
                    } else current
                },
                activeOperationMessage = "Media gagal diproses: ${result.message}",
                isOperationCancellable = false,
            )
        }
    }

    private fun updateFinalPhase() {
        _uiState.update { state ->
            val ready = state.article != null &&
                !state.hasBlockingValidationErrors &&
                state.allMediaReady
            val hasFailure = state.media.any { it.pipelineState in FAILED_RETRYABLE_STATES }
            state.copy(
                phase = when {
                    ready -> WorkspacePhase.READY
                    hasFailure -> WorkspacePhase.FAILED
                    else -> WorkspacePhase.PARSED
                },
                activeOperationMessage = if (ready) {
                    "Artikel siap secara lokal untuk future publishing pipeline."
                } else null,
                isOperationCancellable = false,
            )
        }
    }

    private fun cancelOperationSilently() {
        operationJob?.cancel()
        operationJob = null
    }

    override fun onCleared() {
        operationJob?.cancel()
        super.onCleared()
    }

    private companion object {
        val FAILED_RETRYABLE_STATES = setOf(
            MediaPipelineState.FAILED,
            MediaPipelineState.CANCELLED,
            MediaPipelineState.CLEANED,
        )
    }
}

private fun ValidationResult.hasBlockingErrors(): Boolean = issues.any {
    it.severity == ValidationSeverity.ERROR
}

private fun collectMediaItems(article: Article): List<WorkspaceMediaItem> = buildList {
    article.cover?.let { asset ->
        add(
            WorkspaceMediaItem(
                asset = asset,
                location = "cover",
                role = MediaRole.COVER,
            ),
        )
    }
    article.sections.forEachIndexed { sectionIndex, section ->
        section.blocks.forEachIndexed { blockIndex, block ->
            if (block is ImageBlock) {
                add(
                    WorkspaceMediaItem(
                        asset = block.asset,
                        location = "sections[$sectionIndex].blocks[$blockIndex]",
                        role = MediaRole.CONTENT,
                    ),
                )
            }
        }
    }
}

private fun Article.replaceAsset(updated: ImageAsset): Article = copy(
    cover = cover?.let { if (it.id == updated.id) updated else it },
    sections = sections.map { section ->
        section.copy(
            blocks = section.blocks.map { block ->
                if (block is ImageBlock && block.asset.id == updated.id) {
                    block.copy(asset = updated)
                } else block
            },
        )
    },
)
