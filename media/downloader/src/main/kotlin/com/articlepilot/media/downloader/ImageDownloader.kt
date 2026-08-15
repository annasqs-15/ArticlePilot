package com.articlepilot.media.downloader

import com.articlepilot.core.model.ImageAsset
import com.articlepilot.media.storage.LocalMediaFile
import com.articlepilot.media.storage.MediaStorage
import com.articlepilot.media.storage.withFileFacts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

interface ImageDownloader {
    suspend fun download(asset: ImageAsset, destination: DownloadDestination): DownloadResult
}

fun interface DownloadDestination {
    suspend fun create(asset: ImageAsset): LocalMediaFile
}

sealed interface DownloadResult {
    data class Success(
        val asset: ImageAsset,
        val file: LocalMediaFile,
        val finalUrl: String,
        val attempts: Int,
        val responseMimeType: String?,
    ) : DownloadResult

    data class Failure(
        val asset: ImageAsset,
        val reason: DownloadFailureReason,
        val message: String,
        val canRetry: Boolean,
        val attempts: Int,
    ) : DownloadResult
}

enum class DownloadFailureReason {
    INVALID_URL,
    UNSUPPORTED_SCHEME,
    NETWORK,
    TIMEOUT,
    HTTP_ERROR,
    TOO_MANY_REDIRECTS,
    DESTINATION_UNAVAILABLE,
    CONTENT_TOO_LARGE,
    CANCELLED,
    UNKNOWN,
}

data class HttpDownloadPolicy(
    val connectTimeoutMillis: Int = 15_000,
    val readTimeoutMillis: Int = 30_000,
    val maxRedirects: Int = 5,
    val maxDownloadBytes: Long = 10L * 1024L * 1024L,
    val maxAttempts: Int = 3,
    val backoffBaseMillis: Long = 250L,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
        require(maxRedirects >= 0) { "maxRedirects must not be negative" }
        require(maxDownloadBytes > 0) { "maxDownloadBytes must be positive" }
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(backoffBaseMillis >= 0) { "backoffBaseMillis must not be negative" }
    }
}

data class HttpResponse(
    val statusCode: Int,
    val finalUrl: String,
    val headers: Map<String, String>,
    val contentLength: Long?,
    val body: InputStream,
    val close: () -> Unit,
)

interface HttpTransport {
    suspend fun open(url: String, policy: HttpDownloadPolicy): HttpOpenResult
}

sealed interface HttpOpenResult {
    data class Success(val response: HttpResponse) : HttpOpenResult
    data class Failure(
        val reason: DownloadFailureReason,
        val message: String,
        val canRetry: Boolean,
    ) : HttpOpenResult
}

/**
 * JVM transport backed by HttpURLConnection. It follows redirects itself so the
 * redirect limit is explicit and the final URL can be recorded.
 */
class JvmHttpTransport : HttpTransport {
    override suspend fun open(url: String, policy: HttpDownloadPolicy): HttpOpenResult =
        withContext(Dispatchers.IO) {
            var current = url
            var redirects = 0
            while (true) {
                coroutineContext.ensureActive()
                val connection = try {
                    val parsed = URI(current)
                    if (parsed.scheme !in setOf("http", "https")) {
                        return@withContext HttpOpenResult.Failure(
                            DownloadFailureReason.UNSUPPORTED_SCHEME,
                            "Only HTTP(S) URLs are supported: $current",
                            canRetry = false,
                        )
                    }
                    URL(current).openConnection() as HttpURLConnection
                } catch (error: IllegalArgumentException) {
                    return@withContext HttpOpenResult.Failure(
                        DownloadFailureReason.INVALID_URL,
                        "Malformed URL: $current (${error.message ?: "invalid syntax"})",
                        canRetry = false,
                    )
                } catch (error: IOException) {
                    return@withContext HttpOpenResult.Failure(
                        DownloadFailureReason.NETWORK,
                        "Unable to open URL $current: ${error.message ?: error::class.simpleName}",
                        canRetry = error !is UnknownHostException,
                    )
                }

                connection.instanceFollowRedirects = false
                connection.connectTimeout = policy.connectTimeoutMillis
                connection.readTimeout = policy.readTimeoutMillis
                connection.useCaches = false
                connection.requestMethod = "GET"

                try {
                    val status = connection.responseCode
                    if (status in REDIRECT_STATUS_CODES) {
                        val location = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (location.isNullOrBlank()) {
                            return@withContext HttpOpenResult.Failure(
                                DownloadFailureReason.HTTP_ERROR,
                                "HTTP $status redirect did not include a Location header.",
                                canRetry = false,
                            )
                        }
                        if (redirects >= policy.maxRedirects) {
                            return@withContext HttpOpenResult.Failure(
                                DownloadFailureReason.TOO_MANY_REDIRECTS,
                                "Redirect limit of ${policy.maxRedirects} exceeded.",
                                canRetry = false,
                            )
                        }
                        current = URI(current).resolve(location).toString()
                        redirects += 1
                        continue
                    }

                    val body = if (status in 200..299) connection.inputStream else connection.errorStream
                    if (status !in 200..299 || body == null) {
                        connection.disconnect()
                        return@withContext HttpOpenResult.Failure(
                            DownloadFailureReason.HTTP_ERROR,
                            "HTTP $status returned for $current.",
                            canRetry = status == 408 || status == 429 || status in 500..599,
                        )
                    }
                    val headers = connection.headerFields
                        .filterKeys { it != null }
                        .mapKeys { it.key!! }
                        .mapValues { it.value.joinToString(",") }
                    return@withContext HttpOpenResult.Success(
                        HttpResponse(
                            statusCode = status,
                            finalUrl = connection.url.toString(),
                            headers = headers,
                            contentLength = connection.contentLengthLong.takeIf { it >= 0 },
                            body = body,
                            close = { body.close(); connection.disconnect() },
                        ),
                    )
                } catch (error: java.net.SocketTimeoutException) {
                    connection.disconnect()
                    return@withContext HttpOpenResult.Failure(
                        DownloadFailureReason.TIMEOUT,
                        "Timed out while opening $current: ${error.message ?: "timeout"}",
                        canRetry = true,
                    )
                } catch (error: IOException) {
                    connection.disconnect()
                    return@withContext HttpOpenResult.Failure(
                        DownloadFailureReason.NETWORK,
                        "Network failure for $current: ${error.message ?: error::class.simpleName}",
                        canRetry = true,
                    )
                }
            }
            error("HTTP transport loop terminated unexpectedly")
        }

