package com.github.damontecres.wholphin.desktop

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Installs a default uncaught exception handler that writes crash reports to a
 * file in the XDG state directory (~/.local/state/wholphin/crashes/).
 */
object CrashHandler {
    private val logDir: File by lazy {
        val xdgState = System.getenv("XDG_STATE_HOME")
            ?: "${System.getProperty("user.home")}/.local/state"
        File(xdgState, "wholphin/crashes").also { it.mkdirs() }
    }

    fun install() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(throwable)
            original?.uncaughtException(thread, throwable)
            // If no original handler, print to stderr and exit
            if (original == null) {
                throwable.printStackTrace(System.err)
                Runtime.getRuntime().exit(1)
            }
        }
    }

    private fun writeCrash(throwable: Throwable) {
        try {
            val timestamp = DateTimeFormatter
                .ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())
            val file = File(logDir, "crash_$timestamp.log")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            file.writeText(sw.toString())
            System.err.println("Crash report written to ${file.absolutePath}")
        } catch (_: Exception) {
            // Best-effort; we're crashing anyway
        }
    }
}