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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
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

        // Create an owner-only temp directory for the IPC socket
        val tmpDir = Files.createTempDirectory("wholphin-mpv-").toFile()
        tmpDir.setReadable(false, false)
        tmpDir.setReadable(true, true)
        tmpDir.setWritable(false, false)
        tmpDir.setWritable(true, true)
        tmpDir.setExecutable(false, false)
        tmpDir.setExecutable(true, true)
        val sock = File(tmpDir, "mpv.sock")
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
            kotlinx.coroutines.delay(20)
            attempts++
        }

        if (!sock.exists()) {
            stopInternal()
            _info.value = _info.value.copy(state = PlaybackState.Error)
            throw IOException("mpv IPC socket did not appear")
        }

        _info.value = _info.value.copy(state = PlaybackState.Buffering)

        // Establish a shared Unix socket connection for both send and receive
        val channel = withContext(Dispatchers.IO) {
            SocketChannel.open(UnixDomainSocketAddress.of(sock.toPath()))
        }

        // Register observers for properties we need
        sendCommandOver(channel, "observe_property", 1, "time-pos")
        sendCommandOver(channel, "observe_property", 2, "duration")
        sendCommandOver(channel, "observe_property", 3, "pause")
        sendCommandOver(channel, "observe_property", 4, "eof-reached")

        // Observe position + state from the shared channel's input
        engineScope.launch(Dispatchers.IO) {
            try {
                val reader = Channels.newReader(channel, Charsets.UTF_8)
                val buffered = reader.buffered()
                while (isActive) {
                    val line = buffered.readLine() ?: break
                    if (line.isBlank()) continue
                    handleMessage(line)
                }
            } catch (_: IOException) {
                // Connection closed
            }
        }

        // Save the channel for sendCommand to use
        this.activeChannel = channel

        if (startPositionMs > 0) {
            sendCommandOver(channel, "set", "time-pos", startPositionMs / 1000.0)
        }
    }

    // Volatile shared-channel reference set by play(), cleared by stopInternal()
    @Volatile
    private var activeChannel: SocketChannel? = null

    private fun sendCommand(vararg args: Any) {
        val ch = activeChannel ?: return
        sendCommandOver(ch, *args)
    }

    private fun sendCommandOver(channel: SocketChannel, vararg args: Any) {
        try {
            val id = ++commandId
            val jsonStr = buildJsonCommand(id, *args)
            val bytes = "$jsonStr\n".toByteArray(Charsets.UTF_8)
            java.nio.ByteBuffer.wrap(bytes).let { buf ->
                while (buf.hasRemaining()) channel.write(buf)
            }
        } catch (e: IOException) {
            Log.e(e, "mpv write failed")
        }
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

    private fun handleMessage(line: String) {
        try {
            val obj = json.decodeFromString<JsonObject>(line)
            val event = obj["event"]?.let { (it as? JsonPrimitive)?.content?.trim('"') } ?: return
            when (event) {
                "property-change" -> {
                    val name = obj["name"]?.let { (it as? JsonPrimitive)?.content?.trim('"') } ?: return
                    val data = obj["data"]
                    when (name) {
                        "time-pos" -> {
                            val pos = (data as? JsonPrimitive)?.content?.toDoubleOrNull()
                            if (pos != null) {
                                _info.value = _info.value.copy(positionMs = (pos * 1000L).toLong())
                            }
                        }
                        "duration" -> {
                            val dur = (data as? JsonPrimitive)?.content?.toDoubleOrNull()
                            if (dur != null) {
                                _info.value = _info.value.copy(durationMs = (dur * 1000L).toLong())
                            }
                        }
                        "pause" -> {
                            val paused = (data as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: return
                            _info.value = _info.value.copy(state = if (paused) PlaybackState.Paused else PlaybackState.Playing)
                        }
                        "eof-reached" -> {
                            val eof = (data as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: return
                            if (eof) _info.value = _info.value.copy(state = PlaybackState.Ended)
                        }
                    }
                }
                "end-file" -> {
                    _info.value = _info.value.copy(state = PlaybackState.Ended)
                }
            }
        } catch (_: Exception) {
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
        val p = process ?: return
        p.destroy()
        val cleanExit = p.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!cleanExit) {
            p.destroyForcibly()
            p.waitFor(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        activeChannel?.close()
        activeChannel = null
        process = null
        socketFile?.parentFile?.deleteRecursively()
        socketFile = null
    }

    override fun release() {
        val job = engineScope.coroutineContext[Job] as? Job
        job?.children?.forEach { it.cancel() }
        stopInternal()
    }
}