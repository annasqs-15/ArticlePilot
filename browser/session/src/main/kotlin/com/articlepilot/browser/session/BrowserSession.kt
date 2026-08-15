package com.articlepilot.browser.session

import kotlinx.coroutines.flow.StateFlow
import java.net.URI

/**
 * BrowserSession is the platform-neutral contract consumed by UI and future automation.
 * It reports observed state; it never treats a navigation request as proof of success.
 */
interface BrowserSession {
    val state: StateFlow<BrowserSessionState>
    suspend fun open(url: String): BrowserOperationResult
    suspend fun reload(): BrowserOperationResult
    suspend fun inspect(): BrowserInspection
}

data class BrowserSessionState(
    val currentUrl: String? = null,
    val page: BrowserPage = BrowserPage.UNKNOWN,
    val isAuthenticated: Boolean? = null,
    val navigation: NavigationState = NavigationState.IDLE,
    val authentication: AuthenticationState = AuthenticationState.UNKNOWN,
    val title: String? = null,
    val evidence: List<BrowserEvidence> = emptyList(),
    val failureReason: BrowserFailureReason? = null,
    val failureMessage: String? = null,
)

enum class NavigationState {
    IDLE,
    LOADING,
    LOADED,
    FAILED,
    DISPOSED,
}

enum class AuthenticationState {
    AUTHENTICATING,
    AUTHENTICATED,
    LOGIN_REQUIRED,
    SESSION_EXPIRED,
    SECURITY_CHALLENGE,
    UNKNOWN,
    ERROR,
}

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
    SECURITY_ERROR,
    DISPOSED,
    UNKNOWN,
}

data class BrowserInspection(
    val page: BrowserPage,
    val url: String?,
    val evidence: List<String>,
    val authentication: AuthenticationState = AuthenticationState.UNKNOWN,
    val navigation: NavigationState = NavigationState.LOADED,
    val title: String? = null,
)

enum class BrowserEvidence {
    LOGIN_MARKER,
    SESSION_EXPIRED_MARKER,
    SECURITY_CHALLENGE_MARKER,
    AUTHENTICATED_MARKER,
    EDITOR_MARKER,
    PUBLIC_SHELL,
    HTTP_UNAUTHORIZED,
}

data class PageObservation(
    val url: String?,
    val title: String?,
    val signals: Set<BrowserEvidence> = emptySet(),
)

data class AuthenticationClassification(
    val state: AuthenticationState,
    val page: BrowserPage,
    val evidence: Set<BrowserEvidence>,
)

/**
 * A versioned entry-point profile. Empty authenticated/editor marker sets are intentional:
 * the current reconnaissance did not observe a stable authenticated editor marker.
 */
data class IdnTimesBrowserProfile(
    val contributorEditorUrl: String = DEFAULT_EDITOR_URL,
    val allowedOrigins: Set<String> = setOf(DEFAULT_ORIGIN),
    val loginMarkers: Set<String> = setOf("masuk/daftar", "login"),
    val sessionExpiredMarkers: Set<String> = setOf("session expired", "sesi berakhir"),
    val securityChallengeMarkers: Set<String> = setOf(
        "captcha",
        "verify you are human",
        "security challenge",
        "periksa keamanan",
    ),
    val authenticatedMarkers: Set<String> = emptySet(),
    val editorMarkers: Set<String> = emptySet(),
) {
    val originPolicy: AllowedOriginPolicy = AllowedOriginPolicy(allowedOrigins)

    companion object {
        const val DEFAULT_ORIGIN = "https://community.idntimes.com"
        const val DEFAULT_EDITOR_URL = "$DEFAULT_ORIGIN/dashboard/create-article"
        val current: IdnTimesBrowserProfile = IdnTimesBrowserProfile()
    }
}

class AllowedOriginPolicy(origins: Set<String>) {
    private val allowed: Set<Origin> = origins.mapNotNull(::parseOrigin).toSet()

    init {
        require(allowed.isNotEmpty()) { "At least one HTTPS origin is required" }
    }