    private companion object {
        val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}

class StreamingImageDownloader(
    private val transport: HttpTransport,
    private val storage: MediaStorage,
    private val policy: HttpDownloadPolicy = HttpDownloadPolicy(),
) : ImageDownloader {
    override suspend fun download(asset: ImageAsset, destination: DownloadDestination): DownloadResult {
        val rawUrl = asset.downloadUrl.trim()
        val parsedUrl = try {
            URI(rawUrl)
        } catch (error: IllegalArgumentException) {
            return failure(asset, DownloadFailureReason.INVALID_URL, "Malformed download URL: ${error.message ?: rawUrl}", false, 0)
        }
        if (parsedUrl.scheme !in setOf("http", "https") || parsedUrl.host.isNullOrBlank()) {
            return failure(asset, DownloadFailureReason.UNSUPPORTED_SCHEME, "Download URL must be an absolute HTTP(S) URL: $rawUrl", false, 0)
        }

        var attempt = 0
        while (attempt < policy.maxAttempts) {
            attempt += 1
            val temporary = try {
                destination.create(asset)
            } catch (error: Exception) {
                return failure(asset, DownloadFailureReason.DESTINATION_UNAVAILABLE, "Unable to create destination: ${error.message ?: error::class.simpleName}", false, attempt)
            }

            try {
                when (val opened = transport.open(rawUrl, policy)) {
                    is HttpOpenResult.Failure -> {
                        cleanup(temporary)
                        if (!opened.canRetry || attempt >= policy.maxAttempts) {
                            return failure(asset, opened.reason, opened.message, opened.canRetry && attempt < policy.maxAttempts, attempt)
                        }
                        delay(backoffMillis(attempt))
                    }

                    is HttpOpenResult.Success -> {
                        val response = opened.response
                        try {
                            if (response.contentLength != null && response.contentLength > policy.maxDownloadBytes) {
                                cleanup(temporary)
                                return failure(asset, DownloadFailureReason.CONTENT_TOO_LARGE, "Content-Length ${response.contentLength} exceeds maximum ${policy.maxDownloadBytes}.", false, attempt)
                            }
                            val written = streamToFile(response.body, temporary, policy.maxDownloadBytes)
                            val file = temporary.copy(
                                mimeType = response.headers["Content-Type"]?.substringBefore(';')?.trim()?.lowercase(),
                                sizeBytes = written,
                            ).withFileFacts()
                            return DownloadResult.Success(
                                asset = asset,
                                file = file,
                                finalUrl = response.finalUrl,
                                attempts = attempt,
                                responseMimeType = file.mimeType,
                            )
                        } finally {
                            response.close()
                        }
                    }
                }
            } catch (error: ContentTooLargeException) {
                cleanup(temporary)
                return failure(asset, DownloadFailureReason.CONTENT_TOO_LARGE, error.message ?: "Downloaded content exceeds maximum size.", false, attempt)
            } catch (error: CancellationException) {
                withContext(NonCancellable) { cleanup(temporary) }
                throw error
            } catch (error: java.net.SocketTimeoutException) {
                cleanup(temporary)
                if (attempt >= policy.maxAttempts) {
                    return failure(asset, DownloadFailureReason.TIMEOUT, error.message ?: "Download timed out.", false, attempt)
                }
                delay(backoffMillis(attempt))
            } catch (error: IOException) {
                cleanup(temporary)
                if (attempt >= policy.maxAttempts) {
                    return failure(asset, DownloadFailureReason.NETWORK, error.message ?: "Download failed.", false, attempt)
                }
                delay(backoffMillis(attempt))
            }
        }

        return failure(asset, DownloadFailureReason.UNKNOWN, "Download attempts exhausted.", false, attempt)
    }

    private suspend fun streamToFile(input: InputStream, file: LocalMediaFile, maxBytes: Long): Long = withContext(Dispatchers.IO) {
        val path = Path.of(file.path)
        var total = 0L
        Files.newOutputStream(path).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw ContentTooLargeException("Streamed content exceeds maximum $maxBytes bytes.")
                }
                output.write(buffer, 0, read)
            }
        }
        total
    }

    private suspend fun cleanup(file: LocalMediaFile) {
        runCatching { storage.delete(file) }
    }

    private fun backoffMillis(attempt: Int): Long = policy.backoffBaseMillis * attempt.toLong()

    private fun failure(
        asset: ImageAsset,
        reason: DownloadFailureReason,
        message: String,
        canRetry: Boolean,
        attempts: Int,
    ): DownloadResult.Failure = DownloadResult.Failure(asset, reason, message, canRetry, attempts)

    private class ContentTooLargeException(message: String) : IOException(message)
}

private const val DEFAULT_BUFFER_SIZE = 16 * 1024
