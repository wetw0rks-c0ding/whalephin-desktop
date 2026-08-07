package com.github.damontecres.wholphin.desktop.ui.settings

import com.github.damontecres.wholphin.preferences.*

/**
 * Represents a single preference setting with its label and how to edit it.
 */
sealed interface PrefItem {
    val titleRes: String
    val enabled: Boolean

    data class Switch(
        override val titleRes: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        override val enabled: Boolean = true,
    ) : PrefItem

    data class Slider(
        override val titleRes: String,
        val value: Long,
        val valueRange: ClosedFloatingPointRange<Float>,
        val valueText: (Long) -> String,
        val onValueChange: (Long) -> Unit,
        override val enabled: Boolean = true,
    ) : PrefItem

    data class SingleChoice(
        override val titleRes: String,
        val options: List<String>,
        val selectedIndex: Int,
        val onSelected: (Int) -> Unit,
        val summary: String? = null,
        override val enabled: Boolean = true,
    ) : PrefItem

    data class Click(
        override val titleRes: String,
        val onClick: () -> Unit,
        override val enabled: Boolean = true,
    ) : PrefItem
}

/** A named group of preferences rendered under one header row. */
data class PrefSection(
    val titleRes: String,
    val items: List<PrefItem>,
)

fun buildPlaybackPrefs(prefs: AppPreferences, update: (AppPreferences.() -> AppPreferences) -> Unit): PrefSection {
    val p = prefs.playback
    return PrefSection(
        "Playback",
        listOf(
            PrefItem.Switch("Play automatically", p.autoPlayNext, onCheckedChange = { v -> update { copy(playback = playback.copy(autoPlayNext = v)) } }),
            PrefItem.Switch("Cinema mode", p.cinemaMode, onCheckedChange = { v -> update { copy(playback = playback.copy(cinemaMode = v)) } }),
            PrefItem.Switch("Refresh rate switching", p.refreshRateSwitching, onCheckedChange = { v -> update { copy(playback = playback.copy(refreshRateSwitching = v)) } }),
            PrefItem.Slider("Skip forward (s)", p.skipForwardMs / 1000, 10f..60f, { "$it s" }, onValueChange = { v -> update { copy(playback = playback.copy(skipForwardMs = v * 1000)) } }),
            PrefItem.Slider("Skip back (s)", p.skipBackMs / 1000, 5f..30f, { "$it s" }, onValueChange = { v -> update { copy(playback = playback.copy(skipBackMs = v * 1000)) } }),
            PrefItem.Slider("Controller timeout (s)", p.controllerTimeoutMs / 1000, 5f..60f, { "$it s" }, onValueChange = { v -> update { copy(playback = playback.copy(controllerTimeoutMs = v * 1000)) } }),
            choice(
                "Player backend",
                PlayerBackend.entries.filterNot { it == PlayerBackend.UNRECOGNIZED }.map { it.name },
                p.playerBackend.name,
            ) { v -> update { copy(playback = playback.copy(playerBackend = PlayerBackend.valueOf(v))) } },
            choice(
                "Content scale",
                PrefContentScale.entries.filterNot { it == PrefContentScale.UNRECOGNIZED }.map { it.name },
                p.globalContentScale.name,
            ) { v -> update { copy(playback = playback.copy(globalContentScale = PrefContentScale.valueOf(v))) } },
        ),
    )
}

fun buildGeneralPrefs(prefs: AppPreferences, update: (AppPreferences.() -> AppPreferences) -> Unit): PrefSection =
    PrefSection(
        "General",
        listOf(
            PrefItem.Switch("Sign in automatically", prefs.signInAutomatically, onCheckedChange = { v -> update { copy(signInAutomatically = v) } }),
            PrefItem.Switch("Auto-check for updates", prefs.autoCheckForUpdates, onCheckedChange = { v -> update { copy(autoCheckForUpdates = v) } }),
            PrefItem.Switch("Send crash reports", prefs.sendCrashReports, onCheckedChange = { v -> update { copy(sendCrashReports = v) } }),
            PrefItem.Switch("Debug logging", prefs.debugLogging, onCheckedChange = { v -> update { copy(debugLogging = v) } }),
        ),
    )

