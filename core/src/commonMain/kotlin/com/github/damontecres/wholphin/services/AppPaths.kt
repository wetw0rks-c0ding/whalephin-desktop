package com.github.damontecres.wholphin.services

/**
 * Platform-specific filesystem locations for the app's config, data, and cache directories.
 *
 * Paths are returned as strings so the interface is usable from common code; each platform
 * implementation resolves them per its conventions (desktop: XDG Base Directory spec,
 * Android: context filesDir-based locations).
 */
interface AppPaths {
    /** Directory for configuration and preferences. */
    val configDir: String

    /** Directory for persistent application data (databases, caches that survive restarts). */
    val dataDir: String

    /** Directory for ephemeral caches. */
    val cacheDir: String
}
