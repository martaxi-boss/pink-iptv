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
            "b69ae7150206d216acf894eccf36887b1cfd3d4f7605ef573bb08489d1ea3af4", // pragma: allowlist secret // pragma: allowlist secret
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
