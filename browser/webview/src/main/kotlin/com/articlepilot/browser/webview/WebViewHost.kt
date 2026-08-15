package com.articlepilot.browser.webview

import android.webkit.WebView
import com.articlepilot.browser.session.BrowserInspection
import com.articlepilot.browser.session.BrowserPage
import com.articlepilot.browser.session.NavigationState

/** Explicit fallback for tests or non-Android composition roots; never reports success. */
class UnimplementedWebViewHost : WebViewHost {
    override fun attach(webView: WebView) = Unit
    override fun detach() = Unit
    override fun setNavigationObserver(observer: WebViewNavigationObserver?) = Unit
    override fun load(url: String): Boolean = false
    override fun reload(): Boolean = false
    override fun stopLoading() = Unit
    override suspend fun inspect(): BrowserInspection =
        BrowserInspection(
            page = BrowserPage.UNKNOWN,
            url = null,
            evidence = listOf("WEBVIEW_HOST_NOT_CONFIGURED"),
            navigation = NavigationState.FAILED,
        )
}
