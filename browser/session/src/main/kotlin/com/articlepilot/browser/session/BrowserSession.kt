package com.articlepilot.browser.session

interface BrowserSession {
    val state: BrowserSessionState

    suspend fun open(url: String): BrowserOperationResult
    suspend fun reload(): BrowserOperationResult
    suspend fun inspect(): BrowserInspection
}

data class BrowserSessionState(
    val currentUrl: String? = null,
    val page: BrowserPage = BrowserPage.UNKNOWN,
    val isAuthenticated: Boolean? = null,
)

enum class BrowserPage {
    UNKNOWN,
    LANDING,
    LOGIN,
    EDITOR,
    REVIEW,
    RESULT,
}

sealed interface BrowserOperationResult {
    data object Succeeded : BrowserOperationResult
    data class Failed(val reason: BrowserFailureReason, val message: String) : BrowserOperationResult
}

enum class BrowserFailureReason {
    INVALID_URL,
    NETWORK,
    SESSION_EXPIRED,
    PAGE_UNRECOGNIZED,
    TIMEOUT,
    WEBVIEW_CRASH,
    UNKNOWN,
}

data class BrowserInspection(
    val page: BrowserPage,
    val url: String?,
    val evidence: List<String>,
)
