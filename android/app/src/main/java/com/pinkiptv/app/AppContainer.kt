package com.pinkiptv.app

import android.content.Context
import androidx.room.Room
import com.pinkiptv.app.library.ActiveLibraryProfileStore
import com.pinkiptv.app.library.LocalLibraryRepository
import com.pinkiptv.app.library.PinkLibraryDatabase
import com.pinkiptv.app.library.PlaybackActivityRecorder
import com.pinkiptv.app.library.RoomLocalLibraryRepository
import com.pinkiptv.app.library.RoomPlaybackActivityRecorder
import com.pinkiptv.app.model.CatalogRepository
import com.pinkiptv.app.model.RuntimeProviderSessionStore
import com.pinkiptv.app.model.SessionRepository
import com.pinkiptv.app.network.BackendSessionClient
import com.pinkiptv.app.network.XtreamCatalogClient
import com.pinkiptv.app.network.buildXtreamHttpClient
import com.pinkiptv.app.player.Media3PlaybackFacadeFactory
import com.pinkiptv.app.player.PlaybackFacadeFactory
import com.pinkiptv.app.storage.AndroidKeystoreCredentialCipher
import com.pinkiptv.app.storage.CredentialStore
import com.pinkiptv.app.storage.DataStoreCredentialPersistence
import com.pinkiptv.app.storage.SecureCredentialStore
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val backendHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val providerHttpClient = buildXtreamHttpClient()

    val providerSessionStore = RuntimeProviderSessionStore()

    private val libraryDatabase = Room.databaseBuilder(
        context.applicationContext,
        PinkLibraryDatabase::class.java,
        "pink_library.db",
    ).build()

    val activeLibraryProfileStore = ActiveLibraryProfileStore()

    val localLibraryRepository: LocalLibraryRepository =
        RoomLocalLibraryRepository(libraryDatabase)

    val playbackActivityRecorder: PlaybackActivityRecorder =
        RoomPlaybackActivityRecorder(
            repository = localLibraryRepository,
            profileStore = activeLibraryProfileStore,
        )

    val sessionRepository: SessionRepository = BackendSessionClient(
        baseUrl = BuildConfig.PINK_API_BASE_URL,
        client = backendHttpClient,
    )

    val catalogRepository: CatalogRepository = XtreamCatalogClient(
        sessionStore = providerSessionStore,
        client = providerHttpClient,
    )

    val playbackFacadeFactory: PlaybackFacadeFactory = Media3PlaybackFacadeFactory(
        context = context.applicationContext,
        sessionStore = providerSessionStore,
        client = providerHttpClient,
    )

    val credentialStore: CredentialStore = SecureCredentialStore(
        cipher = AndroidKeystoreCredentialCipher(),
        persistence = DataStoreCredentialPersistence(context.applicationContext),
    )
}
