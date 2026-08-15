package com.articlepilot.media.inspection

import com.articlepilot.media.storage.LocalMediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream

interface MediaInspector {
    suspend fun inspect(file: LocalMediaFile, policy: InspectionPolicy = InspectionPolicy()): MediaInspectionResult
}

data class InspectionPolicy(
    val maxDecodedPixels: Long = 40_000_000L,
) {
    init {
        require(maxDecodedPixels > 0) { "maxDecodedPixels must be positive" }
    }
}

data class MediaMetadata(
    val file: LocalMediaFile,
    val detectedMimeType: String,
    val declaredMimeType: String?,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val format: MediaFormat,
    val decodeVerified: Boolean,
    val issues: List<MediaInspectionIssue> = emptyList(),
)

data class MediaInspectionIssue(
    val code: String,
    val message: String,
    val severity: MediaInspectionSeverity = MediaInspectionSeverity.ERROR,
)

enum class MediaInspectionSeverity {
    ERROR,
    WARNING,
}

enum class MediaFormat(val mimeType: String) {
    JPEG("image/jpeg"),
    PNG("image/png"),
    GIF("image/gif"),
    WEBP("image/webp"),
}

sealed interface MediaInspectionResult {
    data class Success(val metadata: MediaMetadata) : MediaInspectionResult
    data class Failure(
        val reason: MediaInspectionFailureReason,
        val message: String,
    ) : MediaInspectionResult
}

enum class MediaInspectionFailureReason {
    FILE_NOT_FOUND,
    EMPTY_FILE,
    READ_FAILED,
    INVALID_IMAGE,
    UNSUPPORTED_FORMAT,
    DECODE_FAILED,
    DIMENSION_INVALID,
}

class DefaultMediaInspector : MediaInspector {
    override suspend fun inspect(file: LocalMediaFile, policy: InspectionPolicy): MediaInspectionResult =
        withContext(Dispatchers.IO) {
            val path = Path.of(file.path)
            if (!Files.exists(path)) {
                return@withContext MediaInspectionResult.Failure(
                    MediaInspectionFailureReason.FILE_NOT_FOUND,
                    "Media file does not exist: ${file.path}",
                )
            }
            val size = try {
                Files.size(path)
            } catch (error: IOException) {
                return@withContext MediaInspectionResult.Failure(
                    MediaInspectionFailureReason.READ_FAILED,
                    "Unable to inspect file size: ${error.message ?: error::class.simpleName}",
                )
            }
            if (size <= 0) {
                return@withContext MediaInspectionResult.Failure(
                    MediaInspectionFailureReason.EMPTY_FILE,
                    "Media file is empty: ${file.path}",
                )
            }

            val format = sniffFormat(path)
                ?: return@withContext MediaInspectionResult.Failure(
                    MediaInspectionFailureReason.INVALID_IMAGE,
                    "File signature is not a supported image format.",
                )

            if (format == MediaFormat.WEBP) {
                return@withContext inspectWebp(file, path, size, format)
            }

            val decoded = decodeWithImageIo(path, policy)
                ?: return@withContext MediaInspectionResult.Failure(
                    MediaInspectionFailureReason.DECODE_FAILED,
                    "Image decoder could not verify ${format.mimeType}.",
                )
            val declaredMime = file.mimeType?.substringBefore(';')?.trim()?.lowercase()
            val issues = if (declaredMime != null && declaredMime != format.mimeType) {
                listOf(
                    MediaInspectionIssue(
                        code = "MIME_MISMATCH",
                        message = "Response MIME $declaredMime does not match detected ${format.mimeType}.",
                    ),
                )
            } else {
                emptyList()
            }
            val metadata = MediaMetadata(
                file = file.copy(
                    mimeType = format.mimeType,
                    sizeBytes = size,
                ),
                detectedMimeType = format.mimeType,
                declaredMimeType = declaredMime,
                width = decoded.width,
                height = decoded.height,
                sizeBytes = size,
                format = format,
                decodeVerified = true,
                issues = issues,
            )
            MediaInspectionResult.Success(metadata)
        }

