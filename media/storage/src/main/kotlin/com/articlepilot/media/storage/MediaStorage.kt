package com.articlepilot.media.storage

import com.articlepilot.core.model.ImageAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** A local file reference whose lifecycle is owned by the Media Core. */
data class LocalMediaFile(
    val path: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val checksumSha256: String? = null,
    val isTemporary: Boolean = true,
)

data class StoredMedia(
    val file: LocalMediaFile,
    val assetId: String,
)

sealed interface StorageResult {
    data class Success(val file: LocalMediaFile? = null) : StorageResult
    data class Failure(val reason: StorageFailureReason, val message: String) : StorageResult
}

enum class StorageFailureReason {
    ROOT_UNAVAILABLE,
    CREATE_FAILED,
    PROMOTE_FAILED,
    DELETE_FAILED,
    NOT_FOUND,
}

interface MediaStorage {
    suspend fun createTemporary(asset: ImageAsset): Result<LocalMediaFile>
    suspend fun promote(file: LocalMediaFile, asset: ImageAsset): Result<StoredMedia>
    suspend fun delete(file: LocalMediaFile): StorageResult
    suspend fun exists(file: LocalMediaFile): Boolean
}

/**
 * App-private filesystem storage. Remote filenames are never used; every temporary
 * file receives a generated name and every promoted file remains under the configured root.
 */
class FileSystemMediaStorage(
    private val root: Path,
) : MediaStorage {
    private val temporaryRoot: Path
        get() = root.resolve("temporary")

    private val readyRoot: Path
        get() = root.resolve("ready")

    override suspend fun createTemporary(asset: ImageAsset): Result<LocalMediaFile> = withContext(Dispatchers.IO) {
        runCatching {
            Files.createDirectories(temporaryRoot)
            val file = Files.createTempFile(temporaryRoot, "articlepilot-${safeAssetId(asset)}-", ".part")
            LocalMediaFile(path = file.toString(), isTemporary = true)
        }
    }

    override suspend fun promote(file: LocalMediaFile, asset: ImageAsset): Result<StoredMedia> = withContext(Dispatchers.IO) {
        runCatching {
            require(Files.exists(Path.of(file.path))) { "Temporary media file does not exist: ${file.path}" }
            Files.createDirectories(readyRoot)
            val target = readyRoot.resolve("${safeAssetId(asset)}-${UUID.randomUUID()}.media")
            val source = Path.of(file.path)
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source, target)
            }
            val promoted = file.copy(
                path = target.toString(),
                isTemporary = false,
                sizeBytes = Files.size(target),
            )
            StoredMedia(file = promoted, assetId = asset.id.value)
        }
    }

    override suspend fun delete(file: LocalMediaFile): StorageResult = withContext(Dispatchers.IO) {
        try {
            val path = Path.of(file.path)
            if (!Files.exists(path)) {
                StorageResult.Failure(StorageFailureReason.NOT_FOUND, "Media file does not exist: ${file.path}")
            } else {
                Files.delete(path)
                StorageResult.Success()
            }
        } catch (error: IOException) {
            StorageResult.Failure(
                StorageFailureReason.DELETE_FAILED,
                "Unable to delete media file ${file.path}: ${error.message ?: error::class.simpleName}",
            )
        }
    }

    override suspend fun exists(file: LocalMediaFile): Boolean = withContext(Dispatchers.IO) {
        Files.exists(Path.of(file.path))
    }

    private fun safeAssetId(asset: ImageAsset): String = asset.id.value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "asset" }
        .take(80)
}

suspend fun LocalMediaFile.withFileFacts(): LocalMediaFile = withContext(Dispatchers.IO) {
    val path = Path.of(this@withFileFacts.path)
    require(Files.exists(path)) { "Media file does not exist: ${path}" }
    copy(
        sizeBytes = Files.size(path),
        checksumSha256 = sha256(path),
    )
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
