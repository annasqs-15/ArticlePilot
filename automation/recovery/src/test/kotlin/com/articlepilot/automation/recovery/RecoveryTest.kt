package com.articlepilot.automation.recovery

import com.articlepilot.automation.state.AutomationCheckpoint
import com.articlepilot.automation.state.AutomationPhase
import com.articlepilot.core.model.ArticleId
import kotlin.test.Test
import kotlin.test.assertTrue

class RecoveryTest {
    @Test
    fun selector_failure_is_a_manual_takeover_boundary() {
        val checkpoint = AutomationCheckpoint(
            sessionId = "session-1",
            articleId = ArticleId("article-1"),
            phase = AutomationPhase.UPLOAD_IMAGE,
            updatedAtEpochMillis = 1L,
        )
        val decision = RecoveryDecision.PauseForManualTakeover(
            reason = "Image upload element could not be identified",
        )

        assertTrue(decision.reason.contains("could not be identified"))
        assertTrue(checkpoint.phase == AutomationPhase.UPLOAD_IMAGE)
    }
}