    fun isAllowed(url: String): Boolean = runCatching {
        parseOrigin(url)?.let { candidate -> allowed.any { it.matches(candidate) } } == true
    }.getOrDefault(false)

    fun validate(url: String): UrlValidation = if (isAllowed(url)) {
        UrlValidation.Valid
    } else {
        UrlValidation.Invalid("Navigation target is outside the approved HTTPS origins")
    }

    private fun parseOrigin(value: String): Origin? = runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase() ?: return@runCatching null
        val host = uri.host?.lowercase() ?: return@runCatching null
        if (scheme != "https" || uri.userInfo != null || uri.fragment != null) return@runCatching null
        val port = if (uri.port == -1) 443 else uri.port
        if (port != 443) return@runCatching null
        Origin(scheme, host, port)
    }.getOrNull()

    private data class Origin(val scheme: String, val host: String, val port: Int) {
        fun matches(candidate: Origin): Boolean = this == candidate
    }
}

sealed interface UrlValidation {
    data object Valid : UrlValidation
    data class Invalid(val reason: String) : UrlValidation
}

/**
 * Authentication is classified from sanitized page observations, never from HTTP 200 or title alone.
 */
class IdnTimesAuthenticationClassifier(
    private val profile: IdnTimesBrowserProfile,
) {
    fun classify(observation: PageObservation): AuthenticationClassification {
        val normalized = listOfNotNull(observation.url, observation.title)
            .joinToString(" ")
            .lowercase()
        val evidence = observation.signals.toMutableSet()

        if (profile.securityChallengeMarkers.any(normalized::contains)) evidence += BrowserEvidence.SECURITY_CHALLENGE_MARKER
        if (profile.sessionExpiredMarkers.any(normalized::contains)) evidence += BrowserEvidence.SESSION_EXPIRED_MARKER
        if (profile.loginMarkers.any(normalized::contains)) evidence += BrowserEvidence.LOGIN_MARKER
        if (profile.authenticatedMarkers.any(normalized::contains)) evidence += BrowserEvidence.AUTHENTICATED_MARKER
        if (profile.editorMarkers.any(normalized::contains)) evidence += BrowserEvidence.EDITOR_MARKER

        val state = when {
            BrowserEvidence.SECURITY_CHALLENGE_MARKER in evidence -> AuthenticationState.SECURITY_CHALLENGE
            BrowserEvidence.SESSION_EXPIRED_MARKER in evidence -> AuthenticationState.SESSION_EXPIRED
            BrowserEvidence.AUTHENTICATED_MARKER in evidence -> AuthenticationState.AUTHENTICATED
            BrowserEvidence.LOGIN_MARKER in evidence -> AuthenticationState.LOGIN_REQUIRED
            else -> AuthenticationState.UNKNOWN
        }
        return AuthenticationClassification(state, classifyPage(observation.url, state), evidence)
    }

    private fun classifyPage(url: String?, authentication: AuthenticationState): BrowserPage {
        val path = runCatching { URI(url ?: "").path.orEmpty().lowercase() }.getOrDefault("")
        return when {
            authentication == AuthenticationState.LOGIN_REQUIRED -> BrowserPage.LOGIN
            authentication == AuthenticationState.AUTHENTICATED && path.contains("create-article") -> BrowserPage.EDITOR
            path.contains("review") -> BrowserPage.REVIEW
            path.contains("result") -> BrowserPage.RESULT
            path.isBlank() || path == "/" -> BrowserPage.LANDING
            else -> BrowserPage.UNKNOWN
        }
    }
}

sealed interface SessionBootstrapRequest {
    data object ManualLogin : SessionBootstrapRequest
}

sealed interface SessionBootstrapResult {
    data object ManualLoginRequired : SessionBootstrapResult
    data object SessionReadyForVerification : SessionBootstrapResult
    data class Rejected(val reason: String) : SessionBootstrapResult
}

/**
 * The bootstrap contract intentionally carries no password, cookie, token, or header.
 * A future provider-specific implementation must first document the real session mechanism.
 */
interface SessionBootstrapper {
    suspend fun bootstrap(request: SessionBootstrapRequest): SessionBootstrapResult
}
