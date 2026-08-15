package com.articlepilot.browser.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.ClientCertRequest
import android.webkit.SafeBrowsingResponse
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.articlepilot.browser.session.AllowedOriginPolicy
import com.articlepilot.browser.session.BrowserEvidence
import com.articlepilot.browser.session.BrowserInspection
import com.articlepilot.browser.session.BrowserPage
import com.articlepilot.browser.session.NavigationState
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

fun interface WebViewNavigationObserver {
    fun onNavigation(event: WebViewNavigationEvent)
}

sealed interface WebViewNavigationEvent {
    data class Started(val url: String?) : WebViewNavigationEvent
    data class Finished(val url: String?, val title: String?) : WebViewNavigationEvent
    data class Failed(val url: String?, val message: String) : WebViewNavigationEvent
    data class Crashed(val url: String?) : WebViewNavigationEvent
}

interface WebViewHost {
    fun attach(webView: WebView)
    fun detach()
    fun setNavigationObserver(observer: WebViewNavigationObserver?)
    fun load(url: String): Boolean
    fun reload(): Boolean
    fun stopLoading()
    suspend fun inspect(): BrowserInspection
}

data class JavascriptEvaluation(
    val value: String?,
    val succeeded: Boolean,
    val errorMessage: String? = null,
)

/**
 * Real Android WebView adapter. It owns WebView configuration and lifecycle callbacks;
 * Compose and automation code only consume this boundary.
 */
class AndroidWebViewHost(
    private val originPolicy: AllowedOriginPolicy,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : WebViewHost {
    private var webView: WebView? = null
    private var observer: WebViewNavigationObserver? = null
    private var disposed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun attach(webView: WebView) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView must be attached on the main thread" }
        disposed = false
        this.webView = webView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.setSupportMultipleWindows(false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_OFF)
        }
        webView.webViewClient = restrictedClient()
    }

    override fun detach() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            disposeOnMainThread()
        } else {
            mainHandler.post(::disposeOnMainThread)
        }
    }

    override fun setNavigationObserver(observer: WebViewNavigationObserver?) {
        mainHandler.post { this.observer = observer }
    }

    override fun load(url: String): Boolean {
        if (!originPolicy.isAllowed(url)) return false
        if (disposed) return false
        mainHandler.post {
            if (!disposed && originPolicy.isAllowed(url)) webView?.loadUrl(url)
        }
        return true
    }

    override fun reload(): Boolean {
        if (disposed) return false
        mainHandler.post { if (!disposed) webView?.reload() }
        return true
    }

    override fun stopLoading() {
        mainHandler.post { webView?.stopLoading() }
    }

    private suspend fun evaluateJavascript(script: String): JavascriptEvaluation =
        suspendCancellableCoroutine { continuation ->
            val currentWebView = webView
            if (disposed || currentWebView == null) {
                continuation.resume(JavascriptEvaluation(null, false, "WebView is not attached"))
                return@suspendCancellableCoroutine
            }
            mainHandler.post {
                if (disposed) {
                    continuation.resume(JavascriptEvaluation(null, false, "WebView is disposed"))
                } else {
                    runCatching {
                        currentWebView.evaluateJavascript(script) { value ->
                            if (continuation.isActive) continuation.resume(JavascriptEvaluation(value, true))
                        }
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(
                            JavascriptEvaluation(null, false, it.message ?: "JavaScript evaluation failed"),
                        )
                    }
                }
            }
            continuation.invokeOnCancellation { mainHandler.post { currentWebView.stopLoading() } }
        }

    override suspend fun inspect(): BrowserInspection {
        val current = webView
        if (disposed || current == null) {
            return BrowserInspection(
                page = BrowserPage.UNKNOWN,
                url = null,
                evidence = listOf("WEBVIEW_UNAVAILABLE"),
                navigation = NavigationState.FAILED,
            )
        }
        val evaluation = evaluateJavascript(SAFE_INSPECTION_SCRIPT)
        if (!evaluation.succeeded) {
            return BrowserInspection(
                page = BrowserPage.UNKNOWN,
                url = current.url,
                evidence = listOf("INSPECTION_FAILED"),
                navigation = NavigationState.FAILED,
                title = current.title?.take(MAX_TITLE_LENGTH),
            )
        }
        val payload = runCatching { JSONObject(unquoteJavascriptString(evaluation.value.orEmpty())) }
            .getOrNull()
        val evidence = buildList {
            if (payload?.optBoolean("login", false) == true) add(BrowserEvidence.LOGIN_MARKER.name)
            if (payload?.optBoolean("expired", false) == true) add(BrowserEvidence.SESSION_EXPIRED_MARKER.name)
            if (payload?.optBoolean("challenge", false) == true) add(BrowserEvidence.SECURITY_CHALLENGE_MARKER.name)
            if (payload?.optBoolean("publicShell", false) == true) add(BrowserEvidence.PUBLIC_SHELL.name)
        }
        return BrowserInspection(
            page = BrowserPage.UNKNOWN,
            url = current.url,
            evidence = evidence,
            navigation = NavigationState.LOADED,
            title = payload?.optString("title")?.take(MAX_TITLE_LENGTH) ?: current.title?.take(MAX_TITLE_LENGTH),
        )
    }

    private fun restrictedClient(): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !originPolicy.isAllowed(request.url.toString())

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
            super.shouldInterceptRequest(view, request)

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            observer?.onNavigation(WebViewNavigationEvent.Started(url))
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            observer?.onNavigation(WebViewNavigationEvent.Finished(url, view.title?.take(MAX_TITLE_LENGTH)))
            super.onPageFinished(view, url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                observer?.onNavigation(WebViewNavigationEvent.Failed(request.url.toString(), error.description.toString()))
            }
            super.onReceivedError(view, request, error)
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                observer?.onNavigation(WebViewNavigationEvent.Failed(request.url.toString(), "HTTP ${errorResponse.statusCode}"))
            }
            super.onReceivedHttpError(view, request, errorResponse)
        }

        override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
            handler.cancel()
            observer?.onNavigation(WebViewNavigationEvent.Failed(view.url, "TLS validation failed"))
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) = request.cancel()

        override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                callback.backToSafety(true)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            observer?.onNavigation(WebViewNavigationEvent.Crashed(view.url))
            return true
        }
    }

    private fun disposeOnMainThread() {
        disposed = true
        observer = null
        webView?.apply {
            stopLoading()
            webViewClient = WebViewClient()
            removeAllViews()
            destroy()
        }
        webView = null
    }

    private fun unquoteJavascriptString(value: String): String =
        runCatching { JSONObject("{\"value\":$value}").optString("value") }.getOrDefault(value)

    private companion object {
        const val MAX_TITLE_LENGTH = 200
        const val SAFE_INSPECTION_SCRIPT = """
            (() => {
              const text = (document.body?.innerText || '').slice(0, 5000).toLowerCase();
              const title = (document.title || '').slice(0, 200);
              return JSON.stringify({
                title,
                login: text.includes('masuk/daftar') || text.includes('login'),
                expired: text.includes('session expired') || text.includes('sesi berakhir'),
                challenge: text.includes('captcha') || text.includes('verify you are human') || text.includes('security challenge'),
                publicShell: text.includes('masuk/daftar')
              });
            })()
        """
    }
}
