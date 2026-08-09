# Whalephin Desktop

A native Linux desktop port of [Wholphin](https://github.com/damontecres/Wholphin) — an open-source Jellyfin client — built with **Compose Multiplatform** and **MPV** (libmpv).

## Architecture

```
whalephin/
├── app/             # Upstream Android app — unchanged, keeps building
├── core/            # JVM library — Android-free shared logic (models, services, API)
├── desktop/         # Compose Desktop app (JVM)
└── gradle/          # Shared version catalog + wrapper
```

The `core` module extracts all portable Kotlin from the Android app (models, repositories, services, filters) into a plain JVM library. The `desktop` module reimplements the UI in JetBrains Compose Material3. The original Android `app` module is preserved intact and shares the `core` library — no code duplication, no drift.

**Key technology swaps:**

| Android | Desktop |
|---------|---------|
| Hilt (DI) | Koin |
| Room (DB) | JSON file persistence |
| WorkManager | Coroutine-based scheduler |
| ExoPlayer / MPV (AAR) | libmpv via Unix socket IPC |
| androidx.tv.* | Compose Material3 |
| Navigation3 (Android) | State-based navigation |
| DataStore | File-backed preferences |

## Features

### Browse & Discover
- Home screen with customizable rows (Continue Watching, Next Up, pinned libraries)
- Library grids with sort/filter
- Item detail pages — movie, series, season, episode, person
- Search with filter support
- Coil 3 image loading with crossfade

### Playback (MPV via libmpv)
- Direct-play negotiation via device profile
- Seek, pause, volume control
- Subtitle track selection + download
- Audio track selection
- Chapter markers
- Next-up auto-play
- Resume position tracking
- Playback effects (intro skip, etc.)
- Cinema mode / preroll support

### Settings
- Server setup with Quick Connect + user/password auth
- PIN-locked user switching (PBKDF2WithHmacSHA256)
- Multiple servers + user profiles
- Theme selection
- Home screen customization
- Subtitle styling
- Language preferences
- Screensaver preferences

### Media Features
- Favorites management
- Trailers (YouTube integration)
- Extras and special features
- Playlists
- Seerr integration (discover + request)
- Live TV guide (programguide)

### System
- Background task scheduling (suggestions refresh, next-up scan, date-played report)
- XDG base directory compliance (`~/.config/wholphin`, `~/.local/share/wholphin`)
- Secure file permissions (0600) on credential files
- Atomic file writes with backup recovery

## Build & Run

**Prerequisites:** JDK 17+ and libmpv (`mpv` package on most distros).

```bash
git clone https://github.com/wetw0rks-c0ding/whalephin-desktop
cd whalephin-desktop
./gradlew :desktop:run
```

The Android `app` module requires the Android SDK. Desktop-only builds skip it:

```bash
./gradlew :core:test :desktop:compileKotlin
```

## Progress

| Milestone | Status |
|-----------|--------|
| M0 — Clone & validate | Done |
| M1 — Skeleton (modules, Compose window, Koin) | Done |
| M2 — Server connect, auth, persistence | Done |
| M3 — Browse (home, libraries, detail, search) | Done |
| M4 — Playback (MPV, controls, tracks, next-up) | Done |
| M5 — Settings parity | Done |
| M6 — Media features (favorites, trailers, extras, playlists, Seerr) | Done |
| M7 — Background services (coroutine scheduler) | Done |
| M8 — Parity hardening, keyboard shortcuts, AppImage packaging | In progress |

## License

GPL-2.0 — same as the upstream Wholphin project.

## Acknowledgments

This is a fork of [damontecres/Wholphin](https://github.com/damontecres/Wholphin), an excellent open-source Jellyfin client for Android TV. Thanks to the Jellyfin team for creating and maintaining the media server, and to the Jellyfin Kotlin SDK developers.