    private fun decodeWithImageIo(path: Path, policy: InspectionPolicy): DecodedDimensions? {
        ImageIO.createImageInputStream(path.toFile()).use { input: ImageInputStream? ->
            if (input == null) return null
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            return try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width <= 0 || height <= 0) return null
                val param = boundedReadParam(reader, width, height, policy.maxDecodedPixels)
                reader.read(0, param)
                DecodedDimensions(width, height)
            } catch (_: Exception) {
                null
            } finally {
                reader.dispose()
            }
        }
    }

    private fun boundedReadParam(
        reader: ImageReader,
        width: Int,
        height: Int,
        maxPixels: Long,
    ): ImageReadParam {
        val param = reader.defaultReadParam
        val pixels = width.toLong() * height.toLong()
        if (pixels > maxPixels) {
            val scale = kotlin.math.sqrt(pixels.toDouble() / maxPixels.toDouble()).toInt().coerceAtLeast(1)
            param.setSourceSubsampling(scale, scale, 0, 0)
        }
        return param
    }

    private fun inspectWebp(
        file: LocalMediaFile,
        path: Path,
        size: Long,
        format: MediaFormat,
    ): MediaInspectionResult {
        val bytes = Files.readAllBytes(path)
        if (bytes.size < 16 || !bytes.copyOfRange(0, 4).contentEquals(RIFF) || !bytes.copyOfRange(8, 12).contentEquals(WEBP)) {
            return MediaInspectionResult.Failure(
                MediaInspectionFailureReason.INVALID_IMAGE,
                "WebP RIFF signature is invalid.",
            )
        }
        val chunk = bytes.copyOfRange(12, minOf(16, bytes.size))
        if (!chunk.contentEquals(VP8X)) {
            return MediaInspectionResult.Failure(
                MediaInspectionFailureReason.UNSUPPORTED_FORMAT,
                "Only WebP VP8X metadata is supported by the JVM inspector; pixel decoding is not available without an image plugin.",
            )
        }
        if (bytes.size < 30) {
            return MediaInspectionResult.Failure(
                MediaInspectionFailureReason.INVALID_IMAGE,
                "WebP VP8X header is truncated.",
            )
        }
        val width = readLittleEndian24(bytes, 24) + 1
        val height = readLittleEndian24(bytes, 27) + 1
        if (width <= 0 || height <= 0) {
            return MediaInspectionResult.Failure(
                MediaInspectionFailureReason.DIMENSION_INVALID,
                "WebP dimensions must be positive.",
            )
        }
        val declaredMime = file.mimeType?.substringBefore(';')?.trim()?.lowercase()
        val issues = if (declaredMime != null && declaredMime != format.mimeType) {
            listOf(MediaInspectionIssue("MIME_MISMATCH", "Response MIME $declaredMime does not match detected ${format.mimeType}."))
        } else emptyList()
        return MediaInspectionResult.Success(
            MediaMetadata(
                file = file.copy(mimeType = format.mimeType, sizeBytes = size),
                detectedMimeType = format.mimeType,
                declaredMimeType = declaredMime,
                width = width,
                height = height,
                sizeBytes = size,
                format = format,
                decodeVerified = false,
                issues = issues + MediaInspectionIssue(
                    code = "WEBP_HEADER_ONLY_VERIFICATION",
                    message = "WebP metadata was inspected from the VP8X header; a full JVM pixel decoder is not installed.",
                    severity = MediaInspectionSeverity.WARNING,
                ),
            ),
        )
    }

    private fun sniffFormat(path: Path): MediaFormat? {
        Files.newInputStream(path).use { input ->
            val header = ByteArray(32)
            val read = input.read(header)
            if (read < 12) return null
            return when {
                header.startsWith(JPEG) -> MediaFormat.JPEG
                header.startsWith(PNG) -> MediaFormat.PNG
                header.startsWith(GIF87A) || header.startsWith(GIF89A) -> MediaFormat.GIF
                header.copyOfRange(0, 4).contentEquals(RIFF) && header.copyOfRange(8, 12).contentEquals(WEBP) -> MediaFormat.WEBP
                else -> null
            }
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    private fun readLittleEndian24(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16)

    private data class DecodedDimensions(val width: Int, val height: Int)

    private companion object {
        val JPEG = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val GIF87A = "GIF87a".toByteArray()
        val GIF89A = "GIF89a".toByteArray()
        val RIFF = "RIFF".toByteArray()
        val WEBP = "WEBP".toByteArray()
        val VP8X = "VP8X".toByteArray()
    }
}
