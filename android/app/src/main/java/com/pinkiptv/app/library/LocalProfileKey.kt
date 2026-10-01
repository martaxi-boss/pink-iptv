package com.pinkiptv.app.library

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object LocalProfileKey {
    private const val NAMESPACE = "PINK-IPTV|account|"

    fun derive(exactUsername: String): String {
        val bytes = (NAMESPACE + exactUsername).toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
