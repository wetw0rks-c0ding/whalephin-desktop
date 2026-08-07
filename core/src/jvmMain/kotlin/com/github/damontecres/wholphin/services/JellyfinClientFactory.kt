package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.util.CoroutineContextApiClientFactory
import com.github.damontecres.wholphin.util.WholphinDispatchers
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo

/**
 * Builds the [Jellyfin] instance and its shared [ApiClient].
 *
 * Desktop equivalent of the Android app's Hilt `AppModule` wiring; replaces
 * `androidDevice(context)` with a manually constructed [DeviceInfo].
 */
class JellyfinClientFactory(
    val clientInfo: ClientInfo,
    val deviceInfo: DeviceInfo,
) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
    private val okHttpFactory =
        CoroutineContextApiClientFactory(OkHttpFactory(okHttpClient), WholphinDispatchers.IO)

    val jellyfin: Jellyfin =
        createJellyfin {
            // `this@JellyfinClientFactory` — otherwise the DSL receiver's own (null)
            // `clientInfo`/`deviceInfo` shadow the constructor parameters.
            this.clientInfo = this@JellyfinClientFactory.clientInfo
            this.deviceInfo = this@JellyfinClientFactory.deviceInfo
            apiClientFactory = okHttpFactory
            socketConnectionFactory = okHttpFactory
            minimumServerVersion = Jellyfin.minimumVersion
        }

    val apiClient: ApiClient = jellyfin.createApi(clientInfo = clientInfo, deviceInfo = deviceInfo)

    /**
     * An [ApiClient] for a specific server URL, e.g. for connecting to a new server
     */
    fun createApi(baseUrl: String): ApiClient =
        jellyfin.createApi(baseUrl = baseUrl, clientInfo = clientInfo, deviceInfo = deviceInfo)
}
