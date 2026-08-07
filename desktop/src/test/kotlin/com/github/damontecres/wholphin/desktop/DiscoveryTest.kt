package com.github.damontecres.wholphin.desktop

import com.github.damontecres.wholphin.services.JellyfinClientFactory
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the app's real server-discovery path (the same code `addServer`
 * uses) against a public Jellyfin server to verify the connect flow works.
 */
class DiscoveryTest {
    @Test
    fun `discovers a real public server when given its full base URL`() = runBlocking {
        val factory =
            JellyfinClientFactory(
                ClientInfo("Wholphin Test", "0.0.0-test"),
                DeviceInfo(id = "wholphin-test", name = "Wholphin Test"),
            )
        val scores =
            factory.jellyfin.discovery
                .getRecommendedServers("https://demo.jellyfin.org/stable")
        scores.forEach { println("  ${it.address} score=${it.score} issues=${it.issues}") }
        assertTrue(
            scores.any { it.score != RecommendedServerInfoScore.BAD },
            "No usable server discovered from a valid Jellyfin URL",
        )
    }
}
