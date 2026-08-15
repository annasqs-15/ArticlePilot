package com.articlepilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.articlepilot.app.workspace.application.AndroidMediaPipelineFactory
import com.articlepilot.app.workspace.application.ArticleWorkspaceDependencies
import com.articlepilot.app.workspace.application.ArticleWorkspaceViewModel
import com.articlepilot.app.workspace.application.ArticleWorkspaceViewModelFactory
import com.articlepilot.app.workspace.ui.ArticleWorkspaceScreen
import com.articlepilot.core.parser.ArticleFormatV1Parser
import com.articlepilot.core.validator.ArticleValidationEngine

class MainActivity : ComponentActivity() {
    private val workspaceViewModel: ArticleWorkspaceViewModel by viewModels {
        ArticleWorkspaceViewModelFactory(
            ArticleWorkspaceDependencies(
                parser = ArticleFormatV1Parser(),
                validator = ArticleValidationEngine(),
                mediaPipelineFactory = AndroidMediaPipelineFactory(applicationContext),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArticlePilotTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ArticleWorkspaceScreen(workspaceViewModel)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ArticlePilotTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    MaterialTheme(content = content)
}
