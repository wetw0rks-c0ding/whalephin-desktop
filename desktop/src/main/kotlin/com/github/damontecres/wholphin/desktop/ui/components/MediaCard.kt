package com.github.damontecres.wholphin.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.AspectRatio
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.PrefContentScale
import com.github.damontecres.wholphin.data.model.ViewOptionImageType
import com.github.damontecres.wholphin.data.ViewOptions
import com.github.damontecres.wholphin.services.ImageUrlService
import org.jellyfin.sdk.model.api.ImageType

/**
 * Provides the server-aware image URL builder to the whole app, mirroring the Android
 * app's `LocalImageUrlService` CompositionLocal.
 */
val LocalImageUrlService = staticCompositionLocalOf<ImageUrlService?> { null }

fun PrefContentScale.toContentScale(): ContentScale =
    when (this) {
        PrefContentScale.FIT -> ContentScale.Fit
        PrefContentScale.NONE -> ContentScale.None
        PrefContentScale.CROP -> ContentScale.Crop
        PrefContentScale.FILL -> ContentScale.FillWidth
        PrefContentScale.FILL_WIDTH -> ContentScale.FillWidth
        PrefContentScale.FILL_HEIGHT -> ContentScale.FillHeight
        PrefContentScale.UNRECOGNIZED -> ContentScale.Fit
    }

/**
 * Aspect ratio to use for a card: the item's own primary-image ratio wins (the app
 * shows episodes/season art at their native shape), else the row/grid default.
 */
fun effectiveAspectRatio(item: BaseItem, default: AspectRatio): Float = item.aspectRatio ?: default.ratio

/**
 * Poster card for one media item.
 */
@Composable
fun MediaCard(
    item: BaseItem?,
    heightDp: Int,
    aspectRatio: AspectRatio,
    contentScale: PrefContentScale,
    imageType: ImageType,
    showTitle: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val url =
        imageUrlService?.getItemImageUrl(
            item = item,
            imageType = imageType,
            fillWidth = null,
            fillHeight = heightDp,
        )
    val cardAspectRatio = item?.let { effectiveAspectRatio(it, aspectRatio) } ?: aspectRatio.ratio
    val shape = RoundedCornerShape(8.dp)
    Column(modifier = modifier.width((heightDp * cardAspectRatio).dp).clickable(enabled = onClick != null) { onClick?.invoke() }) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(cardAspectRatio)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = url,
                contentDescription = item?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale.toContentScale(),
            )
        }
        if (showTitle) {
            Text(
                text = item?.name ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * One titled row of poster cards (home page and search rows).
 */
@Composable
fun MediaRow(
    title: String?,
    items: List<BaseItem?>,
    viewOptions: HomeRowViewOptions,
    showViewMore: Boolean,
    onItemClick: (BaseItem) -> Unit,
    onViewMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (showViewMore && onViewMore != null) {
                    Text(
                        text = "View more",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onViewMore() }.padding(8.dp),
                    )
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(viewOptions.spacing.dp),
        ) {
            itemsIndexed(items, key = { index, item -> item?.gridId ?: "placeholder-$index" }) { _, item ->
                MediaCard(
                    item = item,
                    heightDp = viewOptions.heightDp,
                    aspectRatio = viewOptions.aspectRatio,
                    contentScale = viewOptions.contentScale,
                    imageType = viewOptions.imageType.imageType,
                    showTitle = viewOptions.showTitles,
                    onClick = item?.let { { onItemClick(it) } },
                )
            }
        }
    }
}

/**
 * Grid of poster cards for a library / search results / etc.
 */
@Composable
fun MediaGrid(
    items: List<BaseItem?>,
    viewOptions: ViewOptions,
    onItemClick: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = viewOptions.columns,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(viewOptions.spacing.dp),
        verticalArrangement = Arrangement.spacedBy(viewOptions.spacing.dp),
    ) {
        itemsIndexed(items, key = { index, item -> item?.gridId ?: "placeholder-$index" }) { _, item ->
            MediaCard(
                item = item,
                heightDp = 172,
                aspectRatio = viewOptions.aspectRatio,
                contentScale = viewOptions.contentScale,
                imageType = viewOptions.imageType.imageType,
                showTitle = viewOptions.showTitles,
                onClick = item?.let { { onItemClick(it) } },
            )
        }
    }
}
