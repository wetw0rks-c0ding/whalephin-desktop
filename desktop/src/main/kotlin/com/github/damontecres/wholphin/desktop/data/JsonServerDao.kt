package com.github.damontecres.wholphin.desktop.data

import com.github.damontecres.wholphin.data.ServerDao
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinServerUsers
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val mutex = Mutex()

    private val _fileState = MutableStateFlow(load())
    private val fileState: Flow<ServersFile> = _fileState

    private fun load(): ServersFile {
        if (!file.exists()) return ServersFile()
        return try {
            Json { ignoreUnknownKeys = true }.decodeFromString<ServersFile>(file.readText())
        } catch (ex: Exception) {
            Log.e(ex, "Error loading ${file.path}, starting fresh")
            ServersFile()
        }
    }

    private fun save(data: ServersFile) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Json { prettyPrint = true }.encodeToString(data))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
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

    override fun addOrUpdateUser(user: JellyfinUser): JellyfinUser {
        val existing = getUser(user.serverId, user.id)
        val updated =
            if (existing != null) {
                user.copy(rowId = existing.rowId)
            } else {
                val rowId = _fileState.value.users.maxOfOrNull { it.rowId }?.plus(1) ?: 1
                user.copy(rowId = rowId)
            }
        updateValue { data ->
            val users =
                if (data.users.any { it.id == updated.id && it.serverId == updated.serverId }) {
                    data.users.map { if (it.id == updated.id && it.serverId == updated.serverId) updated else it }
                } else {
                    data.users + updated
                }
            data.copy(users = users)
        }
        return updated
    }

    override fun getUser(
        serverId: UUID,
        userId: UUID,
    ): JellyfinUser? = _fileState.value.users.firstOrNull { it.serverId == serverId && it.id == userId }

    override fun getUserFlow(
        serverId: UUID,
        userId: UUID,
    ): Flow<JellyfinUser?> =
        fileState.map { data ->
            data.users.firstOrNull { it.serverId == serverId && it.id == userId }
        }

    override fun getServers(): List<JellyfinServerUsers> =
        _fileState.value.servers.map { server ->
            JellyfinServerUsers(
                server = server,
                users = _fileState.value.users.filter { it.serverId == server.id },
            )
        }

    override fun getServer(serverId: UUID): JellyfinServerUsers? {
        val server = _fileState.value.servers.firstOrNull { it.id == serverId } ?: return null
        return JellyfinServerUsers(
            server = server,
            users = _fileState.value.users.filter { it.serverId == server.id },
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
        _fileState.value = _fileState.value.let(transform)
        save(_fileState.value)
    }
}
