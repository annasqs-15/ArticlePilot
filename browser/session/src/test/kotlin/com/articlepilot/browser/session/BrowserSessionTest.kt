package com.articlepilot.browser.session

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserSessionTest {
    @Test
    fun failed_operation_keeps_explicit_failure_reason() {
        val result = BrowserOperationResult.Failed(
            reason = BrowserFailureReason.PAGE_UNRECOGNIZED,
            message = "No known page evidence",
        )

        assertEquals(BrowserFailureReason.PAGE_UNRECOGNIZED, result.reason)
    }
}
