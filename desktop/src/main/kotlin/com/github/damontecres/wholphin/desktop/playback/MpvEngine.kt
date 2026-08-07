package com.github.damontecres.wholphin.desktop.playback

import com.github.damontecres.wholphin.data.playback.PlaybackEngine
import com.github.damontecres.wholphin.data.playback.PlaybackInfo
import com.github.damontecres.wholphin.data.playback.PlaybackState
import com.github.damontecres.wholphin.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * libmpv-based playback engine using JSON IPC over a Unix socket.
 * Mirrors the Android app's player architecture but wraps mpv instead of Media3.
 */
class MpvEngine(
    private val engineScope: CoroutineScope,
) : PlaybackEngine {

    private val _info = MutableStateFlow(PlaybackInfo())
    override val info: StateFlow<PlaybackInfo> = _info

    private var process: Process? = null
    private var socketFile: File? = null
    private var commandId = 0
    private val pending = mutableMapOf<Int, kotlinx.coroutines.channels.SendChannel<Any>>()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun play(url: String, startPositionMs: Long) {
        stopInternal()
        val sock = File.createTempFile("wholphin-mpv-", ".sock")
        sock.delete() // mpv creates it
        socketFile = sock

        process = ProcessBuilder(
            "mpv",
            "--no-video", // phase 1: audio-only via IPC; full video embed comes in M4b
            "--input-ipc-server=${sock.path}",
            "--idle=yes",
            "--force-window=no",
            "--msg-level=all=info",
            url,
        ).redirectErrorStream(true).start()

        // Wait for socket to appear
        var attempts = 0
        while (attempts < 50) {
            if (sock.exists()) break
            Thread.sleep(20)
            attempts++
        }

        if (!sock.exists()) {
            stopInternal()
            _info.value = _info.value.copy(state = PlaybackState.Error)
            throw IOException("mpv IPC socket did not appear")
        }

        _info.value = _info.value.copy(state = PlaybackState.Buffering)

        // Observe position + state
        engineScope.launch { observeLoop() }

        if (startPositionMs > 0) {
            sendCommand("set", "time-pos", startPositionMs / 1000.0)
        }
    }

    private fun sendCommand(vararg args: Any) {
        val id = ++commandId
        val jsonStr = buildJsonCommand(id, *args)
        writeLine(jsonStr)
    }

    private fun buildJsonCommand(id: Int, vararg args: Any): String {
        val elements = args.map { a ->
            when (a) {
                is String -> JsonPrimitive(a)
                is Number -> JsonPrimitive(a)
                is Boolean -> JsonPrimitive(a)
                else -> JsonPrimitive(a.toString())
            }
        }
        val cmd = JsonArray(elements)
        val obj = JsonObject(mapOf(
            "command" to cmd,
            "request_id" to JsonPrimitive(id),
        ))
        return json.encodeToString(obj)
    }

    private fun writeLine(line: String) {
        try {
            process?.outputStream?.write("$line\n".toByteArray())
            process?.outputStream?.flush()
        } catch (e: IOException) {
            Log.e(e, "mpv write failed")
        }
    }

    private fun observeLoop() {
        val reader = socketFile?.bufferedReader() ?: return
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            handleMessage(line)
        }
    }

    private fun handleMessage(line: String) {
        // {"event":"property-change","name":"time-pos","data":12.34} etc.
        try {
            val obj = json.decodeFromString<JsonObject>(line)
            val event = obj["event"]?.let { (it as JsonPrimitive).content }?.trim('"') ?: return
            when (event) {
                "property-change" -> {
                    val name = obj["name"]?.let { (it as JsonPrimitive).content }?.trim('"') ?: return
                    val data = obj["data"]
                    when (name) {
                        "time-pos" -> {
                            val pos = (data as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                            _info.value = _info.value.copy(positionMs = (pos * 1000L).toLong())
                        }
                        "duration" -> {
                            val dur = (data as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                            _info.value = _info.value.copy(durationMs = (dur * 1000L).toLong())
                        }
                        "pause" -> {
                            val paused = (data as? JsonPrimitive)?.content?.toBoolean() ?: false
                            _info.value = _info.value.copy(state = if (paused) PlaybackState.Paused else PlaybackState.Playing)
                        }
                        "eof-reached" -> {
                            val eof = (data as? JsonPrimitive)?.content?.toBoolean() ?: false
                            if (eof) _info.value = _info.value.copy(state = PlaybackState.Ended)
                        }
                    }
                }
                "end-file" -> {
                    _info.value = _info.value.copy(state = PlaybackState.Ended)
                }
            }
        } catch (e: Exception) {
            // ignore parse failures on non-JSON lines
        }
    }

    override fun pause() = sendCommand("set", "pause", true)
    override fun resume() = sendCommand("set", "pause", false)
    override fun togglePlayPause() = sendCommand("cycle", "pause")
    override fun seek(positionMs: Long) = sendCommand("set", "time-pos", positionMs / 1000.0)
    override fun seekRelative(offsetMs: Long) = sendCommand("add", "time-pos", offsetMs / 1000.0)
    override fun setVolume(percent: Int) = sendCommand("set", "volume", percent)
    override fun stop() { stopInternal(); _info.value = _info.value.copy(state = PlaybackState.Idle) }

    private fun stopInternal() {
        process?.destroy()
        process?.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
        process = null
        socketFile?.delete()
        socketFile = null
    }

    override fun release() {
        val job = engineScope.coroutineContext[Job] as? Job
        job?.children?.forEach { it.cancel() }
        stopInternal()
    }
}