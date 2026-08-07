package com.github.damontecres.wholphin.desktop.data

import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.util.Log
import java.io.File
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
                    file.copyTo(File(file.path + ".bak"), overwrite = true)
                    emptyList()
                }
                .associateBy { it.itemId }
        }

    fun get(itemId: UUID): ItemPlayback? = load()[itemId]

    fun save(itemPlayback: ItemPlayback) {
        lock.withLock {
            val updated = (load() + (itemPlayback.itemId to itemPlayback)).values.sortedBy { it.lastPlayed }
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(updated))
            setOwnerOnly(tmp)
            if (tmp.renameTo(file)) {
                setOwnerOnly(file)
            }
        }
    }

    fun remove(itemId: UUID) {
        lock.withLock {
            val updated = load() - itemId
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(updated.values))
            setOwnerOnly(tmp)
            if (tmp.renameTo(file)) {
                setOwnerOnly(file)
            }
        }
    }

    /** Restricts a file to owner read/write (0600) since it contains access tokens */
    private fun setOwnerOnly(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        file.setExecutable(false, false)
    }
}
