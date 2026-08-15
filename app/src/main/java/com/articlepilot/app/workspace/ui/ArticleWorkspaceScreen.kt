package com.articlepilot.app.workspace.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.articlepilot.app.workspace.application.ArticleWorkspaceViewModel
import com.articlepilot.app.workspace.state.ArticleWorkspaceUiState
import com.articlepilot.app.workspace.state.WorkspaceMediaItem
import com.articlepilot.app.workspace.state.WorkspacePhase
import com.articlepilot.core.model.Article
import com.articlepilot.core.model.ArticleBlock
import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageBlock
import com.articlepilot.core.model.TextBlock
import com.articlepilot.core.parser.ParseDiagnostic
import com.articlepilot.core.validator.ValidationIssue
import com.articlepilot.core.validator.ValidationSeverity
import com.articlepilot.media.processor.MediaPipelineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleWorkspaceScreen(
    viewModel: ArticleWorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Article Workspace")
                        Text(
                            text = "Article → Images → Preview",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WorkspaceStatusCard(state)
            ImportCard(
                state = state,
                clipboard = clipboard,
                onSourceChanged = viewModel::updateSource,
                onParse = viewModel::parse,
                onClear = viewModel::clear,
            )
            if (state.parseDiagnostics.isNotEmpty()) {
                ParseDiagnosticsCard(state.parseDiagnostics)
            }
            state.article?.let { article ->
                ValidationCard(state)
                ArticleReviewCard(article = article, media = state.media)
                MediaProcessingCard(
                    state = state,
                    onProcess = { viewModel.processMedia() },
                    onRetry = { viewModel.processMedia(retryOnlyFailed = true) },
                    onCancel = viewModel::cancelMediaProcessing,
                )
            }
            if (state.isLocallyReady) {
                ReadyCard()
            }
        }
    }
}

