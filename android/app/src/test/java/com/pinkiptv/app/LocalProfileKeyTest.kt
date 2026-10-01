package com.pinkiptv.app

import com.pinkiptv.app.library.LocalProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProfileKeyTest {
    @Test
    fun sameExactUsernameProducesStableSha256ProfileKey() {
        val first = LocalProfileKey.derive("alice")
        val second = LocalProfileKey.derive("alice")

        assertEquals(first, second)
        assertEquals(
            "b69ae715" +
                "0206d216" +
                "acf894ec" +
                "cf36887b" +
                "1cfd3d4f" +
                "7605ef57" +
                "3bb08489" +
                "d1ea3af4",
            first,
        )
        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun differentOrCaseChangedAccountsDoNotShareProfile() {
        assertNotEquals(
            LocalProfileKey.derive("alice"),
            LocalProfileKey.derive("bob"),
        )
        assertNotEquals(
            LocalProfileKey.derive("alice"),
            LocalProfileKey.derive("Alice"),
        )
    }

    @Test
    fun derivedProfileKeyContainsNoPlaintextUsername() {
        val key = LocalProfileKey.derive("plain-user")
        assertFalse(key.contains("plain-user"))
        assertFalse(key.contains("PINK-IPTV"))
    }
}
