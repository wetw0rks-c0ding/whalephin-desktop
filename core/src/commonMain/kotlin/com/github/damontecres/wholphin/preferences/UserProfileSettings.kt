package com.github.damontecres.wholphin.preferences

/**
 * Settings from the user's profile on the server that may be overridden within the app.
 * Only the constants needed by shared models live here; the preference UI is platform-specific.
 */
object UserProfileSettings {
    /**
     * Special value that means the preferred language should be taken from the user's profile on the server
     */
    const val USE_USER_PROFILE = ""

    /**
     * Special value that means the user has no preferred language
     */
    const val PREFER_ANY_LANGUAGE = "_any-language"
}

enum class SubtitleModePreference {
    USE_USER_PROFILE,
    DEFAULT,
    SMART,
    ONLY_FORCED,
    ALWAYS,
    NONE,
}
