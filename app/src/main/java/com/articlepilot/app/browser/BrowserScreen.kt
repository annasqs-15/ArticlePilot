package com.articlepilot.app.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.articlepilot.browser.session.AuthenticationState
import com.articlepilot.browser.session.BrowserSessionState
import com.articlepilot.browser.session.NavigationState
import com.articlepilot.browser.webview.WebViewHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    webViewHost: WebViewHost,
    onBackToWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val webView = remember { WebViewHolder() }

    DisposableEffect(Unit) {
        onDispose {
            webView.value?.let { it.stopLoading(); (it.parent as? ViewGroup)?.removeView(it) }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("IDN Times Browser") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BrowserStatusCard(state)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::startSession,
                    modifier = Modifier.testTag("start-session-button"),
                ) { Text("Open IDN Times") }
                OutlinedButton(
                    onClick = viewModel::reload,
                    enabled = state.navigation != NavigationState.DISPOSED,
                    modifier = Modifier.testTag("reload-browser-button"),
                ) { Text("Reload") }
                OutlinedButton(onClick = onBackToWorkspace) { Text("Workspace") }
            }
            Card(modifier = Modifier.fillMaxWidth().testTag("manual-login-fallback")) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Manual login fallback", style = MaterialTheme.typography.titleSmall)
                    Text("Jika login diperlukan, gunakan WebView ini secara normal. ArticlePilot tidak mengisi atau menyimpan kredensial.")
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(420.dp).testTag("idn-times-webview"),
                factory = { context ->
                    WebView(context).also { created ->
                        webView.value = created
                        webViewHost.attach(created)
                        viewModel.notifyWebViewAttached()
                    }
                },
                update = { webViewHost.attach(it) },
            )
        }
    }
}

@Composable
private fun BrowserStatusCard(state: BrowserSessionState) {
    val authLabel = state.authentication.name
    Card(modifier = Modifier.fillMaxWidth().testTag("browser-status")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Browser state", style = MaterialTheme.typography.titleMedium)
            Text(authLabel, style = MaterialTheme.typography.titleLarge)
            Text("Page: ${state.page.name}")
            Text("Navigation: ${state.navigation.name}")
            state.currentUrl?.let { Text("URL: $it", maxLines = 2) }
            state.title?.let { Text("Title: $it", maxLines = 2) }
            if (state.evidence.isNotEmpty()) Text("Signals: ${state.evidence.joinToString(", ")}")
            state.failureMessage?.let { Text("Failure: $it", color = MaterialTheme.colorScheme.error) }
            when (state.authentication) {
                AuthenticationState.AUTHENTICATED -> Text("Authenticated editor evidence observed.")
                AuthenticationState.LOGIN_REQUIRED,
                AuthenticationState.SESSION_EXPIRED -> Text("Silakan login secara manual; setelah selesai, tekan Reload.")
                AuthenticationState.SECURITY_CHALLENGE -> Text("Security challenge terdeteksi. Selesaikan hanya secara manual dan jangan lanjutkan otomasi.")
                AuthenticationState.AUTHENTICATING,
                AuthenticationState.UNKNOWN,
                AuthenticationState.ERROR -> Unit
            }
        }
    }
}

private class WebViewHolder {
    var value: WebView? = null
}
