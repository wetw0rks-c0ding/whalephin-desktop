package com.github.damontecres.wholphin.desktop.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val FORMAT_V1 = "v1"
private const val PBKDF2_ITERATIONS = 100_000
private const val SALT_BYTES = 16
private const val DERIVED_KEY_BYTES = 32

/**
 * Produce a stored credential from [pin] and [pinLength].
 * Format:  `v1:<length>:<hex-salt>:<hex-derived-key>`
 */
fun hashPin(pin: String, pinLength: Int): String {
    val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
    val key = derive(pin, salt)
    val saltHex = salt.joinToString("") { "%02x".format(it) }
    val keyHex = key.joinToString("") { "%02x".format(it) }
    return "$FORMAT_V1:$pinLength:$saltHex:$keyHex"
}

/**
 * Extract the PIN length from a stored credential string.
 */
fun pinLengthFromHash(stored: String?): Int =
    stored
        ?.split(":")
        ?.takeIf { it.size == 4 && it[0] == FORMAT_V1 }
        ?.get(1)
        ?.toIntOrNull()
        ?: 4

/**
 * Compare an entered plain-text PIN against a stored versioned credential.
 */
fun checkPin(entered: String, stored: String): Boolean {
    val parts = stored.split(":")
    if (parts.size != 4 || parts[0] != FORMAT_V1) {
        return legacyCheck(entered, stored)
    }
    if (parts[2].length != SALT_BYTES * 2 || parts[3].length != DERIVED_KEY_BYTES * 2) {
        return false
    }
    val salt = runCatching { parts[2].chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
        .getOrNull() ?: return false
    val expectedKey = runCatching { parts[3].chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
        .getOrNull() ?: return false
    val enteredKey = derive(entered, salt)
    return MessageDigest.isEqual(enteredKey, expectedKey)
}

private fun derive(pin: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, DERIVED_KEY_BYTES * 8)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    return factory.generateSecret(spec).encoded
}

/** Legacy SHA-256 comparison for existing unsalted hashes. */
private fun legacyCheck(entered: String, storedHash: String): Boolean {
    val enteredHash =
        MessageDigest
            .getInstance("SHA-256")
            .digest(entered.toByteArray())
            .joinToString("") { "%02x".format(it) }
    return MessageDigest.isEqual(enteredHash.toByteArray(), storedHash.toByteArray())
}