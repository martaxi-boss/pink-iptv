package com.pinkiptv.extreme

import org.junit.Assert.*
import org.junit.Test

class PinkCatalogPolicyTest {
    @Test fun fixedRoutePreservesSchemeAndEscapesCredentials() {
        assertEquals("http://provider.example/player_api.php?username=fixture%20user&password=fixture%2Fpass&action=get_live_streams",
            PinkCatalog.requestUrl("http://provider.example/", "fixture user", "fixture/pass", "get_live_streams").toString())
        assertEquals("https", PinkCatalog.requestUrl("https://provider.example:8443", "u", "p", "get_live_categories").protocol)
    }
    @Test fun vodAndSeriesCategoriesReuseLiveNativeNetworkWithoutRelaxingLimits() {
        for (action in listOf("get_vod_categories", "get_series_categories")) {
            assertEquals("http://provider.example/player_api.php?username=fixture%20user&password=fixture%2Fpass&action=$action",
                PinkCatalog.requestUrl("http://provider.example/", "fixture user", "fixture/pass", action).toString())
            assertEquals(8 * 1024 * 1024, PinkCatalog.maxResponseBytes(action))
        }
        for (action in listOf("get_live_categories", "get_live_streams")) {
            assertEquals(32 * 1024 * 1024, PinkCatalog.maxResponseBytes(action))
        }
        for (action in listOf("get_vod_streams", "get_series", "get_vod_info", "get.php")) {
            try { PinkCatalog.requestUrl("https://provider.example", "u", "p", action); fail("Bulk/unsafe action entered direct path") }
            catch (_: IllegalArgumentException) { }
        }
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
