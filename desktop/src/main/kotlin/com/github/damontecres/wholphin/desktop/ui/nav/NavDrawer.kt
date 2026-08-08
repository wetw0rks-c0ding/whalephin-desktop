package com.github.damontecres.wholphin.desktop.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.desktop.services.Library

/**
 * Left-hand navigation rail with the top-level pages and the user's libraries.
 * Desktop equivalent of the app's modal [NavDrawer], kept permanently visible.
 */
@Composable
fun NavDrawer(
    libraries: List<Library>,
    selectedIndex: Int,
    user: String,
    server: String,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: (Int) -> Unit,
    onSettings: () -> Unit,
    onFavorites: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        DrawerItem(Icons.Filled.Home, "Home", 0, selectedIndex, onHome)
        DrawerItem(Icons.Filled.Search, "Search", 1, selectedIndex, onSearch)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        DrawerItem(Icons.Filled.Star, "Favorites", -3, selectedIndex, onFavorites)
        DrawerItem(Icons.Filled.Settings, "Settings", -2, selectedIndex, onSettings)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            "Libraries",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            libraries.forEachIndexed { index, library ->
                DrawerItem(
                    icon = Icons.Filled.Movie,
                    title = library.name,
                    index = index + 2,
                    selectedIndex = selectedIndex,
                    onClick = { onLibrary(index) },
                )
            }
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(user, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(server, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            DrawerItem(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", -1, selectedIndex, onSignOut)
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    title: String,
    index: Int,
    selectedIndex: Int,
    onClick: () -> Unit,
) {
    val selected = index == selectedIndex
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .semantics { this.selected = selected }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
