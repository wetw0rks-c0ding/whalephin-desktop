package com.github.damontecres.wholphin.desktop.di

import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.AppPaths
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

private class TempAppPaths(
    private val tempDir: File,
) : AppPaths {
    private fun sub(name: String) = File(tempDir, name).apply { mkdirs() }
    override val configDir: String = sub("config").path
    override val dataDir: String = sub("data").path
    override val cacheDir: String = sub("cache").path
}

class AppPreferencesStoreTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `persists successive updates across store reconstruction`() = runBlocking {
        val paths = TempAppPaths(tempDir)
        val store = AppPreferencesStore(paths)
        store.updateData { copy(autoCheckForUpdates = false) }
        store.updateData {
            copy(debugLogging = true)
        }

        // A fresh store over the same directory must load the latest persisted value
        val reloaded = AppPreferencesStore(paths)
        val loaded: AppPreferences = reloaded.data.first()
        assertEquals(false, loaded.autoCheckForUpdates, "second write autoCheckForUpdates should persist")
        assertEquals(true, loaded.debugLogging, "debugLogging write should persist")
    }

    @Test
    fun `data flow emits default before any write`() = runBlocking {
        val store = AppPreferencesStore(TempAppPaths(tempDir))
        val value = store.data.first()
        assertEquals(AppPreferences(), value)
    }
}