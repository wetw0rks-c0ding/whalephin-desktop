package com.github.damontecres.wholphin.desktop.data

import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import java.io.File
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JsonServerDaoTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `persists servers and users across reloads`() {
        val file = File(tempDir, "servers.json")
        val dao = JsonServerDao(file)
        val server =
            JellyfinServer(
                id = UUID.randomUUID(),
                name = "Test Server",
                url = "http://localhost:8096",
                version = "10.10.0",
            )
        dao.addOrUpdateServer(server)
        val user =
            JellyfinUser(
                id = UUID.randomUUID(),
                name = "tester",
                serverId = server.id,
                accessToken = "tok123",
                requireLogin = true,
            )
        val saved = dao.addOrUpdateUser(user)
        assertTrue(saved.rowId != 0, "rowId should be assigned on first insert")

        // A fresh DAO over the same file must see everything (persistence)
        val reloaded = JsonServerDao(file)
        val servers = reloaded.getServers()
        assertEquals(1, servers.size)
        assertEquals("Test Server", servers.first().server.name)
        val fetched = reloaded.getUser(server.id, user.id)
        assertNotNull(fetched)
        assertEquals("tok123", fetched?.accessToken)
        assertEquals(true, fetched?.requireLogin)

        reloaded.deleteServer(server.id)
        assertEquals(0, reloaded.getServers().size)
    }

    @Test
    fun `updates an existing user instead of duplicating`() {
        val file = File(tempDir, "servers.json")
        val dao = JsonServerDao(file)
        val server =
            JellyfinServer(id = UUID.randomUUID(), name = "S", url = "http://localhost:8096", version = null)
        dao.addOrUpdateServer(server)
        val userId = UUID.randomUUID()
        val first =
            dao.addOrUpdateUser(JellyfinUser(id = userId, name = "a", serverId = server.id, accessToken = "t1"))
        val second =
            dao.addOrUpdateUser(JellyfinUser(rowId = first.rowId, id = userId, name = "b", serverId = server.id, accessToken = "t2"))
        assertEquals(first.rowId, second.rowId, "same user row should be updated, not duplicated")
        assertEquals(1, dao.getServers().first().users.size)
        assertEquals("t2", dao.getUser(server.id, userId)?.accessToken)
    }
}
