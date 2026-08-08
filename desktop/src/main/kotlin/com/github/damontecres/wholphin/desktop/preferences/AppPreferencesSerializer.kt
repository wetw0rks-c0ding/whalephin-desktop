package com.github.damontecres.wholphin.desktop.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.github.damontecres.wholphin.preferences.AppPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * JSON serializer for [AppPreferences] backed by DataStore preferences.
 * Desktop equivalent of the Android app's protobuf [AppPreferencesSerializer].
 */
class AppPreferencesSerializer : Serializer<AppPreferences> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: AppPreferences = AppPreferences()

    override suspend fun readFrom(input: InputStream): AppPreferences {
        val reader = InputStreamReader(input, StandardCharsets.UTF_8)
        val content = reader.readText()
        return try {
            json.decodeFromString<AppPreferences>(content)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Cannot parse AppPreferences JSON", e)
        }
    }

    override suspend fun writeTo(t: AppPreferences, output: OutputStream) {
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
        writer.write(json.encodeToString(t))
        writer.flush()
    }
}