fun buildHomePrefs(prefs: AppPreferences, update: (AppPreferences.() -> AppPreferences) -> Unit): PrefSection {
    val hp = prefs.homePage
    return PrefSection(
        "Home Page",
        listOf(
            PrefItem.Switch("Enable rewatching next up", hp.enableRewatchingNextUp, onCheckedChange = { v -> update { copy(homePage = homePage.copy(enableRewatchingNextUp = v)) } }),
            PrefItem.Slider("Max days next up", hp.maxDaysNextUp.toLong(), -1f..30f, { if (it < 0) "-1 (all)" else "$it days" }, onValueChange = { v -> update { copy(homePage = homePage.copy(maxDaysNextUp = v.toInt())) } }),
            PrefItem.Slider("Max items per row", hp.maxItemsPerRow.toLong(), 5f..50f, { "$it" }, onValueChange = { v -> update { copy(homePage = homePage.copy(maxItemsPerRow = v.toInt())) } }),
        ),
    )
}

fun buildInterfacePrefs(prefs: AppPreferences, update: (AppPreferences.() -> AppPreferences) -> Unit): PrefSection {
    val i = prefs.`interface`
    return PrefSection(
        "Interface",
        listOf(
            PrefItem.Switch("Show clock", i.showClock, onCheckedChange = { v -> update { copy(`interface` = `interface`.copy(showClock = v)) } }),
            PrefItem.Switch("Remember selected tab", i.rememberSelectedTab, onCheckedChange = { v -> update { copy(`interface` = `interface`.copy(rememberSelectedTab = v)) } }),
            choice(
                "Backdrop style",
                BackdropStyle.entries.filterNot { it == BackdropStyle.UNRECOGNIZED }.map { it.name },
                i.backdropStyle.name,
            ) { v -> update { copy(`interface` = `interface`.copy(backdropStyle = BackdropStyle.valueOf(v))) } },
            choice(
                "App theme color",
                AppThemeColors.entries.filterNot { it == AppThemeColors.UNRECOGNIZED }.map { it.name },
                i.appThemeColors.name,
            ) { v -> update { copy(`interface` = `interface`.copy(appThemeColors = AppThemeColors.valueOf(v))) } },
        ),
    )
}

fun buildLiveTvPrefs(prefs: AppPreferences, update: (AppPreferences.() -> AppPreferences) -> Unit): PrefSection {
    val lt = prefs.liveTv
    return PrefSection(
        "Live TV",
        listOf(
            PrefItem.Switch("Show header", lt.showHeader, onCheckedChange = { v -> update { copy(liveTv = liveTv.copy(showHeader = v)) } }),
            PrefItem.Switch("Color code programs", lt.colorCodePrograms, onCheckedChange = { v -> update { copy(liveTv = liveTv.copy(colorCodePrograms = v)) } }),
            PrefItem.Switch("Sort by recently watched", lt.sortByRecentlyWatched, onCheckedChange = { v -> update { copy(liveTv = liveTv.copy(sortByRecentlyWatched = v)) } }),
        ),
    )
}

fun buildMpvPrefs(prefs: AppPreferences, update: (AppPreferences.() -> AppPreferences) -> Unit): PrefSection {
    val mpv = prefs.mpv
    return PrefSection(
        "MPV",
        listOf(
            PrefItem.Switch("Enable hardware decoding", mpv.enableHardwareDecoding, onCheckedChange = { v -> update { copy(mpv = mpv.copy(enableHardwareDecoding = v)) } }),
            PrefItem.Switch("Use gpu-next", mpv.useGpuNext, onCheckedChange = { v -> update { copy(mpv = mpv.copy(useGpuNext = v)) } }),
        ),
    )
}

fun buildPhotoPrefs(prefs: AppPreferences, update: (AppPreferences.() -> AppPreferences) -> Unit): PrefSection {
    val ph = prefs.photo
    return PrefSection(
        "Photos",
        listOf(
            PrefItem.Switch("Slideshow play videos", ph.slideshowPlayVideos, onCheckedChange = { v -> update { copy(photo = photo.copy(slideshowPlayVideos = v)) } }),
            PrefItem.Slider("Slideshow duration (s)", ph.slideshowDuration / 1000, 2f..60f, { "$it s" }, onValueChange = { v -> update { copy(photo = photo.copy(slideshowDuration = v * 1000)) } }),
        ),
    )
}

private inline fun choice(
    title: String,
    options: List<String>,
    current: String,
    crossinline onSelected: (String) -> Unit,
): PrefItem.SingleChoice {
    val idx = options.indexOf(current).coerceAtLeast(0)
    return PrefItem.SingleChoice(
        titleRes = title,
        options = options,
        selectedIndex = idx,
        onSelected = { i -> onSelected(options[i]) },
    )
}