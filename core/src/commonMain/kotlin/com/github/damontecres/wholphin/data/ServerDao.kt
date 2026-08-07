package com.github.damontecres.wholphin.data

import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinServerUsers
import com.github.damontecres.wholphin.data.model.JellyfinUser
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Persistence for servers and users.
 *
 * Mirrors the Android app's Room `JellyfinServerDao` so the storage backend can be swapped
 * (desktop: JSON-file implementation; Android: Room; M4 may migrate to Room-KMP for
 * playback-history queries).
 */
interface ServerDao {
    fun addOrUpdateServer(server: JellyfinServer)

    fun addOrUpdateUser(user: JellyfinUser): JellyfinUser

    fun getUser(
        serverId: UUID,
        userId: UUID,
    ): JellyfinUser?

    fun getUserFlow(
        serverId: UUID,
        userId: UUID,
    ): Flow<JellyfinUser?>

    fun getServers(): List<JellyfinServerUsers>

    fun getServer(serverId: UUID): JellyfinServerUsers?

    fun deleteServer(serverId: UUID)

    fun deleteUser(
        serverId: UUID,
        userId: UUID,
    )
}
