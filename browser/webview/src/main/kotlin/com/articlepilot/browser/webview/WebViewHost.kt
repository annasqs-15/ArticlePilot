package com.articlepilot.browser.webview

import android.webkit.WebView
import com.articlepilot.browser.session.BrowserInspection
interface WebViewHost {
    fun attach(webView: WebView)
    fun detach()
    suspend fun evaluateJavascript(script: String): JavascriptEvaluation
    suspend fun inspect(): BrowserInspection
}

data class JavascriptEvaluation(
    val value: String?,
    val succeeded: Boolean,
    val errorMessage: String? = null,
)

/**
 * Adapter boundary only. Production implementation must verify DOM evidence and lifecycle state
 * before reporting success; it must not infer success from a dispatched WebView action.
 */
class UnimplementedWebViewHost : WebViewHost {
    override fun attach(webView: WebView) = Unit

    override fun detach() = Unit

    override suspend fun evaluateJavascript(script: String): JavascriptEvaluation =
        JavascriptEvaluation(
            value = null,
            succeeded = false,
            errorMessage = "WebView bridge is not implemented yet",
        )

    override suspend fun inspect(): BrowserInspection =
        BrowserInspection(
            page = com.articlepilot.browser.session.BrowserPage.UNKNOWN,
            url = null,
            evidence = emptyList(),
        )
}
