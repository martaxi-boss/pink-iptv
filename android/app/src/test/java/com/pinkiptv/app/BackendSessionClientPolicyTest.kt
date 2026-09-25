package com.pinkiptv.app

import com.pinkiptv.app.network.BackendSessionClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendSessionClientPolicyTest {
    private val client = OkHttpClient()

    @Test
    fun productionBaseUrlRequiresHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            BackendSessionClient(
                baseUrl = "http://pink-api.invalid/",
                client = client,
            )
        }
    }

    @Test
    fun productionBaseUrlRejectsPathsAndCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            BackendSessionClient(
                baseUrl = "https://pink-api.invalid/api/",
                client = client,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackendSessionClient(
                baseUrl = "https://fixture-user@pink-api.invalid/",
                client = client,
            )
        }
    }
}
