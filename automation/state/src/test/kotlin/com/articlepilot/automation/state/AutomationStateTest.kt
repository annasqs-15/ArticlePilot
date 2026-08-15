package com.articlepilot.automation.state

import com.articlepilot.core.model.ArticleId
import kotlin.test.Test
import kotlin.test.assertEquals

class AutomationStateTest {
    @Test
    fun checkpoint_preserves_resume_position_and_verified_evidence() {
        val checkpoint = AutomationCheckpoint(
            sessionId = "session-1",
            articleId = ArticleId("article-1"),
            phase = AutomationPhase.UPLOAD_IMAGE,
            sectionIndex = 2,
            blockId = "image-4",
            attempt = 1,
            lastVerifiedEvidence = listOf("image-upload-input-present"),
            updatedAtEpochMillis = 1234L,
        )

        assertEquals(AutomationPhase.UPLOAD_IMAGE, checkpoint.phase)
        assertEquals("image-4", checkpoint.blockId)
        assertEquals("image-upload-input-present", checkpoint.lastVerifiedEvidence.single())
    }
}
