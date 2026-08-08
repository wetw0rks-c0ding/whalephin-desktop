package com.github.damontecres.wholphin.desktop.data

import com.github.damontecres.wholphin.data.ServerDao
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinServerUsers
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * File-based [ServerDao] for the desktop app, backed by a single JSON file
 * (`servers.json` in the XDG data directory).
 *
 * Mirrors the semantics of the Android app's Room DAO. Room-KMP / SQLDelight can replace
 * this implementation when playback-history queries are needed (M4) without touching callers.
 */
class JsonServerDao(
    private val file: File,
) : ServerDao {
    @Serializable
    private data class ServersFile(
        val servers: List<JellyfinServer> = emptyList(),
        val users: List<JellyfinUser> = emptyList(),
    )

    /** Guards read-modify-write transitions; the DAO interface is non-suspend so [synchronized] is used. */
    private val lock = Any()

    /** When true, persistence is blocked because the backing file could not be recovered. */
    @Volatile
    private var persistBlocked = false

    private val _fileState = MutableStateFlow(load())
    private val fileState: Flow<ServersFile> = _fileState

    private fun load(): ServersFile {
        if (!file.exists()) {
            // Try to recover from a backup left behind by a crash during save.
            // Only consider .bak files (tmp files are incomplete by definition); pick the newest.
            val parent = file.parentFile ?: return ServersFile()
            val bakFiles = parent.listFiles { f ->
                f.isFile && f.name.startsWith(file.name + ".") && f.name.endsWith(".bak")
            }?.sortedByDescending { it.lastModified() }.orEmpty()
            if (bakFiles.isNotEmpty()) {
                return try {
                    val bak = bakFiles.first()
                    val data = Json { ignoreUnknownKeys = true }.decodeFromString<ServersFile>(bak.readText())
                    Log.w("Recovered ${file.path} from backup ${bak.name}")
                    // Write recovery as the primary, using atomic move if possible
                    if (!writePrimaryAtomically(data)) {
                        Log.e("Failed to write recovered state to ${file.path}")
                    }
                    data
                } catch (_: Exception) {
                    ServersFile()
                }
            }
            return ServersFile()
        }
        return try {
            Json { ignoreUnknownKeys = true }.decodeFromString<ServersFile>(file.readText())
        } catch (ex: Exception) {
            // Preserve the corrupt file for inspection instead of silently overwriting it on save
            Log.e(ex, "Error loading ${file.path}; preserving file and starting fresh")
            val bak = File(file.parentFile, file.name + ".${System.currentTimeMillis()}.bak")
            if (!file.renameTo(bak)) {
                Log.e("Failed to rename ${file.path} to backup; blocking persistence until manually recovered")
                persistBlocked = true
            }
            ServersFile()
        }
    }

    /**
     * Attempts an atomic move of tmp → primary file. Falls back to rename.
     * Returns true if the primary file was successfully written.
     */
    private fun writePrimaryAtomically(data: ServersFile): Boolean {
        file.parentFile?.mkdirs()
        val tmpPath = File(file.parentFile, file.name + ".tmp").toPath()
        try {
            Files.writeString(tmpPath, Json { prettyPrint = true }.encodeToString(data))
            setOwnerOnly(tmpPath.toFile())
            Files.move(tmpPath, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return true
        } catch (_: Exception) {
            // Atomic move failed — fall back to direct write
            setOwnerOnly(file)
            file.writeText(Json { prettyPrint = true }.encodeToString(data))
            try { Files.deleteIfExists(tmpPath) } catch (_: IOException) {}
            return true
        }
    }

    private fun save(data: ServersFile): Boolean {
        if (persistBlocked) {
            Log.e("Persistence blocked: corrupt ${file.path} could not be backed up. " +
                "Manually move or delete the file to restore writes.")
            return false
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.createNewFile()
        setOwnerOnly(tmp)
        tmp.writeText(Json { prettyPrint = true }.encodeToString(data))
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // Atomic move not supported — try backup-then-copy
            if (file.exists()) {
                val bak = File(file.parentFile, file.name + ".bak")
                if (!file.renameTo(bak)) {
                    Log.e("Failed to back up ${file.path} before save; keeping tmp for recovery")
                    return false
                }
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Log.e("Failed to write new state to ${file.path}")
                return false
            }
        } finally {
            tmp.delete()
        }
        return true
    }

    /** Restricts a file to owner read/write (0600) since it contains access tokens */
    private fun setOwnerOnly(file: File) {
        require(file.setReadable(false, false)) { "Failed to clear other-read on ${file.path}" }
        require(file.setReadable(true, true)) { "Failed to set owner-read on ${file.path}" }
        require(file.setWritable(false, false)) { "Failed to clear other-write on ${file.path}" }
        require(file.setWritable(true, true)) { "Failed to set owner-write on ${file.path}" }
        require(file.setExecutable(false, false)) { "Failed to clear execute on ${file.path}" }
    }

    override fun addOrUpdateServer(server: JellyfinServer) {
        updateValue { data ->
            val servers =
                if (data.servers.any { it.id == server.id }) {
                    data.servers.map { if (it.id == server.id) server else it }
                } else {
                    data.servers + server
                }
            data.copy(servers = servers)
        }
    }

    override fun addOrUpdateUser(user: JellyfinUser): JellyfinUser =
        synchronized(lock) {
            val current = _fileState.value
            val existing = current.users.firstOrNull { it.serverId == user.serverId && it.id == user.id }
            val updated =
                if (existing != null) {
                    user.copy(rowId = existing.rowId)
                } else {
                    user.copy(rowId = current.users.maxOfOrNull { it.rowId }?.plus(1) ?: 1)
                }
            val users =
                if (existing != null) {
                    current.users.map { if (it.serverId == updated.serverId && it.id == updated.id) updated else it }
                } else {
                    current.users + updated
                }
            val newState = current.copy(users = users)
            if (save(newState)) {
                _fileState.value = newState
            } else {
                throw IOException("Failed to persist user ${updated.id}; save to ${file.path} failed")
            }
            updated
        }

    override fun getUser(
        serverId: UUID,
        userId: UUID,
    ): JellyfinUser? = synchronized(lock) { _fileState.value.users.firstOrNull { it.serverId == serverId && it.id == userId } }

    override fun getUserFlow(
        serverId: UUID,
        userId: UUID,
    ): Flow<JellyfinUser?> =
        fileState.map { data ->
            data.users.firstOrNull { it.serverId == serverId && it.id == userId }
        }

    override fun getServers(): List<JellyfinServerUsers> =
        synchronized(lock) {
            val data = _fileState.value
            data.servers.map { server ->
                JellyfinServerUsers(
                    server = server,
                    users = data.users.filter { it.serverId == server.id },
                )
            }
        }

    override fun getServer(serverId: UUID): JellyfinServerUsers? =
        synchronized(lock) {
            val data = _fileState.value
            val server = data.servers.firstOrNull { it.id == serverId } ?: return null
            JellyfinServerUsers(
                server = server,
                users = data.users.filter { it.serverId == server.id },
            )
        }

    override fun deleteServer(serverId: UUID) {
        updateValue { data ->
            data.copy(
                servers = data.servers.filterNot { it.id == serverId },
                users = data.users.filterNot { it.serverId == serverId },
            )
        }
    }

    override fun deleteUser(
        serverId: UUID,
        userId: UUID,
    ) {
        updateValue { data ->
            data.copy(users = data.users.filterNot { it.serverId == serverId && it.id == userId })
        }
    }

    private fun updateValue(transform: (ServersFile) -> ServersFile) {
        synchronized(lock) {
            val newState = _fileState.value.let(transform)
            if (save(newState)) {
                _fileState.value = newState
            } else {
                throw IOException("Failed to persist state to ${file.path}; in-memory state matches last successful save")
            }
        }
    }
}