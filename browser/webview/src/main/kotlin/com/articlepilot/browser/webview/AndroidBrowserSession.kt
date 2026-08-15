package com.articlepilot.browser.webview

import com.articlepilot.browser.session.AuthenticationState
import com.articlepilot.browser.session.BrowserEvidence
import com.articlepilot.browser.session.BrowserFailureReason
import com.articlepilot.browser.session.BrowserInspection
import com.articlepilot.browser.session.BrowserOperationResult
import com.articlepilot.browser.session.BrowserPage
import com.articlepilot.browser.session.BrowserSession
import com.articlepilot.browser.session.BrowserSessionState
import com.articlepilot.browser.session.IdnTimesAuthenticationClassifier
import com.articlepilot.browser.session.IdnTimesBrowserProfile
import com.articlepilot.browser.session.NavigationState
import com.articlepilot.browser.session.PageObservation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Android-backed session; authentication is reported only after sanitized inspection evidence. */
class AndroidBrowserSession(
    private val host: WebViewHost,
    private val profile: IdnTimesBrowserProfile = IdnTimesBrowserProfile.current,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : BrowserSession {
    private val classifier = IdnTimesAuthenticationClassifier(profile)
    private val mutableState = MutableStateFlow(BrowserSessionState())
    private var inspectionJob: Job? = null

    override val state: StateFlow<BrowserSessionState> = mutableState.asStateFlow()

    init {
        host.setNavigationObserver { event ->
            when (event) {
                is WebViewNavigationEvent.Started -> mutableState.value = mutableState.value.copy(
                    currentUrl = event.url,
                    navigation = NavigationState.LOADING,
                    authentication = AuthenticationState.AUTHENTICATING,
                    isAuthenticated = null,
                    failureReason = null,
                    failureMessage = null,
                )
                is WebViewNavigationEvent.Finished -> {
                    mutableState.value = mutableState.value.copy(
                        currentUrl = event.url,
                        title = event.title,
                        navigation = NavigationState.LOADED,
                        failureReason = null,
                        failureMessage = null,
                    )
                    scheduleInspection()
                }
                is WebViewNavigationEvent.Failed -> mutableState.value = mutableState.value.copy(
                    currentUrl = event.url,
                    navigation = NavigationState.FAILED,
                    authentication = AuthenticationState.ERROR,
                    isAuthenticated = false,
                    failureReason = BrowserFailureReason.NETWORK,
                    failureMessage = event.message.take(MAX_FAILURE_LENGTH),
                )
                is WebViewNavigationEvent.Crashed -> mutableState.value = mutableState.value.copy(
                    currentUrl = event.url,
                    navigation = NavigationState.FAILED,
                    authentication = AuthenticationState.ERROR,
                    isAuthenticated = false,
                    failureReason = BrowserFailureReason.WEBVIEW_CRASH,
                    failureMessage = "WebView render process stopped",
                )
            }
        }
    }

    override suspend fun open(url: String): BrowserOperationResult {
        when (val validation = profile.originPolicy.validate(url)) {
            is com.articlepilot.browser.session.UrlValidation.Invalid -> {
                val result = BrowserOperationResult.Failed(BrowserFailureReason.INVALID_URL, validation.reason)
                setFailure(result)
                return result
            }
            com.articlepilot.browser.session.UrlValidation.Valid -> Unit
        }
        mutableState.value = mutableState.value.copy(
            currentUrl = url,
            navigation = NavigationState.LOADING,
            authentication = AuthenticationState.AUTHENTICATING,
            isAuthenticated = null,
            failureReason = null,
            failureMessage = null,
        )
        return if (host.load(url)) {
            BrowserOperationResult.Succeeded
        } else {
            val result = BrowserOperationResult.Failed(BrowserFailureReason.SECURITY_ERROR, "WebView rejected the approved navigation target")
            setFailure(result)
            result
        }
    }

    override suspend fun reload(): BrowserOperationResult {
        if (mutableState.value.navigation == NavigationState.DISPOSED) {
            val result = BrowserOperationResult.Failed(BrowserFailureReason.DISPOSED, "Browser session is disposed")
            setFailure(result)
            return result
        }
        mutableState.value = mutableState.value.copy(
            navigation = NavigationState.LOADING,
            authentication = AuthenticationState.AUTHENTICATING,
            isAuthenticated = null,
            failureReason = null,
            failureMessage = null,
        )
        return if (host.reload()) BrowserOperationResult.Succeeded else {
            val result = BrowserOperationResult.Failed(BrowserFailureReason.WEBVIEW_CRASH, "WebView reload was rejected")
            setFailure(result)
            result
        }
    }

    override suspend fun inspect(): BrowserInspection {
        val inspection = host.inspect()
        applyInspection(inspection)
        return inspection
    }

    fun dispose() {
        inspectionJob?.cancel()
        mutableState.value = mutableState.value.copy(navigation = NavigationState.DISPOSED)
        host.detach()
        scope.cancel()
    }

    private fun scheduleInspection() {
        inspectionJob?.cancel()
        inspectionJob = scope.launch { inspect() }
    }

    private fun applyInspection(inspection: BrowserInspection) {
        val signals = inspection.evidence.mapNotNull { raw ->
            runCatching { BrowserEvidence.valueOf(raw) }.getOrNull()
        }.toSet()
        val classification = classifier.classify(
            PageObservation(url = inspection.url, title = inspection.title, signals = signals),
        )
        val isAuthenticated = when (classification.state) {
            AuthenticationState.AUTHENTICATED -> true
            AuthenticationState.LOGIN_REQUIRED,
            AuthenticationState.SESSION_EXPIRED,
            AuthenticationState.SECURITY_CHALLENGE,
            AuthenticationState.ERROR -> false
            AuthenticationState.AUTHENTICATING,
            AuthenticationState.UNKNOWN -> null
        }
        mutableState.value = mutableState.value.copy(
            currentUrl = inspection.url,
            page = classification.page,
            isAuthenticated = isAuthenticated,
            authentication = classification.state,
            navigation = inspection.navigation,
            title = inspection.title,
            evidence = classification.evidence.sortedBy(BrowserEvidence::name),
            failureReason = null,
            failureMessage = null,
        )
    }

    private fun setFailure(result: BrowserOperationResult.Failed) {
        mutableState.value = mutableState.value.copy(
            navigation = NavigationState.FAILED,
            authentication = AuthenticationState.ERROR,
            isAuthenticated = false,
            failureReason = result.reason,
            failureMessage = result.message.take(MAX_FAILURE_LENGTH),
        )
    }

    private companion object {
        const val MAX_FAILURE_LENGTH = 300
    }
}
