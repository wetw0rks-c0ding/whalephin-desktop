package com.github.damontecres.wholphin.desktop.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.StorageConnection
import androidx.datastore.preferences.core.Preferences
import com.github.damontecres.wholphin.data.ServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.desktop.data.JsonServerDao
import com.github.damontecres.wholphin.desktop.data.LibraryDisplayInfoStore
import com.github.damontecres.wholphin.data.playback.PlaybackEngine
import com.github.damontecres.wholphin.desktop.data.ItemPlaybackStore
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.desktop.preferences.AppPreferencesSerializer
import com.github.damontecres.wholphin.desktop.playback.MpvEngine
import com.github.damontecres.wholphin.desktop.services.HomeRowService
import com.github.damontecres.wholphin.desktop.services.HomeSettingsService
import com.github.damontecres.wholphin.desktop.services.NavigationManager
import com.github.damontecres.wholphin.desktop.services.SetupNavigationManager
import com.github.damontecres.wholphin.services.AppPaths
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.JellyfinClientFactory
import com.github.damontecres.wholphin.services.PreferenceStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.dsl.module
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.io.File
import java.nio.file.StandardCopyOption
import java.nio.file.Files
import java.util.UUID

/**
 * XDG Base Directory spec implementation of [AppPaths] for Linux/desktop.
 */
class XdgAppPaths(
    val appName: String = "wholphin",
) : AppPaths {
    private fun baseDir(envVar: String, fallbackSubdir: String): String {
        val home = System.getProperty("user.home")
        val base = System.getenv(envVar) ?: File(home, fallbackSubdir).path
        return File(base, appName).path
    }

    override val configDir: String = baseDir("XDG_CONFIG_HOME", ".config")
    override val dataDir: String = baseDir("XDG_DATA_HOME", ".local/share")
    override val cacheDir: String = baseDir("XDG_CACHE_HOME", ".cache")

    init {
        listOf(configDir, dataDir, cacheDir).forEach { File(it).mkdirs() }
    }
}

/**
 * Simple JSON-based preferences store using atomic file writes.
 * This avoids the complex DataStore API issues for desktop.
 */
