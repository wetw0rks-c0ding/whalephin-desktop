package com.github.damontecres.wholphin.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.model.UUID

/**
 * Manages Jellyfin display preferences (custom prefs key-value store on the
 * server). Used for persisting per-user state like "removed from next up" markers.
 * Desktop port of the Android DisplayPreferencesService (no Context dependency).
 */
class DisplayPreferencesService(
    private val api: ApiClient,
) {
    private val mutex = Mutex()

    suspend fun getDisplayPreferences(
        userId: UUID,
        displayPreferencesId: String = DEFAULT_DISPLAY_PREF_ID,
        client: String = DEFAULT_CLIENT,
    ) = api.displayPreferencesApi
        .getDisplayPreferences(
            userId = userId,
            displayPreferencesId = displayPreferencesId,
            client = client,
        ).content

    suspend fun updateDisplayPreferences(
        userId: UUID,
        displayPreferencesId: String = DEFAULT_DISPLAY_PREF_ID,
        client: String = DEFAULT_CLIENT,
        block: MutableMap<String, String?>.() -> Unit,
    ) {
        mutex.withLock {
            val current = getDisplayPreferences(userId, DEFAULT_DISPLAY_PREF_ID)
            val customPrefs =
                current.customPrefs.toMutableMap().apply {
                    block.invoke(this)
                }
            api.displayPreferencesApi.updateDisplayPreferences(
                displayPreferencesId = displayPreferencesId,
                userId = userId,
                client = client,
                data = current.copy(customPrefs = customPrefs),
            )
        }
    }

    companion object {
        const val DEFAULT_DISPLAY_PREF_ID = "default"
        const val DEFAULT_CLIENT = "Wholphin Desktop"
    }
}