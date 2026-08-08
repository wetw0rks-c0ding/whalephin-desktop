package com.github.damontecres.wholphin.util

/**
 * Minimal logging facade for shared code.
 *
 * Logs to stderr so warnings and errors don't mix with normal stdout.
 * Can be swapped for slf4j/file logging at M8.
 */
object Log {
    var enabled: Boolean = true

    fun v(msg: String) {
        if (enabled) System.err.println("V: $msg")
    }

    fun d(msg: String) {
        if (enabled) System.err.println("D: $msg")
    }

    fun w(msg: String) {
        if (enabled) System.err.println("W: $msg")
    }

    fun w(
        t: Throwable,
        msg: String,
    ) {
        if (enabled) {
            System.err.println("W: $msg")
            t.printStackTrace(System.err)
        }
    }

    fun e(msg: String) {
        if (enabled) System.err.println("E: $msg")
    }

    fun e(
        t: Throwable,
        msg: String,
    ) {
        if (enabled) {
            System.err.println("E: $msg")
            t.printStackTrace(System.err)
        }
    }
}
