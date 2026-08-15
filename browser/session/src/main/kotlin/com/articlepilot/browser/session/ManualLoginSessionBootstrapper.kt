package com.articlepilot.browser.session

/**
 * Current IDN Times reconnaissance did not establish a safe reusable credential/session
 * material format. The supported bootstrap therefore opens a normal WebView for user-controlled
 * authentication and asks BrowserSession to verify the resulting page state.
 */
class ManualLoginSessionBootstrapper : SessionBootstrapper {
    override suspend fun bootstrap(request: SessionBootstrapRequest): SessionBootstrapResult =
        when (request) {
            SessionBootstrapRequest.ManualLogin -> SessionBootstrapResult.SessionReadyForVerification
        }
}
