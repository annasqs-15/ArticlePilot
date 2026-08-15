package com.articlepilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.articlepilot.app.browser.BrowserScreen
import com.articlepilot.app.browser.BrowserViewModel
import com.articlepilot.app.browser.BrowserViewModelFactory
import com.articlepilot.app.workspace.application.AndroidMediaPipelineFactory
import com.articlepilot.app.workspace.application.ArticleWorkspaceDependencies
import com.articlepilot.app.workspace.application.ArticleWorkspaceViewModel
import com.articlepilot.app.workspace.application.ArticleWorkspaceViewModelFactory
import com.articlepilot.app.workspace.ui.ArticleWorkspaceScreen
import com.articlepilot.browser.session.IdnTimesBrowserProfile
import com.articlepilot.browser.webview.AndroidBrowserSession
import com.articlepilot.browser.webview.AndroidWebViewHost
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

    private val browserProfile = IdnTimesBrowserProfile.current
    private val browserHost = AndroidWebViewHost(browserProfile.originPolicy)
    private val browserSession = AndroidBrowserSession(browserHost, browserProfile)
    private val browserViewModel: BrowserViewModel by viewModels {
        BrowserViewModelFactory(browserSession)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArticlePilotTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ArticlePilotRoot(
                        workspaceViewModel = workspaceViewModel,
                        browserViewModel = browserViewModel,
                        browserHost = browserHost,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        browserSession.dispose()
        super.onDestroy()
    }
}

@Composable
private fun ArticlePilotRoot(
    workspaceViewModel: ArticleWorkspaceViewModel,
    browserViewModel: BrowserViewModel,
    browserHost: AndroidWebViewHost,
) {
    var showBrowser by remember { mutableStateOf(false) }
    if (showBrowser) {
        BrowserScreen(
            viewModel = browserViewModel,
            webViewHost = browserHost,
            onBackToWorkspace = { showBrowser = false },
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = { showBrowser = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { androidx.compose.material3.Text("Open IDN Times Browser") }
            ArticleWorkspaceScreen(workspaceViewModel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArticlePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
