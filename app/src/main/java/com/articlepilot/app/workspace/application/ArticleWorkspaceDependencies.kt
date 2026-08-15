package com.articlepilot.app.workspace.application

import android.content.Context
import com.articlepilot.core.parser.ArticleParser
import com.articlepilot.core.validator.ArticleValidator
import com.articlepilot.core.validator.GenericArticleValidationPolicy
import com.articlepilot.core.validator.ValidationPolicy
import com.articlepilot.media.downloader.JvmHttpTransport
import com.articlepilot.media.downloader.StreamingImageDownloader
import com.articlepilot.media.inspection.DefaultMediaInspector
import com.articlepilot.media.processor.MediaPipeline
import com.articlepilot.media.processor.MediaPipelineObserver
import com.articlepilot.media.storage.FileSystemMediaStorage
import com.articlepilot.media.storage.MediaStorage
import com.articlepilot.media.validator.DefaultImageValidator
import java.nio.file.Path

/** Creates an isolated pipeline instance for one asset operation. */
fun interface MediaPipelineFactory {
    fun create(observer: MediaPipelineObserver): MediaPipeline
}

/**
 * Concrete Android composition-root factory. The current storage adapter is JVM NIO
 * rooted inside app-private filesDir; it does not expose credentials or files outside
 * the application sandbox.
 */
class AndroidMediaPipelineFactory(
    context: Context,
) : MediaPipelineFactory {
    private val storage: MediaStorage = FileSystemMediaStorage(
        root = context.filesDir.toPath().resolve("articlepilot-media"),
    )
    private val inspector = DefaultMediaInspector()
    private val downloader = StreamingImageDownloader(
        transport = JvmHttpTransport(),
        storage = storage,
    )
    private val validator = DefaultImageValidator(inspector)

    override fun create(observer: MediaPipelineObserver): MediaPipeline = MediaPipeline(
        downloader = downloader,
        storage = storage,
        inspector = inspector,
        validator = validator,
        observer = observer,
    )
}

data class ArticleWorkspaceDependencies(
    val parser: ArticleParser,
    val validator: ArticleValidator,
    val mediaPipelineFactory: MediaPipelineFactory,
    val validationPolicy: ValidationPolicy = GenericArticleValidationPolicy,
)
