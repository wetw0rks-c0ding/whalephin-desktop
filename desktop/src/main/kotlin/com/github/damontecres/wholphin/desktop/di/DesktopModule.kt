package com.github.damontecres.wholphin.desktop.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.damontecres.wholphin.data.ServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.desktop.data.JsonServerDao
import com.github.damontecres.wholphin.desktop.data.LibraryDisplayInfoStore
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
import kotlinx.coroutines.flow.map
import org.koin.dsl.module
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.io.File
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
 * [PreferenceStorage] backed by a preferences DataStore file in the XDG data directory.
 */
class DataStorePreferenceStorage(
    appPaths: AppPaths,
) : PreferenceStorage {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(appPaths.dataDir, "preferences.preferences_pb") },
        )

    override fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data.map { it[key] ?: default }

    override suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    override suspend fun remove(key: Preferences.Key<*>) {
        dataStore.edit { it.remove(key) }
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
    single<PreferenceStorage> { DataStorePreferenceStorage(get()) }
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
}
