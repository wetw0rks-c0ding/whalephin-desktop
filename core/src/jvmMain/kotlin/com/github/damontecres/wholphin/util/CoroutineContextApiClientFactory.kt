package com.github.damontecres.wholphin.util

import kotlinx.coroutines.CoroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.ApiClientFactory
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.api.sockets.SocketConnection
import org.jellyfin.sdk.api.sockets.SocketConnectionFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import kotlin.coroutines.CoroutineContext

/**
 * Creates [CoroutineContextApiClient]s by wrapping the given [OkHttpFactory]
 */
class CoroutineContextApiClientFactory(
    private val factory: OkHttpFactory,
    private val coroutineContext: CoroutineContext = WholphinDispatchers.IO,
) : ApiClientFactory,
    SocketConnectionFactory {
    override fun create(
        baseUrl: String?,
        accessToken: String?,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
        httpClientOptions: HttpClientOptions,
        socketConnectionFactory: SocketConnectionFactory,
    ): ApiClient =
        CoroutineContextApiClient(
            factory.create(baseUrl, accessToken, clientInfo, deviceInfo, httpClientOptions, socketConnectionFactory),
            coroutineContext,
        )

    override fun create(
        clientOptions: HttpClientOptions,
        scope: CoroutineScope,
    ): SocketConnection = factory.create(clientOptions, scope)
}