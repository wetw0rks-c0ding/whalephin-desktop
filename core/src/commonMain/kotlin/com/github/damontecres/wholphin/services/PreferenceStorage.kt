package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.damontecres.wholphin.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow

/**
 * A key-value preferences store backed by [DataStore] (multiplatform).
 *
 * Replaces the Android app's Hilt-provided [androidx.datastore.preferences.core.Preferences]
 * DataStore instances; each platform supplies its own storage location.
 */
interface PreferenceStorage {
    val dataStore: DataStore<Preferences>

    /** Emits the current value for [key], or [default] while no value is set. */
    fun <T> get(key: Preferences.Key<T>, default: T): Flow<T>

    /** Sets [key] to [value], replacing any previous value. */
    suspend fun <T> put(key: Preferences.Key<T>, value: T)

    /** Removes [key] if present. */
    suspend fun remove(key: Preferences.Key<*>)

    /** Emits the full AppPreferences object. */
    val appPreferences: Flow<AppPreferences>

    /** Atomically updates the AppPreferences. */
    suspend fun updateAppPreferences(block: AppPreferences.() -> AppPreferences)
}
