package com.github.damontecres.wholphin.desktop.util

import java.security.MessageDigest

/** Compares an entered plain-text PIN against a stored (hashed) value. */
fun checkPin(entered: String, storedHash: String): Boolean {
    val enteredHash = hashPin(entered)
    return MessageDigest.isEqual(enteredHash.toByteArray(), storedHash.toByteArray())
}

private fun hashPin(pin: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray())
        .joinToString("") { "%02x".format(it) }