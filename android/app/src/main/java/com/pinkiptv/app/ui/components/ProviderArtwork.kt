package com.pinkiptv.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.pinkiptv.app.network.buildXtreamHttpClient
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/** Provider artwork is optional. No credentials, disk cache, logging or redirects. */
@Composable
fun ProviderArtwork(url: String?, title: String, modifier: Modifier = Modifier, poster: Boolean = false) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = null
        value = ArtworkMemory.load(url)
    }
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        ))),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(loaded.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (poster) ContentScale.Crop else ContentScale.Fit)
        } else {
            Text(title.take(2).uppercase(), style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.secondary)
        }
    }
}

internal fun safeArtworkUrl(value: String?): String? {
    val url = value?.toHttpUrlOrNull() ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    if (url.queryParameterNames.any { it.lowercase() in setOf("username", "password", "token", "auth", "key") }) return null
    // Provider artwork is never a credential-bearing stream URL.
    if (url.pathSegments.firstOrNull() in setOf("live", "movie", "series", "timeshift")) return null
    return url.toString()
}

private object ArtworkMemory {
    private val client = buildXtreamHttpClient()
    private val slots = Semaphore(4)
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(value: String?): Bitmap? {
        val url = safeArtworkUrl(value) ?: return null
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            slots.withPermit {
                ensureActive()
                cache.get(url)?.let { return@withPermit it }
                try {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) return@withPermit null
                        val body = response.body ?: return@withPermit null
                        if (body.contentLength() > 2 * 1024 * 1024) return@withPermit null
                        val bytes = ByteArrayOutputStream()
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (bytes.size() + count > 2 * 1024 * 1024) return@withPermit null
                                bytes.write(buffer, 0, count)
                            }
                        }
                        val data = bytes.toByteArray()
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withPermit null
                        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 512) {
                            options.inSampleSize *= 2
                        }
                        ensureActive()
                        BitmapFactory.decodeByteArray(data, 0, data.size, options)?.also { cache.put(url, it) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
