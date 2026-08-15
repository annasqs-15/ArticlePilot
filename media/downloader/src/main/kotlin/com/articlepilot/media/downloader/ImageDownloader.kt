package com.articlepilot.media.downloader

import com.articlepilot.core.model.ImageAsset

interface ImageDownloader {
    suspend fun download(asset: ImageAsset, destination: DownloadDestination): DownloadResult
}

fun interface DownloadDestination {
    suspend fun create(asset: ImageAsset): LocalMediaFile
}

data class LocalMediaFile(
    val path: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

sealed interface DownloadResult {
    data class Success(val asset: ImageAsset, val file: LocalMediaFile) : DownloadResult
    data class Failure(
        val asset: ImageAsset,
        val reason: DownloadFailureReason,
        val message: String,
        val canRetry: Boolean,
    ) : DownloadResult
}

enum class DownloadFailureReason {
    INVALID_URL,
    NETWORK,
    HTTP_ERROR,
    DESTINATION_UNAVAILABLE,
    CONTENT_TOO_LARGE,
    UNKNOWN,
}
