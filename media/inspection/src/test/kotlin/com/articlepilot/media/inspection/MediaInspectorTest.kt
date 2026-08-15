package com.articlepilot.media.inspection

import com.articlepilot.media.storage.LocalMediaFile
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaInspectorTest {
    private val inspector = DefaultMediaInspector()

    @Test
    fun `valid PNG is decoded and dimensions are reported`() = runBlocking {
        val file = Files.createTempFile("articlepilot-inspection", ".png")
        ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", file.toFile())

        val result = inspector.inspect(LocalMediaFile(file.toString(), mimeType = "image/png"))
        val success = assertIs<MediaInspectionResult.Success>(result)
        assertEquals(MediaFormat.PNG, success.metadata.format)
        assertEquals("image/png", success.metadata.detectedMimeType)
        assertEquals(3, success.metadata.width)
        assertEquals(2, success.metadata.height)
        assertTrue(success.metadata.decodeVerified)
        assertTrue(success.metadata.issues.isEmpty())
    }

    @Test
    fun `MIME mismatch is surfaced without trusting response header`() = runBlocking {
        val file = Files.createTempFile("articlepilot-inspection-mismatch", ".png")
        ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", file.toFile())

        val result = inspector.inspect(LocalMediaFile(file.toString(), mimeType = "image/jpeg"))
        val success = assertIs<MediaInspectionResult.Success>(result)
        assertEquals("image/png", success.metadata.detectedMimeType)
        assertEquals("MIME_MISMATCH", success.metadata.issues.single().code)
    }

    @Test
    fun `unsupported bytes are rejected as invalid image`() = runBlocking {
        val file = Files.createTempFile("articlepilot-inspection-invalid", ".bin")
        Files.writeString(file, "<html>not an image</html>")

        val result = inspector.inspect(LocalMediaFile(file.toString()))
        val failure = assertIs<MediaInspectionResult.Failure>(result)
        assertEquals(MediaInspectionFailureReason.INVALID_IMAGE, failure.reason)
    }

    @Test
    fun `missing file produces explicit failure`() = runBlocking {
        val path = Files.createTempDirectory("articlepilot-inspection-missing").resolve("missing.png")
        val result = inspector.inspect(LocalMediaFile(path.toString()))
        val failure = assertIs<MediaInspectionResult.Failure>(result)
        assertEquals(MediaInspectionFailureReason.FILE_NOT_FOUND, failure.reason)
    }

    @Test
    fun `WebP VP8X header provides dimensions but remains decode-unverified`() = runBlocking {
        val file = Files.createTempFile("articlepilot-inspection-webp", ".webp")
        val bytes = ByteArray(30)
        "RIFF".toByteArray().copyInto(bytes, 0)
        "WEBP".toByteArray().copyInto(bytes, 8)
        "VP8X".toByteArray().copyInto(bytes, 12)
        writeLittleEndian24(bytes, 24, 639)
        writeLittleEndian24(bytes, 27, 479)
        Files.write(file, bytes)

        val result = inspector.inspect(LocalMediaFile(file.toString(), mimeType = "image/webp"))
        val success = assertIs<MediaInspectionResult.Success>(result)
        assertEquals(MediaFormat.WEBP, success.metadata.format)
        assertEquals(640, success.metadata.width)
        assertEquals(480, success.metadata.height)
        assertFalse(success.metadata.decodeVerified)
        assertTrue(success.metadata.issues.any { it.code == "WEBP_HEADER_ONLY_VERIFICATION" })
    }

    private fun writeLittleEndian24(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xff).toByte()
    }
}
