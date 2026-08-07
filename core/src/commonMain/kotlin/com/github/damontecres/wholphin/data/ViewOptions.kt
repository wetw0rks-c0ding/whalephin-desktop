package com.github.damontecres.wholphin.data

import com.github.damontecres.wholphin.data.model.AspectRatio
import com.github.damontecres.wholphin.data.model.PrefContentScale
import com.github.damontecres.wholphin.data.model.ViewOptionImageType
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.ImageType

/**
 * User-customizable view state for a collection grid. Desktop port of the app's model.
 */
@Serializable
data class ViewOptions(
    val columns: Int = 6,
    val spacing: Int = 16,
    val contentScale: PrefContentScale = PrefContentScale.FIT,
    val aspectRatio: AspectRatio = AspectRatio.TALL,
    val showDetails: Boolean = false,
    val showBackdrop: Boolean = false,
    val imageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
    val showTitles: Boolean = true,
    val type: ViewOptionsType = ViewOptionsType.GRID,
) {
    companion object {
        val EMPTY = ViewOptions()
    }
}

enum class ViewOptionsType {
    GRID,
    LIST,
    DENSE_LIST,
}
