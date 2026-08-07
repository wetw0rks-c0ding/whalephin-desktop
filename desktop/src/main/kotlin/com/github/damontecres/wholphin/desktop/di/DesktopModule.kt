package com.github.damontecres.wholphin.desktop.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.damontecres.wholphin.services.AppPaths
import com.github.damontecres.wholphin.services.PreferenceStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.dsl.module
import java.io.File

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

val desktopModule = module {
    single<AppPaths> { XdgAppPaths() }
    single<PreferenceStorage> { DataStorePreferenceStorage(get()) }
}
