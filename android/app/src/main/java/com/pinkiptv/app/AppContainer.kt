package com.pinkiptv.app

import android.content.Context
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.network.BackendSessionClient
import com.pinkiptv.app.storage.AndroidKeystoreCredentialCipher
import com.pinkiptv.app.storage.CredentialStore
import com.pinkiptv.app.storage.DataStoreCredentialPersistence
import com.pinkiptv.app.storage.SecureCredentialStore
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    val sessionRepository: SessionRepository = BackendSessionClient(
        baseUrl = BuildConfig.PINK_API_BASE_URL,
        client = httpClient,
    )

    val credentialStore: CredentialStore = SecureCredentialStore(
        cipher = AndroidKeystoreCredentialCipher(),
        persistence = DataStoreCredentialPersistence(context.applicationContext),
    )
}
