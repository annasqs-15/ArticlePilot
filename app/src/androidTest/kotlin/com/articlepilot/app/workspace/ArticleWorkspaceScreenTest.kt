package com.articlepilot.app.workspace

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.articlepilot.app.workspace.application.AndroidMediaPipelineFactory
import com.articlepilot.app.workspace.application.ArticleWorkspaceViewModel
import com.articlepilot.app.workspace.ui.ArticleWorkspaceScreen
import com.articlepilot.core.parser.ArticleFormatV1Parser
import com.articlepilot.core.validator.ArticleValidationEngine
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleWorkspaceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun validArticleRendersMetadataAndOrderedBlocks() {
        val viewModel = createViewModel()
        composeRule.setContent { ArticleWorkspaceScreen(viewModel) }

        composeRule.runOnIdle {
            viewModel.updateSource(validSource())
            viewModel.parse()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.article != null
        }

        composeRule.onNodeWithTag("article-review").assertIsDisplayed()
        composeRule.onNodeWithText("Workspace UI title").assertIsDisplayed()
        composeRule.onNodeWithText("Workspace UI excerpt").assertIsDisplayed()
        composeRule.onNodeWithText("Category: Technology").assertIsDisplayed()
        composeRule.onNodeWithText("Tags: android, workspace").assertIsDisplayed()
        composeRule.onNodeWithText("First ordered paragraph").assertIsDisplayed()
        composeRule.onNodeWithText("Second ordered paragraph").assertIsDisplayed()
        composeRule.onNodeWithText("Download: https://example.test/image.png").assertIsDisplayed()

        composeRule.runOnIdle {
            check(viewModel.uiState.value.sourceText == validSource())
        }
    }

    @Test
    fun invalidArticleRendersStructuredDiagnostics() {
        val viewModel = createViewModel()
        composeRule.setContent { ArticleWorkspaceScreen(viewModel) }

        composeRule.runOnIdle {
            viewModel.updateSource("@article\n@excerpt\nMissing title")
            viewModel.parse()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.parseDiagnostics.isNotEmpty()
        }

        composeRule.onNodeWithTag("parse-diagnostics").assertIsDisplayed()
        composeRule.onNodeWithText("MISSING_TITLE").assertIsDisplayed()
        composeRule.onNodeWithText("Article requires exactly one non-blank title.").assertIsDisplayed()
    }

    private fun createViewModel(): ArticleWorkspaceViewModel = ArticleWorkspaceViewModel(
        parser = ArticleFormatV1Parser(),
        validator = ArticleValidationEngine(),
        mediaPipelineFactory = AndroidMediaPipelineFactory(context),
        workerDispatcher = Dispatchers.Default,
    )

    private fun validSource(): String = buildString {
        appendLine("@article")
        appendLine("@title")
        appendLine("Workspace UI title")
        appendLine("@excerpt")
        appendLine("Workspace UI excerpt")
        appendLine("@category")
        appendLine("Technology")
        appendLine("@tag")
        appendLine("android")
        appendLine("@tag")
        appendLine("workspace")
        appendLine("@section")
        appendLine("@heading")
        appendLine("Ordered section")
        appendLine("@text")
        appendLine("First ordered paragraph")
        appendLine("@end-text")
        appendLine("@image")
        appendLine("download-url: https://example.test/image.png")
        appendLine("caption: Ordered image")
        appendLine("@end-image")
        appendLine("@text")
        appendLine("Second ordered paragraph")
        appendLine("@end-text")
    }
}
