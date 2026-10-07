package com.pinkiptv.extreme

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class PinkVodCatalogTest {
    private class Reply(val stream: InputStream, val code: Int = 200, val length: Long = -1) : HttpURLConnection(URL("https://fixture.invalid")) {
        var disconnected = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = code
        override fun getContentLengthLong() = length
        override fun getInputStream() = stream
    }
    @Test fun allFourFixedActionsPreserveOriginAndCredentialsRemainNative() {
        for (action in listOf("get_vod_categories", "get_vod_streams", "get_series_categories", "get_series")) {
            assertEquals("http://provider.example/player_api.php?username=fixture%20user&password=fixture%2Fpass&action=$action",
                PinkVodCatalog.requestUrl("http://provider.example/", "fixture user", "fixture/pass", action).toString())
            assertEquals("https", PinkVodCatalog.requestUrl("https://provider.example:8443", "u", "p", action).protocol)
        }
        for (origin in listOf("file:///tmp/x", "http://u:p@provider.example", "http://provider.example/path", "http://provider.example/?q=1", "http://provider.example/#fragment")) {
            try { PinkVodCatalog.requestUrl(origin,"u","p","get_series"); fail("Invalid origin admitted") } catch (_: IllegalArgumentException) {}
        }
        for (action in listOf("get_live_streams", "get_vod_info", "get.php", "http://provider.example")) {
            try { PinkVodCatalog.requestUrl("https://provider.example","u","p",action); fail("Invalid action admitted") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun largeCatalogStreamsToDiskBeyondLiveLimitWithoutTruncation() {
        val size = 36L * 1024 * 1024
        var remaining = size
        val stream = object : InputStream() {
            override fun read(): Int = if (remaining-- > 0) 32 else -1
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (remaining == 0L) return -1
                val count = minOf(remaining, len.toLong()).toInt()
                java.util.Arrays.fill(bytes, off, off+count, 32.toByte())
                if (remaining == size) bytes[off] = 91
                if (remaining == count.toLong()) bytes[off+count-1] = 93
                remaining -= count
                return count
            }
        }
        val file = File.createTempFile("pink-catalog", ".tmp")
        val reply = Reply(stream, length=size)
        try {
            PinkCatalogTransfer.download(reply,file,PinkCatalogTransfer.policy("get_vod_streams")) { true }
            assertEquals(size,file.length())
            assertEquals(91,file.inputStream().use { it.read() })
            assertTrue(reply.disconnected)
            assertFalse(reply.instanceFollowRedirects)
            assertEquals(15000,reply.readTimeout)
        } finally { file.delete() }
    }
    @Test fun unicodeBytesArePreservedAndErrorsDeletePartialFiles() {
        val bytes = "[{\"name\":\"Português 🎬\"}]".toByteArray(Charsets.UTF_8)
        val file = File.createTempFile("pink-catalog", ".tmp")
        try {
            PinkCatalogTransfer.download(Reply(ByteArrayInputStream(bytes)),file,PinkCatalogTransfer.policy("get_series")) { true }
            assertArrayEquals(bytes,file.readBytes())
            for ((code, body, limit, admitted) in listOf(
                Failure(302,bytes,1000,true), Failure(200,"<html>".toByteArray(),1000,true),
                Failure(200,bytes,3,true), Failure(200,bytes,1000,false))) {
                val reply = Reply(ByteArrayInputStream(body),code)
                try { PinkCatalogTransfer.download(reply,file,PinkCatalogTransfer.Policy(limit.toLong(),30)) { admitted }; fail("Failure admitted") }
                catch (_: IllegalStateException) {}
                assertFalse(file.exists()); assertTrue(reply.disconnected)
            }
            var checks = 0
            try { PinkCatalogTransfer.download(Reply(ByteArrayInputStream(bytes)),file,PinkCatalogTransfer.policy("get_series")) { ++checks < 3 }; fail("Lost VPN admitted") }
            catch (_: IllegalStateException) {}
            assertFalse(file.exists())
        } finally { file.delete() }
    }
    private data class Failure(val code:Int,val body:ByteArray,val limit:Int,val admitted:Boolean)
}
