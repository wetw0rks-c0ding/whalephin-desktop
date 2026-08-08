package com.github.damontecres.wholphin.desktop.data

import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.util.Log
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON-file persistence for resume positions, mirroring the app's `ItemPlaybackDao`
 * semantics (one row per item, newest write wins). Atomic save via tmp-file + rename.
 */
class ItemPlaybackStore(
    private val file: File,
) {
    private val lock = ReentrantLock()
    private val json = Json { ignoreUnknownKeys = true }

    private fun load(): Map<UUID, ItemPlayback> =
        lock.withLock {
            if (!file.exists()) return emptyMap()
            runCatching { json.decodeFromString<List<ItemPlayback>>(file.readText()) }
                .getOrElse { ex ->
                    Log.e(ex, "Corrupt item playback store, backing up to .bak")
                    val bak = File(file.path + ".bak")
                    bak.createNewFile()
                    file.copyTo(bak, overwrite = true)
                    setOwnerOnly(bak)
                    emptyList()
                }
                .associateBy { it.itemId }
        }

    fun get(itemId: UUID): ItemPlayback? = load()[itemId]

    fun save(itemPlayback: ItemPlayback) {
        lock.withLock {
            val updated = (load() + (itemPlayback.itemId to itemPlayback)).values.sortedBy { it.lastPlayed }
            atomicReplace(json.encodeToString(updated))
        }
    }

    fun remove(itemId: UUID) {
        lock.withLock {
            val updated = load() - itemId
            atomicReplace(json.encodeToString(updated.values))
        }
    }

    private fun atomicReplace(content: String) {
        val tmp = File(file.path + "." + System.currentTimeMillis() + ".tmp")
        tmp.createNewFile()
        tmp.writeText(content)
        setOwnerOnly(tmp)
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            // Atomic move not supported on this filesystem — non-atomic fallback preserves the temp
            // as a recoverable generation if we crash mid-write
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.delete()
        }
    }

    /** Restricts a file to owner read/write (0600) since it contains access tokens */
    private fun setOwnerOnly(file: File) {
        require(file.setReadable(false, false)) { "Failed to clear other-read on ${file.path}" }
        require(file.setReadable(true, true)) { "Failed to set owner-read on ${file.path}" }
        require(file.setWritable(false, false)) { "Failed to clear other-write on ${file.path}" }
        require(file.setWritable(true, true)) { "Failed to set owner-write on ${file.path}" }
        require(file.setExecutable(false, false)) { "Failed to clear execute on ${file.path}" }
    }
}
