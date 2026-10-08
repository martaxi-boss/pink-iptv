package com.pinkiptv.extreme

import org.junit.Assert.*
import org.junit.Test

class PinkCatalogPolicyTest {
    @Test fun fixedRoutePreservesSchemeAndEscapesCredentials() {
        assertEquals("http://provider.example/player_api.php?username=fixture%20user&password=fixture%2Fpass&action=get_live_streams",
            PinkCatalog.requestUrl("http://provider.example/", "fixture user", "fixture/pass", "get_live_streams").toString())
        assertEquals("https", PinkCatalog.requestUrl("https://provider.example:8443", "u", "p", "get_live_categories").protocol)
    }
    @Test fun rejectsUntrustedRoutesAndActions() {
        for (origin in listOf("file:///tmp/x", "http://u:p@provider.example", "http://provider.example/path", "http://provider.example/?q=1", "http://provider.example/#fragment")) {
            try { PinkCatalog.requestUrl(origin, "u", "p", "get_live_streams"); fail("Invalid origin admitted") }
            catch (_: IllegalArgumentException) { }
        }
        try { PinkCatalog.requestUrl("https://provider.example", "u", "p", "get.php"); fail("Invalid action admitted") }
        catch (_: IllegalArgumentException) { }
    }
}
