package com.github.damontecres.wholphin.desktop.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.PreferenceStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preferenceStorage: PreferenceStorage = koinInject(),
    appScope: CoroutineScope = koinInject(),
) {
    // Persisted state emits asynchronously; replace defaults once loaded unless the
    // user has an edit still pending, in which case keep the local value.
    val storedPrefs by preferenceStorage.appPreferences.collectAsState(initial = AppPreferences())
    var appPrefs by remember { mutableStateOf(storedPrefs) }
    var selectedSection by remember { mutableIntStateOf(0) }

    // A queue of pending writes drained on the app-scoped scope, so debounced
    // persistence survives navigating away from this screen.
    val pendingWrites = remember {
        MutableSharedFlow<AppPreferences.() -> AppPreferences>(extraBufferCapacity = 64)
    }
    LaunchedEffect(appScope, pendingWrites) {
        pendingWrites.collectLatest { task ->
            delay(400)
            preferenceStorage.updateAppPreferences {
                with(copy()) { task() }
            }
        }
    }

    fun updatePrefs(task: AppPreferences.() -> AppPreferences) {
        appPrefs = task(appPrefs.copy())
        pendingWrites.tryEmit(task)
    }

    // Sync asynchronously-loaded persisted values back into local state,
    // replacing the initial default once the store has actually emitted.
    // Edits made here are written to the same store, so the echo is a no-op
    // (equal values) and never clobbers in-progress changes.
    LaunchedEffect(storedPrefs) {
        appPrefs = storedPrefs
    }

    val sections = listOf(
        buildPlaybackPrefs(appPrefs, ::updatePrefs),
        buildGeneralPrefs(appPrefs, ::updatePrefs),
        buildHomePrefs(appPrefs, ::updatePrefs),
        buildInterfacePrefs(appPrefs, ::updatePrefs),
        buildLiveTvPrefs(appPrefs, ::updatePrefs),
        buildMpvPrefs(appPrefs, ::updatePrefs),
        buildPhotoPrefs(appPrefs, ::updatePrefs),
    )
    val sectionLabels = listOf(
        "Playback", "General", "Home Page", "Interface", "Live TV", "MPV", "Photos",
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedSection) {
                sections.forEachIndexed { idx, _ ->
                    Tab(
                        selected = selectedSection == idx,
                        onClick = { selectedSection = idx },
                        text = { Text(sectionLabels[idx]) },
                    )
                }
            }
            if (selectedSection in sections.indices) {
                val section = sections[selectedSection]
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item(key = "header") {
                        Text(
                            section.titleRes,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    itemsIndexed(section.items) { i, item ->
                        PrefItemRow(item)
                        if (i < section.items.size - 1) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefItemRow(item: PrefItem, modifier: Modifier = Modifier) {
    when (item) {
        is PrefItem.Switch -> SwitchPref(item, modifier)
        is PrefItem.Slider -> SliderPref(item, modifier)
        is PrefItem.SingleChoice -> ChoicePref(item, modifier)
        is PrefItem.Click -> ClickPref(item, modifier)
    }
}

@Composable
private fun SwitchPref(item: PrefItem.Switch, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.titleRes, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = item.checked, enabled = item.enabled, onCheckedChange = item.onCheckedChange)
    }
}

@Composable
private fun SliderPref(item: PrefItem.Slider, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.titleRes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(item.valueText(item.value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = item.value.toFloat(),
            onValueChange = { item.onValueChange(it.toLong()) },
            valueRange = item.valueRange,
            steps = maxOf(0, (item.valueRange.endInclusive - item.valueRange.start).toInt() - 1),
            enabled = item.enabled,
        )
    }
}

@Composable
private fun ChoicePref(item: PrefItem.SingleChoice, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = modifier.fillMaxWidth().clickable(enabled = item.enabled) { expanded = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.titleRes, style = MaterialTheme.typography.bodyLarge)
                if (item.options.isNotEmpty() && item.selectedIndex in item.options.indices) {
                    Text(item.options[item.selectedIndex], style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            item.options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, fontWeight = if (index == item.selectedIndex) FontWeight.Bold else FontWeight.Normal) },
                    enabled = item.enabled,
                    onClick = { item.onSelected(index); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ClickPref(item: PrefItem.Click, modifier: Modifier = Modifier) {
    Text(
        item.titleRes,
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp).clickable(enabled = item.enabled) { item.onClick() },
        style = MaterialTheme.typography.bodyLarge,
    )
}