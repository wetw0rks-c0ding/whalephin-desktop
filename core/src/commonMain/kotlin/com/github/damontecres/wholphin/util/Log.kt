package com.github.damontecres.wholphin.util

/**
 * Minimal logging facade for shared code.
 *
 * The Android app uses Timber; the desktop port logs to stderr for now and can be
 * swapped for slf4j/file logging at M8.
 */
object Log {
    var enabled: Boolean = true

    fun v(msg: String) {
        if (enabled) println("V: $msg")
    }

    fun d(msg: String) {
        if (enabled) println("D: $msg")
    }

    fun w(msg: String) {
        if (enabled) println("W: $msg")
    }

    fun w(
        t: Throwable,
        msg: String,
    ) {
        if (enabled) {
            println("W: $msg")
            t.printStackTrace()
        }
    }

    fun e(msg: String) {
        if (enabled) println("E: $msg")
    }

    fun e(
        t: Throwable,
        msg: String,
    ) {
        if (enabled) {
            println("E: $msg")
            t.printStackTrace()
        }
    }
}
