package com.articlepilot.media.storage

import com.articlepilot.core.model.ImageAsset
import com.articlepilot.core.model.ImageAssetId
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MediaStorageTest {
    @Test
    fun `temporary files are unique and promotion moves file into ready lifecycle`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-storage-test")
        val storage = FileSystemMediaStorage(root)
        val asset = imageAsset("asset-1")

        val first = storage.createTemporary(asset).getOrThrow()
        val second = storage.createTemporary(asset).getOrThrow()
        assertNotEquals(first.path, second.path)
        Files.writeString(java.nio.file.Path.of(first.path), "payload")

        val promoted = storage.promote(first, asset).getOrThrow()
        assertFalse(promoted.file.isTemporary)
        assertTrue(promoted.file.path.contains("ready"))
        assertTrue(storage.exists(promoted.file))
        assertFalse(storage.exists(first))
        assertEquals(7L, promoted.file.sizeBytes)
    }

    @Test
    fun `file facts include size and sha256`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-storage-facts")
        val storage = FileSystemMediaStorage(root)
        val file = storage.createTemporary(imageAsset("facts")).getOrThrow()
        Files.writeString(java.nio.file.Path.of(file.path), "abc")

        val facts = file.withFileFacts()
        assertEquals(3L, facts.sizeBytes)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", facts.checksumSha256)
    }

    @Test
    fun `delete reports missing file instead of silently succeeding`() = runBlocking {
        val root = Files.createTempDirectory("articlepilot-storage-delete")
        val storage = FileSystemMediaStorage(root)
        val file = storage.createTemporary(imageAsset("delete")).getOrThrow()
        assertTrue(storage.delete(file) is StorageResult.Success)
        val secondDelete = storage.delete(file)
        assertTrue(secondDelete is StorageResult.Failure)
        assertEquals(StorageFailureReason.NOT_FOUND, (secondDelete as StorageResult.Failure).reason)
    }

    private fun imageAsset(id: String) = ImageAsset(
        id = ImageAssetId(id),
        downloadUrl = "https://example.test/$id.png",
    )
}
