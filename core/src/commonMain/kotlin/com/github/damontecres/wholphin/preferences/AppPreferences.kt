package com.github.damontecres.wholphin.preferences

import kotlinx.serialization.Serializable

/**
 * Desktop port of the Android app's AppPreferences.
 * Uses JSON + DataStore instead of protobuf.
 * Mirrors the nested structure: playback, home page, interface, live TV, photo, MPV, etc.
 */
@Serializable
data class AppPreferences(
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val homePage: HomePagePreferences = HomePagePreferences(),
    val `interface`: InterfacePreferences = InterfacePreferences(),
    val liveTv: LiveTvPreferences = LiveTvPreferences(),
    val photo: PhotoPreferences = PhotoPreferences(),
    val mpv: MpvOptions = MpvOptions(),
    val advanced: AdvancedPreferences = AdvancedPreferences(),
    val experimental: ExperimentalPreferences = ExperimentalPreferences(),
    val updateUrl: String = "https://api.github.com/repos/damontecres/Wholphin/releases/latest",
    val autoCheckForUpdates: Boolean = true,
    val sendCrashReports: Boolean = true,
    val debugLogging: Boolean = false,
    val signInAutomatically: Boolean = true,
)

@Serializable
data class PlaybackPreferences(
    val skipForwardMs: Long = 30_000,
    val skipBackMs: Long = 10_000,
    val controllerTimeoutMs: Long = 5000,
    val seekBarSteps: Int = 16,
    val showDebugInfo: Boolean = false,
    val autoPlayNext: Boolean = true,
    val autoPlayNextDelaySeconds: Long = 15,
    val skipBackOnResumeSeconds: Long = 0,
    val maxBitrate: Long = 100_000_000,
    val skipIntros: SkipSegmentBehavior = SkipSegmentBehavior.ASK_TO_SKIP,
    val skipOutros: SkipSegmentBehavior = SkipSegmentBehavior.ASK_TO_SKIP,
    val skipCommercials: SkipSegmentBehavior = SkipSegmentBehavior.ASK_TO_SKIP,
    val skipPreviews: SkipSegmentBehavior = SkipSegmentBehavior.IGNORE,
    val skipRecaps: SkipSegmentBehavior = SkipSegmentBehavior.IGNORE,
    val passOutProtectionMs: Long = 2 * 60 * 60 * 1000,
    val showNextUpWhen: ShowNextUpWhen = ShowNextUpWhen.END_OF_PLAYBACK,
    val playerBackend: PlayerBackend = PlayerBackend.MPV,
    val refreshRateSwitching: Boolean = false,
    val resolutionSwitching: Boolean = false,
    val cinemaMode: Boolean = true,
    val dpadSeekMode: DpadSeekMode = DpadSeekMode.SEEKBAR_MINIMAL,
    val globalContentScale: PrefContentScale = PrefContentScale.FIT,
    val oneClickPause: Boolean = false,
    val externalPlayer: String = "",
    val overrides: PlaybackOverrides = PlaybackOverrides(),
)

@Serializable
data class PlaybackOverrides(
    val ac3Supported: Boolean = true,
    val downmixStereo: Boolean = false,
    val directPlayPgs: Boolean = true,
    val directPlayDolbyVisionEL: Boolean = false,
    val decodeAv1: Boolean = true,
    val assPlaybackMode: AssPlaybackMode = AssPlaybackMode.ASS_LIBASS,
    val mediaExtensionsEnabled: MediaExtensionStatus = MediaExtensionStatus.MES_FALLBACK,
)

@Serializable
data class HomePagePreferences(
    val maxItemsPerRow: Int = 25,
    val enableRewatchingNextUp: Boolean = false,
    val maxDaysNextUp: Int = -1,
    val clickToPlay: Boolean = false,
)

@Serializable
data class InterfacePreferences(
    val playThemeSongs: ThemeSongVolume = ThemeSongVolume.MEDIUM,
    val appThemeColors: AppThemeColors = AppThemeColors.PURPLE,
    val navDrawerSwitchOnFocus: Boolean = true,
    val showClock: Boolean = true,
    val backdropStyle: BackdropStyle = BackdropStyle.BACKDROP_DYNAMIC_COLOR,
    val showLogos: Boolean = true,
    val displayToggles: List<DisplayToggle> = emptyList(),
    val rememberSelectedTab: Boolean = false,
)

@Serializable
data class LiveTvPreferences(
    val showHeader: Boolean = true,
    val favoriteChannelsAtBeginning: Boolean = true,
    val sortByRecentlyWatched: Boolean = false,
    val colorCodePrograms: Boolean = true,
)

@Serializable
data class PhotoPreferences(
    val slideshowDuration: Long = 5000,
    val slideshowPlayVideos: Boolean = false,
)

@Serializable
data class MpvOptions(
    val enableHardwareDecoding: Boolean = true,
    val useGpuNext: Boolean = false,
)

@Serializable
data class AdvancedPreferences(
    val imageDiskCacheSizeBytes: Long = 200 * 1024 * 1024,
)

@Serializable
data class ExperimentalPreferences(
    val enabled: Boolean = false,
    val videoTunnelingEnabled: Boolean = false,
)

/** Enum mirrors from the Android app */
@Serializable
enum class SkipSegmentBehavior(val number: Int) {
    IGNORE(0),
    ASK_TO_SKIP(1),
    AUTO_SKIP(2),
    UNRECOGNIZED(-1);

    companion object {
        fun forNumber(n: Int): SkipSegmentBehavior = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class ShowNextUpWhen(val number: Int) {
    END_OF_PLAYBACK(0),
    END_OF_EPISODE(1),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): ShowNextUpWhen = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class PlayerBackend(val number: Int) {
    EXO_PLAYER(0),
    MPV(1),
    PREFER_MPV(2),
    EXTERNAL_PLAYER(3),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): PlayerBackend = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class DpadSeekMode(val number: Int) {
    SEEKBAR_MINIMAL(0),
    SEEKBAR_FULL(1),
    SHORT_SEEK(2),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): DpadSeekMode = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class PrefContentScale(val number: Int) {
    FIT(0),
    NONE(1),
    CROP(2),
    FILL(3),
    FILL_WIDTH(4),
    FILL_HEIGHT(5),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): PrefContentScale = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class ThemeSongVolume(val number: Int) {
    OFF(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): ThemeSongVolume = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class AppThemeColors(val number: Int) {
    PURPLE(0),
    BLUE(1),
    GREEN(2),
    RED(3),
    ORANGE(4),
    TEAL(5),
    PINK(6),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): AppThemeColors = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class BackdropStyle(val number: Int) {
    BACKDROP_DYNAMIC_COLOR(0),
    BACKDROP_SOLID_COLOR(1),
    BACKDROP_BLURRED(2),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): BackdropStyle = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class DisplayToggle(val number: Int) {
    NONE(0),
    HEADER(1),
    TITLE(2),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): DisplayToggle = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class AssPlaybackMode(val number: Int) {
    ASS_LIBASS(0),
    ASS_SKIA(1),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): AssPlaybackMode = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}

@Serializable
enum class MediaExtensionStatus(val number: Int) {
    MES_FALLBACK(0),
    MES_ENABLED(1),
    MES_DISABLED(2),
    UNRECOGNIZED(-1);
    companion object {
        fun forNumber(n: Int): MediaExtensionStatus = entries.firstOrNull { it.number == n } ?: UNRECOGNIZED
    }
}