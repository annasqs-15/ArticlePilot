package com.articlepilot.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class ArticleId(val value: String)

@JvmInline
@Serializable
value class ImageAssetId(val value: String)

@Serializable
data class Article(
    val id: ArticleId? = null,
    val formatVersion: String,
    val metadata: ArticleMetadata,
    val cover: ImageAsset? = null,
    val sections: List<ArticleSection> = emptyList(),
    val revision: Int = 0,
    val createdAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
)

@Serializable
data class ArticleMetadata(
    val title: String,
    val excerpt: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class ArticleSection(
    val id: String,
    val heading: String? = null,
    val blocks: List<ArticleBlock> = emptyList(),
)

@Serializable
sealed interface ArticleBlock {
    val id: String
}

@Serializable
@SerialName("text")
data class TextBlock(
    override val id: String,
    val text: String,
) : ArticleBlock

@Serializable
@SerialName("image")
data class ImageBlock(
    override val id: String,
    val asset: ImageAsset,
) : ArticleBlock

@Serializable
data class ImageAsset(
    val id: ImageAssetId,
    val downloadUrl: String,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val credit: String? = null,
    val caption: String? = null,
    val localFileReference: String? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fileSizeBytes: Long? = null,
    val processingStatus: ImageProcessingStatus = ImageProcessingStatus.NOT_STARTED,
    val validationStatus: ImageValidationStatus = ImageValidationStatus.NOT_VALIDATED,
)

@Serializable
enum class ImageProcessingStatus {
    NOT_STARTED,
    DOWNLOADING,
    DOWNLOADED,
    PROCESSING,
    READY,
    FAILED,
}

@Serializable
enum class ImageValidationStatus {
    NOT_VALIDATED,
    VALID,
    INVALID,
}

@Serializable
data class DraftRevision(
    val article: Article,
    val revisionNumber: Int,
    val createdAtEpochMillis: Long,
)

@Serializable
data class PublishingSession(
    val id: String,
    val articleId: ArticleId,
    val state: PublishingState,
    val currentSectionIndex: Int? = null,
    val currentBlockId: String? = null,
    val updatedAtEpochMillis: Long,
)

@Serializable
enum class PublishingState {
    NOT_STARTED,
    IN_PROGRESS,
    PAUSED_FOR_MANUAL_TAKEOVER,
    FAILED,
    COMPLETED,
}
