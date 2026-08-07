package com.github.damontecres.wholphin.desktop.data

import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.util.Log
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON-file store for per-library saved sort/filter/view options, keyed by
 * (serverId, itemId). Desktop stand-in for the app's Room `LibraryDisplayInfoDao`.
 */
class LibraryDisplayInfoStore(
    private val file: File,
) {
    private val lock = ReentrantLock()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoreFile(val entries: List<LibraryDisplayInfo> = emptyList())

    fun get(
        serverId: String,
        itemId: String,
    ): LibraryDisplayInfo? =
        lock.withLock {
            load().entries.firstOrNull { it.serverId == serverId && it.itemId == itemId }
        }

    fun set(entry: LibraryDisplayInfo) {
        lock.withLock {
            val current = load()
            val entries =
                current.entries.filterNot { it.serverId == entry.serverId && it.itemId == entry.itemId } + entry
            save(StoreFile(entries))
        }
    }

    private fun load(): StoreFile {
        if (!file.exists()) return StoreFile()
        return try {
            json.decodeFromString<StoreFile>(file.readText())
        } catch (ex: Exception) {
            Log.e(ex, "Error loading ${file.path}; starting fresh")
            file.renameTo(File(file.parentFile, file.name + ".bak"))
            StoreFile()
        }
    }

    private fun save(data: StoreFile) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.createNewFile()
        setOwnerOnly(tmp)
        tmp.writeText(json.encodeToString(data))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        setOwnerOnly(file)
    }

    private fun setOwnerOnly(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        file.setExecutable(false, false)
    }
}
