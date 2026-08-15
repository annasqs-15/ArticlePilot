package com.articlepilot.media.processor

import com.articlepilot.core.model.ImageAsset
import com.articlepilot.media.downloader.LocalMediaFile

interface ImageProcessor {
    suspend fun process(asset: ImageAsset, input: LocalMediaFile, policy: ProcessingPolicy): ProcessingResult
}

interface ProcessingPolicy {
    val id: String
    val version: String
}

sealed interface ProcessingResult {
    data class Ready(
        val asset: ImageAsset,
        val output: LocalMediaFile,
    ) : ProcessingResult

    data class Failed(
        val asset: ImageAsset,
        val message: String,
        val recoverable: Boolean,
    ) : ProcessingResult
}
