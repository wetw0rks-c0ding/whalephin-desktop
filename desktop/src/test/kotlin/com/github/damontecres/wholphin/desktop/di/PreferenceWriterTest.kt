package com.github.damontecres.wholphin.desktop.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.PreferenceStorage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PreferenceWriterTest {
    @TempDir
    lateinit var tempDir: File

    private fun tempPaths() = object : com.github.damontecres.wholphin.services.AppPaths {
        private fun sub(name: String) = File(tempDir, name).apply { mkdirs() }
        override val configDir: String = sub("config").path
        override val dataDir: String = sub("data").path
        override val cacheDir: String = sub("cache").path
    }

    @Test
    fun `composes two edits within one debounce window`() = runBlocking {
        val paths = tempPaths()
        val store = AppPreferencesStore(paths)
        val writerScope = CoroutineScope(Dispatchers.Default)
        val writer = PreferenceWriter(
            object : PreferenceStorage {
                override val dataStore: DataStore<Preferences>
                    get() = error("n/a")
                override val appPreferences: Flow<AppPreferences> = store.data
                override suspend fun updateAppPreferences(block: AppPreferences.() -> AppPreferences) =
                    store.updateData(block)
                override fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
                    error("n/a")
                override suspend fun <T> put(key: Preferences.Key<T>, value: T) {}
                override suspend fun remove(key: Preferences.Key<*>) {}
            },
            writerScope,
        )

        // Queue two distinct-field edits before the debounce flush fires.
        writer.enqueue { copy(debugLogging = true) }
        writer.enqueue { copy(autoCheckForUpdates = false) }

        // Poll the persisted store until the flush lands.
        val persisted = withTimeout(10_000) {
            var result: AppPreferences = AppPreferences()
            while (true) {
                val current = store.data.first()
                if (current.debugLogging && !current.autoCheckForUpdates) {
                    result = current
                    break
                }
                kotlinx.coroutines.delay(50)
            }
            result
        }

        assertEquals(true, persisted.debugLogging, "first edit must survive")
        assertEquals(false, persisted.autoCheckForUpdates, "second edit must survive")
    }
}