@Composable
private fun WorkspaceStatusCard(state: ArticleWorkspaceUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workspace-status"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workspace state", style = MaterialTheme.typography.titleMedium)
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(state.phase.label()) },
                modifier = Modifier.semantics { contentDescription = "Workspace state ${state.phase.name}" },
            )
            state.activeOperationMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ImportCard(
    state: ArticleWorkspaceUiState,
    clipboard: ClipboardManager,
    onSourceChanged: (String) -> Unit,
    onParse: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("article-import"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Import ArticlePilot document", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tempel seluruh dokumen ArticlePilot v1.0. Source asli dipertahankan selama workspace ini aktif.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.sourceText,
                onValueChange = onSourceChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("article-source-input"),
                label = { Text("ArticlePilot source") },
                minLines = 9,
                maxLines = 18,
                supportingText = { Text("Source tidak dikirim ke server ArticlePilot.") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboard.getText()?.text?.let(onSourceChanged)
                    },
                    modifier = Modifier.semantics { contentDescription = "Paste dari clipboard" },
                ) {
                    Text("Paste")
                }
                Button(
                    onClick = onParse,
                    enabled = state.sourceText.isNotBlank() && state.phase != WorkspacePhase.IMPORTING,
                    modifier = Modifier.testTag("parse-button"),
                ) {
                    Text("Parse")
                }
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun ParseDiagnosticsCard(diagnostics: List<ParseDiagnostic>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("parse-diagnostics"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Parser diagnostics", style = MaterialTheme.typography.titleMedium)
            diagnostics.forEach { diagnostic ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = diagnostic.code,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(diagnostic.message, style = MaterialTheme.typography.bodyMedium)
                    val location = buildString {
                        diagnostic.line?.let { append("line $it") }
                        diagnostic.column?.let { if (isNotEmpty()) append(", "); append("column $it") }
                        diagnostic.path?.let { if (isNotEmpty()) append(" • "); append(it) }
                    }
                    if (location.isNotEmpty()) {
                        Text(location, style = MaterialTheme.typography.labelSmall)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ValidationCard(state: ArticleWorkspaceUiState) {
    val issues = state.validationIssues
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("validation-card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Article validation", style = MaterialTheme.typography.titleMedium)
            if (issues.isEmpty()) {
                Text("No generic validation issues.", style = MaterialTheme.typography.bodyMedium)
            } else {
                issues.forEach { issue -> ValidationIssueRow(issue) }
            }
        }
    }
}

@Composable
private fun ValidationIssueRow(issue: ValidationIssue) {
    val label = when (issue.severity) {
        ValidationSeverity.ERROR -> "ERROR"
        ValidationSeverity.WARNING -> "WARNING"
        ValidationSeverity.INFO -> "INFO"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label ${issue.code}" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$label • ${issue.code}", fontWeight = FontWeight.Bold)
        Text(issue.message, style = MaterialTheme.typography.bodyMedium)
        issue.path?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun ArticleReviewCard(
    article: Article,
    media: List<WorkspaceMediaItem>,
) {
    val mediaById = media.associateBy { it.asset.id }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("article-review"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Article review", style = MaterialTheme.typography.titleMedium)
            Text(article.metadata.title, style = MaterialTheme.typography.headlineSmall)
            article.metadata.excerpt?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            article.metadata.category?.let { Text("Category: $it") }
            if (article.metadata.tags.isNotEmpty()) {
                Text("Tags: ${article.metadata.tags.joinToString(", ")}")
            }
            article.cover?.let { asset ->
                Text("Cover", style = MaterialTheme.typography.titleSmall)
                ImageReview(asset, mediaById[asset.id])
            }
            article.sections.forEachIndexed { sectionIndex, section ->
                HorizontalDivider()
                Text(
                    text = section.heading ?: "Section ${sectionIndex + 1}",
                    style = MaterialTheme.typography.titleMedium,
                )
                section.blocks.forEachIndexed { blockIndex, block ->
                    when (block) {
                        is TextBlock -> Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("text-block-$sectionIndex-$blockIndex"),
                        )

                        is ImageBlock -> ImageReview(
                            asset = block.asset,
                            mediaItem = mediaById[block.asset.id],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaProcessingCard(
    state: ArticleWorkspaceUiState,
    onProcess: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val hasPending = state.media.any { !it.isReady }
    val hasRetryableFailure = state.media.any {
        it.pipelineState in setOf(
            MediaPipelineState.FAILED,
            MediaPipelineState.CANCELLED,
            MediaPipelineState.CLEANED,
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("media-processing"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Image review and processing", style = MaterialTheme.typography.titleMedium)
            state.media.forEach { item -> MediaItemRow(item) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onProcess,
                    enabled = hasPending && !state.hasBlockingValidationErrors && !state.isOperationCancellable,
                    modifier = Modifier.testTag("process-media-button"),
                ) {
                    Text("Process media")
                }
                if (hasRetryableFailure) {
                    OutlinedButton(
                        onClick = onRetry,
                        enabled = !state.isOperationCancellable,
                        modifier = Modifier.testTag("retry-media-button"),
                    ) {
                        Text("Retry failed")
                    }
                }
                if (state.isOperationCancellable) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("cancel-media-button")) {
                        Text("Cancel")
                    }
                }
            }
            if (state.hasBlockingValidationErrors) {
                Text(
                    "Perbaiki validation error sebelum memproses media.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MediaItemRow(item: WorkspaceMediaItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Media ${item.asset.id.value} ${item.pipelineState.name}" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${item.location} • ${item.pipelineState.name}", fontWeight = FontWeight.Bold)
        Text("Download: ${item.asset.downloadUrl}", style = MaterialTheme.typography.bodySmall)
        item.asset.sourceUrl?.let { Text("Source: $it", style = MaterialTheme.typography.bodySmall) }
        item.asset.sourceName?.let { Text("Source name: $it", style = MaterialTheme.typography.bodySmall) }
        item.asset.credit?.let { Text("Credit: $it", style = MaterialTheme.typography.bodySmall) }
        item.asset.caption?.let { Text("Caption: $it", style = MaterialTheme.typography.bodySmall) }
        val facts = listOfNotNull(
            item.asset.mimeType,
            item.asset.width?.let { width -> item.asset.height?.let { height -> "$width × $height" } },
            item.asset.fileSizeBytes?.let { "$it bytes" },
        )
        if (facts.isNotEmpty()) Text("Facts: ${facts.joinToString(" • ")}", style = MaterialTheme.typography.bodySmall)
        item.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        item.validationIssues.forEach { issue ->
            Text("${issue.severity.name}: ${issue.message}", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ImageReview(asset: ImageAsset, mediaItem: WorkspaceMediaItem?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LocalImagePreview(
            path = asset.localFileReference,
            description = asset.caption ?: "Article image ${asset.id.value}",
        )
        if (mediaItem != null) MediaItemRow(mediaItem)
    }
}

@Composable
private fun LocalImagePreview(path: String?, description: String) {
    val bitmapState = remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmapState.value = withContext(Dispatchers.IO) {
            path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        }
    }
    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = description,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        )
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics { contentDescription = "Image preview belum tersedia" },
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Preview lokal tersedia setelah media READY.")
            }
        }
    }
}

@Composable
private fun ReadyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("article-ready"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("READY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Artikel telah disiapkan secara lokal untuk future publishing pipeline. Artikel belum diterbitkan.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun WorkspacePhase.label(): String = when (this) {
    WorkspacePhase.EMPTY -> "Empty"
    WorkspacePhase.IMPORTING -> "Importing"
    WorkspacePhase.INVALID -> "Needs correction"
    WorkspacePhase.PARSED -> "Article parsed"
    WorkspacePhase.PROCESSING_MEDIA -> "Processing media"
    WorkspacePhase.FAILED -> "Action failed"
    WorkspacePhase.CANCELLED -> "Cancelled"
    WorkspacePhase.READY -> "Ready locally"
}
