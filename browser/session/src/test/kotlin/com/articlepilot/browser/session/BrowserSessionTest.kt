package com.articlepilot.browser.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserSessionTest {
    private val profile = IdnTimesBrowserProfile.current
    private val classifier = IdnTimesAuthenticationClassifier(profile)

    @Test
    fun failed_operation_keeps_explicit_failure_reason() {
        val result = BrowserOperationResult.Failed(
            reason = BrowserFailureReason.PAGE_UNRECOGNIZED,
            message = "No known page evidence",
        )
        assertEquals(BrowserFailureReason.PAGE_UNRECOGNIZED, result.reason)
    }

    @Test
    fun approved_origin_accepts_only_https_community_origin() {
        assertTrue(profile.originPolicy.isAllowed(profile.contributorEditorUrl))
        assertTrue(profile.originPolicy.isAllowed("https://community.idntimes.com/dashboard"))
        assertFalse(profile.originPolicy.isAllowed("http://community.idntimes.com/dashboard"))
        assertFalse(profile.originPolicy.isAllowed("https://evil.example/dashboard"))
        assertFalse(profile.originPolicy.isAllowed("https://community.idntimes.com.evil.example/dashboard"))
    }

    @Test
    fun public_login_marker_is_login_required_even_when_dashboard_title_is_present() {
        val result = classifier.classify(
            PageObservation(
                url = "https://community.idntimes.com/dashboard",
                title = "Manage Article Community | IDN Times",
                signals = setOf(BrowserEvidence.LOGIN_MARKER),
            ),
        )
        assertEquals(AuthenticationState.LOGIN_REQUIRED, result.state)
        assertEquals(BrowserPage.LOGIN, result.page)
    }

    @Test
    fun http_success_or_title_alone_does_not_prove_authentication() {
        val result = classifier.classify(
            PageObservation(
                url = profile.contributorEditorUrl,
                title = "Manage Article Community | IDN Times",
            ),
        )
        assertEquals(AuthenticationState.UNKNOWN, result.state)
        assertEquals(BrowserPage.UNKNOWN, result.page)
    }

    @Test
    fun security_challenge_has_priority_over_login_marker() {
        val result = classifier.classify(
            PageObservation(
                url = "https://community.idntimes.com/login",
                title = "Verify you are human",
                signals = setOf(BrowserEvidence.LOGIN_MARKER),
            ),
        )
        assertEquals(AuthenticationState.SECURITY_CHALLENGE, result.state)
    }

    @Test
    fun authenticated_editor_requires_explicit_profile_evidence() {
        val authenticatedProfile = profile.copy(
            authenticatedMarkers = setOf("editor shell"),
            editorMarkers = setOf("editor shell"),
        )
        val result = IdnTimesAuthenticationClassifier(authenticatedProfile).classify(
            PageObservation(
                url = authenticatedProfile.contributorEditorUrl,
                title = "Editor shell",
            ),
        )
        assertEquals(AuthenticationState.AUTHENTICATED, result.state)
        assertEquals(BrowserPage.EDITOR, result.page)
    }

    @Test
    fun session_expired_is_distinguished_from_login_when_observed() {
        val result = classifier.classify(
            PageObservation(
                url = "https://community.idntimes.com/dashboard/create-article",
                title = "Sesi berakhir",
            ),
        )
        assertEquals(AuthenticationState.SESSION_EXPIRED, result.state)
    }
}
