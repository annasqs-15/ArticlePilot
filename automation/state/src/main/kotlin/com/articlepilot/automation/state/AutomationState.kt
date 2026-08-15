package com.articlepilot.automation.state

import com.articlepilot.core.model.ArticleId
import kotlinx.serialization.Serializable

@Serializable
enum class AutomationPhase {
    START,
    OPEN_SITE,
    CHECK_SESSION,
    OPEN_EDITOR,
    FILL_METADATA,
    UPLOAD_COVER,
    CREATE_SECTION,
    WRITE_SECTION,
    UPLOAD_IMAGE,
    FILL_IMAGE_METADATA,
    NEXT_SECTION,
    FINAL_REVIEW,
    SUBMIT,
    VERIFY,
    COMPLETE,
}

@Serializable
data class AutomationCheckpoint(
    val sessionId: String,
    val articleId: ArticleId,
    val phase: AutomationPhase,
    val sectionIndex: Int? = null,
    val blockId: String? = null,
    val attempt: Int = 0,
    val lastVerifiedEvidence: List<String> = emptyList(),
    val updatedAtEpochMillis: Long,
)

sealed interface AutomationEvent {
    data object Begin : AutomationEvent
    data class Verified(val evidence: List<String>) : AutomationEvent
    data class Failed(val reason: AutomationFailure) : AutomationEvent
    data class ManualTakeoverCompleted(val evidence: List<String>) : AutomationEvent
    data object Cancel : AutomationEvent
}

sealed interface AutomationFailure {
    val message: String
    val recoverable: Boolean

    data class Retryable(override val message: String) : AutomationFailure {
        override val recoverable: Boolean = true
    }

    data class Pausable(override val message: String) : AutomationFailure {
        override val recoverable: Boolean = true
    }

    data class Fatal(override val message: String) : AutomationFailure {
        override val recoverable: Boolean = false
    }
}

sealed interface StateTransition {
    data class Advanced(val checkpoint: AutomationCheckpoint) : StateTransition
    data class Paused(val checkpoint: AutomationCheckpoint, val reason: String) : StateTransition
    data class Failed(val checkpoint: AutomationCheckpoint, val reason: String) : StateTransition
    data class Completed(val checkpoint: AutomationCheckpoint) : StateTransition
}

interface AutomationStateMachine {
    suspend fun accept(checkpoint: AutomationCheckpoint, event: AutomationEvent): StateTransition
}
