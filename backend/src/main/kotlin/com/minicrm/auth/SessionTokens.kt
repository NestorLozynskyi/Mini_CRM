package com.minicrm.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/** Непрозорі токени сесій: 256 біт випадковості у cookie, у БД — лише SHA-256. */
object SessionTokens {
    private val random = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII)))

    fun looksValid(token: String?): Boolean = token != null && token.length in 40..64 && token.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}
