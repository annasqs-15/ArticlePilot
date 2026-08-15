package com.articlepilot.app.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.articlepilot.browser.session.AuthenticationState
import com.articlepilot.browser.session.BrowserOperationResult
import com.articlepilot.browser.session.BrowserSession
import com.articlepilot.browser.session.IdnTimesBrowserProfile
import com.articlepilot.browser.session.ManualLoginSessionBootstrapper
import com.articlepilot.browser.session.SessionBootstrapRequest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val session: BrowserSession,
    private val bootstrapper: ManualLoginSessionBootstrapper = ManualLoginSessionBootstrapper(),
    val profile: IdnTimesBrowserProfile = IdnTimesBrowserProfile.current,
) : ViewModel() {
    val state: StateFlow<com.articlepilot.browser.session.BrowserSessionState> = session.state

    fun startSession() {
        viewModelScope.launch {
            bootstrapper.bootstrap(SessionBootstrapRequest.ManualLogin)
            session.open(profile.contributorEditorUrl)
        }
    }

    fun reload() {
        viewModelScope.launch { session.reload() }
    }

    fun inspect() {
        viewModelScope.launch { session.inspect() }
    }

    fun notifyWebViewAttached() {
        if (state.value.currentUrl == null) startSession()
    }

    fun operationMessage(result: BrowserOperationResult): String? =
        (result as? BrowserOperationResult.Failed)?.message

    override fun onCleared() {
        (session as? com.articlepilot.browser.webview.AndroidBrowserSession)?.dispose()
        super.onCleared()
    }
}

fun AuthenticationState.userLabel(): String = when (this) {
    AuthenticationState.AUTHENTICATING -> "AUTHENTICATING"
    AuthenticationState.AUTHENTICATED -> "AUTHENTICATED"
    AuthenticationState.LOGIN_REQUIRED -> "LOGIN REQUIRED"
    AuthenticationState.SESSION_EXPIRED -> "SESSION EXPIRED"
    AuthenticationState.SECURITY_CHALLENGE -> "SECURITY CHALLENGE"
    AuthenticationState.ERROR -> "ERROR"
    AuthenticationState.UNKNOWN -> "UNKNOWN"
}
