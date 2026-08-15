package com.articlepilot.media.validator

import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageAssetId
import com.articlepilot.media.inspection.MediaFormat
import com.articlepilot.media.inspection.MediaInspectionResult
import com.articlepilot.media.inspection.MediaInspector
import com.articlepilot.media.inspection.MediaMetadata
import com.articlepilot.media.storage.LocalMediaFile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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

    @Test
    fun `valid inspected image satisfies generic policy`() {
        val result = validator().validateMetadata(asset(), metadata(), GenericImageValidationPolicy())

        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
        assertEquals("image/png", result.metadata?.detectedMimeType)
    }

    @Test
    fun `unsupported MIME produces stable error path`() {
        val result = validator().validateMetadata(
            asset(),
            metadata(mime = "image/bmp"),
            GenericImageValidationPolicy(allowedMimeTypes = setOf("image/png")),
        )

        val issue = result.issues.single()
        assertFalse(result.isValid)
        assertEquals("UNSUPPORTED_MIME_TYPE", issue.code)
        assertEquals("image[asset-1].mimeType", issue.path)
    }

    @Test
    fun `size and dimensions are checked against policy`() {
        val result = validator().validateMetadata(
            asset(),
            metadata(width = 500, height = 2, size = 2048),
            GenericImageValidationPolicy(maxFileSizeBytes = 1024, maxWidth = 100, minHeight = 10),
        )

        assertFalse(result.isValid)
        assertEquals(
            listOf("FILE_SIZE_EXCEEDS_LIMIT", "DIMENSION_ABOVE_MAXIMUM", "DIMENSION_BELOW_MINIMUM"),
            result.issues.map { it.code },
        )
    }

    @Test
    fun `decode verification is a policy decision rather than fabricated fact`() {
        val result = validator().validateMetadata(
            asset(),
            metadata(decodeVerified = false),
            GenericImageValidationPolicy(requireDecodeVerification = true),
        )

        assertFalse(result.isValid)
        assertEquals("DECODE_NOT_VERIFIED", result.issues.single().code)
    }

    @Test
    fun `MIME mismatch can be warning when policy allows it`() {
        val metadata = metadata(
            declaredMime = "image/jpeg",
            issues = listOf(
                com.articlepilot.media.inspection.MediaInspectionIssue(
                    code = "MIME_MISMATCH",
                    message = "Response MIME image/jpeg does not match detected image/png.",
                ),
            ),
        )
        val result = validator().validateMetadata(
            asset(),
            metadata,
            GenericImageValidationPolicy(rejectMimeMismatch = false),
        )

        assertTrue(result.isValid)
        assertEquals(ImageIssueSeverity.WARNING, result.issues.single().severity)
        assertEquals("MIME_MISMATCH", result.issues.single().code)
    }

    @Test
    fun `inspector failure is mapped to explicit invalid result`() = runBlocking {
        val failingInspector = object : MediaInspector {
            override suspend fun inspect(file: LocalMediaFile, policy: com.articlepilot.media.inspection.InspectionPolicy): MediaInspectionResult =
                MediaInspectionResult.Failure(
                    com.articlepilot.media.inspection.MediaInspectionFailureReason.INVALID_IMAGE,
                    "not an image",
                )
        }
        val result = DefaultImageValidator(failingInspector).validate(
            asset(),
            LocalMediaFile("/tmp/missing"),
            GenericImageValidationPolicy(),
        )

        val issue = result.issues.single()
        assertFalse(result.isValid)
        assertEquals("INVALID_IMAGE", issue.code)
        assertEquals("image[asset-1]", issue.path)
    }

    @Test
    fun `policy metadata remains independent from IDN Times profile`() {
        val first = GenericImageValidationPolicy(id = "generic", version = "1", maxWidth = 1000)
        val second = GenericImageValidationPolicy(id = "future-platform", version = "2", maxWidth = 2000)

        assertEquals("generic", first.id)
        assertEquals(1000, first.maxWidth)
        assertEquals("future-platform", second.id)
        assertEquals(2000, second.maxWidth)
    }

    private fun validator() = DefaultImageValidator(
        object : MediaInspector {
            override suspend fun inspect(file: LocalMediaFile, policy: com.articlepilot.media.inspection.InspectionPolicy): MediaInspectionResult =
                MediaInspectionResult.Success(metadata())
        },
    )

    private fun asset() = ImageAsset(
        id = ImageAssetId("asset-1"),
        downloadUrl = "https://example.test/asset.png",
    )

    private fun metadata(
        mime: String = "image/png",
        declaredMime: String? = mime,
        width: Int = 100,
        height: Int = 100,
        size: Long = 512,
        decodeVerified: Boolean = true,
        issues: List<com.articlepilot.media.inspection.MediaInspectionIssue> = emptyList(),
    ) = MediaMetadata(
        file = LocalMediaFile("/tmp/asset.png", mimeType = mime, sizeBytes = size),
        detectedMimeType = mime,
        declaredMimeType = declaredMime,
        width = width,
        height = height,
        sizeBytes = size,
        format = MediaFormat.PNG,
        decodeVerified = decodeVerified,
        issues = issues,
    )
}