class AppPreferencesStore(
    appPaths: AppPaths,
) {
    private val file = File(appPaths.dataDir, "app-preferences.json")
    private val serializer = AppPreferencesSerializer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val _data = MutableStateFlow<AppPreferences?>(null)
    val data: Flow<AppPreferences> = _data
        .map { it ?: serializer.defaultValue }
        .distinctUntilChanged()

    init {
        // Load synchronously in init
        if (file.exists()) {
            try {
                val content = file.readText()
                val prefs = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString<AppPreferences>(content)
                _data.value = prefs
            } catch (e: Exception) {
                file.delete()
                _data.value = serializer.defaultValue
            }
        } else {
            _data.value = serializer.defaultValue
        }
    }

    /**
     * Atomically computes the next preferences from the last persisted value,
     * persists it, and only then publishes it. A failed write leaves both the
     * file and the in-memory state unchanged.
     */
    suspend fun updateData(block: AppPreferences.() -> AppPreferences) {
        mutex.withLock {
            val current = _data.value ?: serializer.defaultValue
            val newPrefs = current.block()
            save(newPrefs)
            _data.value = newPrefs
        }
    }

    private suspend fun save(prefs: AppPreferences) {
        val tmpFile = File(file.parent, "${file.name}.tmp")
        try {
            val content = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .encodeToString(prefs)
            Files.write(tmpFile.toPath(), content.toByteArray())
            Files.move(
                tmpFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
    }
}

/**
 * [PreferenceStorage] backed by JSON file store for AppPreferences.
 */
class DataStorePreferenceStorage(
    appPaths: AppPaths,
) : PreferenceStorage {
    private val prefsStore = AppPreferencesStore(appPaths)

    override val dataStore: DataStore<Preferences>
        get() = error("Preferences DataStore not used by desktop JSON preferences")
    override val appPreferences: Flow<AppPreferences> = prefsStore.data

    override suspend fun updateAppPreferences(block: AppPreferences.() -> AppPreferences) {
        prefsStore.updateData(block)
    }

    override fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
        kotlinx.coroutines.flow.flowOf(default)

    override suspend fun <T> put(key: Preferences.Key<T>, value: T) = Unit

    override suspend fun remove(key: Preferences.Key<*>) = Unit
}

/**
 * Application-scoped writer that collects preference edits from the UI and
 * persists a single atomic snapshot after a debounce interval.
 *
 * Owns its collector on the app [CoroutineScope] so queued edits complete even
 * after the settings screen leaves composition. Edits arriving within the
 * debounce window are composed so changes to different fields all survive.
 * Persistence failures are retried with bounded backoff without stopping the
 * collector, so later edits are still processed.
 */
class PreferenceWriter(
    private val preferenceStorage: PreferenceStorage,
    scope: CoroutineScope,
) {
    @Volatile
    private var composedBatch: (AppPreferences) -> AppPreferences = { it }

    init {
        scope.launch {
            // Debounce loop: wait for a quiet window, then flush the accumulated
            // (composed) edits as one atomic write, retrying transient failures.
            while (true) {
                delay(400)
                val batch = composedBatch
                composedBatch = { it }
                if (batch === IDLE) continue
                persistWithRetry(batch)
            }
        }
    }

    private suspend fun persistWithRetry(batch: (AppPreferences) -> AppPreferences) {
        var attempt = 0
        while (true) {
            try {
                preferenceStorage.updateAppPreferences {
                    batch(copy())
                }
                return
            } catch (_: Exception) {
                attempt++
                if (attempt >= MAX_WRITE_ATTEMPTS) {
                    // Re-queue the batch so it isn't silently lost. Any edits
                    // that arrived during the retry cycle are already composed
                    // into the current composedBatch and will fire next cycle.
                    synchronized(this) {
                        composedBatch = compose(batch, composedBatch)
                    }
                    return
                }
                delay(RETRY_DELAY_MS * attempt)
            }
        }
    }

    /** Queues [task] to be applied to the preferences and persisted. */
    fun enqueue(task: AppPreferences.() -> AppPreferences) {
        // Compose so edits from the same debounce window to different fields
        // are all applied, not overwritten.
        synchronized(this) {
            composedBatch = compose(composedBatch, task)
        }
    }

    private companion object {
        val IDLE: (AppPreferences) -> AppPreferences = { it }
        // Chain the existing batch, then apply the new edit on its result.
        fun compose(
            existing: (AppPreferences) -> AppPreferences,
            next: AppPreferences.() -> AppPreferences,
        ): (AppPreferences) -> AppPreferences = { prefs -> next(existing(prefs)) }

        const val MAX_WRITE_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 250L
    }
}

/**
 * A [DeviceInfo] identifying this app instance to Jellyfin servers.
 * Desktop equivalent of `androidDevice(context)`. The device id is persisted so the
 * server sees a stable device identity across app restarts.
 */
private fun desktopDeviceInfo(appPaths: AppPaths): DeviceInfo =
    DeviceInfo(
        id = deviceId(appPaths),
        name = "Wholphin Desktop",
    )

private fun deviceId(appPaths: AppPaths): String {
    val file = File(appPaths.dataDir, "device-id")
    file.parentFile?.mkdirs()
    if (file.exists()) {
        return file.readText().trim()
    }
    val id = UUID.randomUUID().toString()
    file.writeText(id)
    return id
}

val desktopModule = module {
    single<AppPaths> { XdgAppPaths() }
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single<PreferenceStorage> { DataStorePreferenceStorage(get()) }
    single { PreferenceWriter(preferenceStorage = get(), scope = get()) }
    single {
        JellyfinClientFactory(
            clientInfo = ClientInfo(name = "Wholphin Desktop", version = "0.0.0-dev"),
            deviceInfo = desktopDeviceInfo(get()),
        )
    }
    single<Jellyfin> { get<JellyfinClientFactory>().jellyfin }
    single { get<JellyfinClientFactory>().apiClient }
    single<ServerDao> { JsonServerDao(File(get<AppPaths>().dataDir, "servers.json")) }
    single {
        ServerRepository(
            apiClient = get(),
            serverDao = get(),
            preferenceStorage = get(),
        )
    }
    single { SetupNavigationManager() }
    single { NavigationManager() }
    single { LibraryDisplayInfoStore(File(get<AppPaths>().dataDir, "library-display-info.json")) }
    single { ImageUrlService(api = get()) }
    single { HomeSettingsService(api = get()) }
    single { HomeRowService(serverRepository = get(), homeSettingsService = get()) }
    single { MpvEngine(engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) }
    single { ItemPlaybackStore(File(get<AppPaths>().dataDir, "item-playback.json")) }
}
