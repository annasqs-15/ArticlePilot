package com.articlepilot.media.validator

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageValidatorTest {
    @Test
    fun image_validation_result_reflects_error_issue() {
        val result = ImageValidationResult(
            isValid = false,
            issues = listOf(
                ImageValidationIssue(
                    code = "INVALID_MIME_TYPE",
                    message = "Image format is not allowed",
                    severity = ImageIssueSeverity.ERROR,
                ),
            ),
        )

        assertFalse(result.isValid)
    }

    @Test
    fun image_validation_result_can_contain_warning_without_invalidating_asset() {
        val result = ImageValidationResult(
            isValid = true,
            issues = listOf(
                ImageValidationIssue(
                    code = "LARGE_DIMENSIONS",
                    message = "Image may be resized",
                    severity = ImageIssueSeverity.WARNING,
                ),
            ),
        )

        assertTrue(result.isValid)
    }
}
