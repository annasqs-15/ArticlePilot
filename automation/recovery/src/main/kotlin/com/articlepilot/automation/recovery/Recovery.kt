package com.articlepilot.automation.recovery

import com.articlepilot.automation.state.AutomationCheckpoint

interface RecoveryPolicy {
    fun decide(failure: RecoveryFailure, checkpoint: AutomationCheckpoint): RecoveryDecision
}

sealed interface RecoveryFailure {
    data object NetworkUnavailable : RecoveryFailure
    data object WebViewCrashed : RecoveryFailure
    data object SessionExpired : RecoveryFailure
    data object PageReloaded : RecoveryFailure
    data object SelectorNotFound : RecoveryFailure
    data object UploadFailed : RecoveryFailure
    data object Timeout : RecoveryFailure
    data class Unknown(val message: String) : RecoveryFailure
}

sealed interface RecoveryDecision {
    data class Retry(val maxAttempts: Int, val backoffMillis: Long) : RecoveryDecision
    data class ResumeFromCheckpoint(val checkpoint: AutomationCheckpoint) : RecoveryDecision
    data class PauseForManualTakeover(val reason: String) : RecoveryDecision
    data class Fail(val reason: String) : RecoveryDecision
}

data class ManualTakeoverRequest(
    val sessionId: String,
    val reason: String,
    val expectedEvidence: List<String>,
)
