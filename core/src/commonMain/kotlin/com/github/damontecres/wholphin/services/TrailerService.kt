package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.Trailer
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi

/**
 * Gets trailers for media. Desktop port of the Android app's TrailerService
 * without Android Context dependencies.
 */
class TrailerService(
    private val api: ApiClient,
) {
    fun getRemoteTrailers(item: BaseItem): List<Trailer> =
        item.data.remoteTrailers
            ?.mapNotNull { t ->
                t.url?.let { url ->
                    val name = t.name ?: "Trailer"
                    val subtitle =
                        when {
                            url.contains("youtube.com") || url.contains("youtu.be") -> "YouTube"
                            else -> null
                        }
                    RemoteTrailer(name, url, subtitle)
                }
            }.orEmpty()
            .sortedWith(
                compareBy(
                    {
                        when {
                            it.name.contains("Official", true) -> 0
                            it.name.contains("Teaser", true) -> 2
                            it.name.contains("Trailer", true) -> 1
                            else -> 3
                        }
                    },
                    { it.name },
                ),
            )

    suspend fun getLocalTrailers(item: BaseItem): List<Trailer> {
        val localTrailerCount = item.data.localTrailerCount ?: return emptyList()
        return if (localTrailerCount > 0) {
            api.userLibraryApi.getLocalTrailers(item.id).content.map {
                LocalTrailer(BaseItem(it))
            }
        } else {
            emptyList()
        }
    }
}
