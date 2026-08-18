package com.estatedesk.crm.core

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN hashing for app lock: PBKDF2WithHmacSHA256, 120k iterations,
 *  random 16-byte salt. Never stores the PIN itself. */
object Security {

    fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return key.joinToString("") { "%02x".format(it) }
    }

    fun randomSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun verifyPin(pin: String, saltHex: String, expectedHash: String): Boolean {
        if (saltHex.isBlank() || expectedHash.isBlank()) return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val actual = hashPin(pin, salt)
        return constantTimeEquals(actual, expectedHash)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    fun saltToHex(salt: ByteArray): String = salt.joinToString("") { "%02x".format(it) }
}
