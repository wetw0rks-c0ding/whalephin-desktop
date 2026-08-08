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
        if (!file.exists()) return ServersFile()
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

    private fun save(data: ServersFile) {
        if (persistBlocked) {
            Log.e("Persistence blocked: corrupt ${file.path} could not be backed up. " +
                "Manually move or delete the file to restore writes.")
            return
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        // Restrict permissions before writing so tokens never sit in a world-readable file
        tmp.createNewFile()
        setOwnerOnly(tmp)
        tmp.writeText(Json { prettyPrint = true }.encodeToString(data))
        if (!tmp.renameTo(file)) {
            // Rename failed: keep a backup of the old file, then copy the tmp content over
            if (file.exists()) {
                file.renameTo(File(file.parentFile, file.name + ".bak"))
            }
            file.createNewFile()
            setOwnerOnly(file)
            file.writeText(tmp.readText())
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
            save(newState)
            _fileState.value = newState
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
            save(newState)
            _fileState.value = newState
        }
    }
}
