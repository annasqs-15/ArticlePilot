package com.articlepilot.media.downloader

import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageAssetId
import com.articlepilot.media.storage.FileSystemMediaStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImageDownloaderTest {
    @Test
    fun `invalid scheme is rejected before transport`() = runBlocking {
        val transport = RecordingTransport(emptyList())
        val storage = FileSystemMediaStorage(Files.createTempDirectory("articlepilot-download-url"))
        val result = downloader(transport, storage).download(asset("file:///tmp/image.png"), destination(storage))

        val failure = assertIs<DownloadResult.Failure>(result)
        assertEquals(DownloadFailureReason.UNSUPPORTED_SCHEME, failure.reason)
        assertEquals(0, transport.calls)
    }

    @Test
    fun `content length limit rejects before writing body and cleans temporary`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-download-length")
        val storage = FileSystemMediaStorage(root)
        val transport = RecordingTransport(listOf(response(status = 200, bytes = "1234".toByteArray(), contentLength = 4)))
        val result = downloader(transport, storage, maxBytes = 3).download(asset("https://example.test/large"), destination(storage))

        val failure = assertIs<DownloadResult.Failure>(result)
        assertEquals(DownloadFailureReason.CONTENT_TOO_LARGE, failure.reason)
        assertEquals(0L, temporaryFileCount(root))
    }

    @Test
    fun `streamed limit catches absent or dishonest content length`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-download-stream")
        val storage = FileSystemMediaStorage(root)
        val transport = RecordingTransport(listOf(response(status = 200, bytes = "1234".toByteArray(), contentLength = null)))
        val result = downloader(transport, storage, maxBytes = 3).download(asset("https://example.test/stream"), destination(storage))

        val failure = assertIs<DownloadResult.Failure>(result)
        assertEquals(DownloadFailureReason.CONTENT_TOO_LARGE, failure.reason)
        assertEquals(0L, temporaryFileCount(root))
    }

    @Test
    fun `retryable network failure retries and preserves successful bytes`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-download-retry")
        val storage = FileSystemMediaStorage(root)
        val transport = RecordingTransport(
            listOf(
                HttpOpenResult.Failure(DownloadFailureReason.NETWORK, "temporary", canRetry = true),
                response(status = 200, bytes = "ok".toByteArray(), contentLength = 2),
            ),
        )
        val result = downloader(storage = storage, transport = transport, maxAttempts = 2).download(asset("https://example.test/retry"), destination(storage))

        val success = assertIs<DownloadResult.Success>(result)
        assertEquals(2, success.attempts)
        assertEquals("ok", Files.readString(java.nio.file.Path.of(success.file.path)))
        assertEquals(2L, success.file.sizeBytes)
        assertEquals("2689367b205c16ce32ed4200942b8b8b1e262dfc70d9bc9fbc77c49699a4f1df", success.file.checksumSha256)
        assertTrue(Files.exists(java.nio.file.Path.of(success.file.path)))
    }

    @Test
    fun `non retryable HTTP failure stops immediately`() = runBlocking {
        val transport = RecordingTransport(listOf(HttpOpenResult.Failure(DownloadFailureReason.HTTP_ERROR, "HTTP 404", canRetry = false)))
        val storage = FileSystemMediaStorage(Files.createTempDirectory("articlepilot-download-http"))
        val result = downloader(transport, storage).download(asset("https://example.test/missing"), destination(storage))

        val failure = assertIs<DownloadResult.Failure>(result)
        assertEquals(DownloadFailureReason.HTTP_ERROR, failure.reason)
        assertEquals(1, failure.attempts)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `cancellation cleans temporary file and propagates cancellation`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-download-cancel")
        val storage = FileSystemMediaStorage(root)
        val transport = object : HttpTransport {
            override suspend fun open(url: String, policy: HttpDownloadPolicy): HttpOpenResult {
                delay(Long.MAX_VALUE)
                error("unreachable")
            }
        }
        var cancelled = false
        try {
            withTimeout(50) {
                downloader(transport, storage).download(asset("https://example.test/cancel"), destination(storage))
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(0L, temporaryFileCount(root))
    }

    private fun downloader(
        transport: HttpTransport,
        storage: FileSystemMediaStorage,
        maxBytes: Long = 10,
        maxAttempts: Int = 1,
    ) = StreamingImageDownloader(
        transport = transport,
        storage = storage,
        policy = HttpDownloadPolicy(maxDownloadBytes = maxBytes, maxAttempts = maxAttempts, backoffBaseMillis = 0),
    )

    private fun destination(storage: FileSystemMediaStorage) = DownloadDestination { asset ->
        storage.createTemporary(asset).getOrThrow()
    }

    private fun response(status: Int, bytes: ByteArray, contentLength: Long?): HttpOpenResult.Success =
        HttpOpenResult.Success(
            HttpResponse(
                statusCode = status,
                finalUrl = "https://example.test/final",
                headers = mapOf("Content-Type" to "image/png"),
                contentLength = contentLength,
                body = ByteArrayInputStream(bytes),
                close = {},
            ),
        )

    private fun asset(url: String) = ImageAsset(
        id = ImageAssetId("asset-${url.hashCode()}"),
        downloadUrl = url,
    )

    private fun temporaryFileCount(root: java.nio.file.Path): Long =
        root.resolve("temporary").let { directory ->
            if (!Files.exists(directory)) 0 else Files.list(directory).use { it.count() }
        }

    private class RecordingTransport(
        private val outcomes: List<HttpOpenResult>,
    ) : HttpTransport {
        var calls: Int = 0
            private set

        override suspend fun open(url: String, policy: HttpDownloadPolicy): HttpOpenResult {
            val outcome = outcomes.getOrNull(calls) ?: error("No scripted outcome for call $calls")
            calls += 1
            return outcome
        }
    }
}
