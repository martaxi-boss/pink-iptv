package com.pinkiptv.extreme

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class PinkVodCatalogTest {
    private class Reply(val stream: InputStream, val code: Int = 200, val length: Long = -1, val headers: () -> Unit = {}) : HttpURLConnection(URL("https://fixture.invalid")) {
        var disconnected = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode(): Int { headers(); return code }
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
            assertTrue(reply.readTimeout in 1..20000)
        } finally { file.delete() }
    }
    @Test fun unicodeBytesArePreservedAndErrorsDeletePartialFiles() {
        val bytes = "[{\"name\":\"Português 🎬\"}]".toByteArray(Charsets.UTF_8)
        val file = File.createTempFile("pink-catalog", ".tmp")
        try {
            PinkCatalogTransfer.download(Reply(ByteArrayInputStream(bytes)),file,PinkCatalogTransfer.policy("get_series")) { true }
            assertArrayEquals(bytes,file.readBytes())
            for ((code, body, limit, admitted) in listOf(
                Failure(302,bytes,1000,true),
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
    @Test fun aRecreatedDocumentCancelsOldIoAndCannotReuseItsLease() {
        val old = PinkCatalogTransfer.Lease()
        val request = Reply(ByteArrayInputStream("[]".toByteArray()))
        old.attach(request)
        assertTrue(old.active())
        old.close()
        assertTrue(request.disconnected)
        assertFalse(old.active())
        try { old.attach(Reply(ByteArrayInputStream(byteArrayOf()))); fail("Disposed document reused") }
        catch (_: IllegalStateException) {}
        val fresh = PinkCatalogTransfer.Lease()
        assertTrue(fresh.active())
        fresh.attach(Reply(ByteArrayInputStream("[]".toByteArray())))
        fresh.release(); fresh.close()
    }
    @Test fun unexpectedNativeStageFailuresAreClassifiedWithoutExposingSecrets() {
        for (phase in listOf("ACCOUNT_BINDING", "SOURCE_VALIDATION", "VPN_NETWORK", "STAGE_FILE", "STAGE_LIFECYCLE")) {
            val error = try {
                PinkCatalogStage.attempt(phase) { throw IllegalStateException("sensitive native failure text") }
                fail("Unexpected error was not mapped")
                null
            } catch (failure: PinkCatalogFailure) { failure }
            assertEquals(phase, error?.phase)
            assertEquals(phase, error?.message)
            assertNull(error?.cause)
            assertNull(error?.httpStatus)
            assertNull(error?.bytes)
        }
        val original = PinkCatalogFailure("READ_IDLE", 123, 20000, 200)
        val observed = try {
            PinkCatalogStage.attempt("STAGE_FILE") { throw original }
            fail("Expected typed failure")
            null
        } catch (failure: PinkCatalogFailure) { failure }
        assertSame(original, observed)
        try { PinkCatalogStage.attempt("private-secret") { true }; fail("Unsafe code admitted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun supersededCatalogsNeverExhaustFourSlotsOrLeakFiles() {
        val store = PinkVodStageStore()
        val files = mutableListOf<File>()
        try {
            repeat(8) { index ->
                val file = File.createTempFile("pink-stage", ".json")
                file.writeText("[]")
                files.add(file)
                store.admit("vod-$index", "fixture", "get_vod_streams", file)
                assertEquals(1, store.count())
                if (index > 0) {
                    assertFalse(files[index - 1].exists())
                    try { store.chunk("vod-${index - 1}", "fixture"); fail("Old token served") }
                    catch (failure: PinkCatalogFailure) { assertEquals("STAGE_EXPIRED", failure.phase) }
                }
            }
            val series = File.createTempFile("pink-series-stage", ".json")
            series.writeText("[]")
            files.add(series)
            store.admit("series", "fixture", "get_series", series)
            assertEquals(2, store.count())
            assertEquals("W10=", store.chunk("series", "fixture").getString("data"))
            assertTrue(store.chunk("series", "fixture").getBoolean("done"))
            assertEquals(1, store.count())
            val different = File.createTempFile("pink-other-stage", ".json")
            different.writeText("[]")
            files.add(different)
            store.admit("new-account", "other", "get_series", different)
            assertEquals(1, store.count())
            assertFalse("Old selected account stage not disposed", files[7].exists())
        } finally {
            store.closeAll()
            assertEquals(0, store.count())
            files.forEach { it.delete() }
        }
    }

    @Test fun missingFileAndCapacityFailureRemainDistinctAndFailClosed() {
        val store = PinkVodStageStore(maxOpen = 1)
        val file = File.createTempFile("pink-valid-stage", ".json").apply { writeText("[]") }
        val other = File.createTempFile("pink-cap-stage", ".json").apply { writeText("[]") }
        val missing = File.createTempFile("pink-missing-stage", ".json").apply { delete() }
        try {
            store.admit("original", "fixture", "get_vod_streams", file)
            try { store.admit("missing", "fixture", "get_vod_streams", missing); fail("Missing file admitted") }
            catch (failure: PinkCatalogFailure) { assertEquals("STAGE_FILE", failure.phase) }
            assertTrue(file.exists())
            assertEquals(1, store.count())
            try { store.admit("over-limit", "fixture", "get_series", other); fail("Cap exceeded") }
            catch (failure: PinkCatalogFailure) { assertEquals("STAGE_CAPACITY", failure.phase) }
            assertEquals(1, store.count())
            try { store.chunk("original", "other"); fail("Wrong account read") }
            catch (failure: PinkCatalogFailure) { assertEquals("ACCOUNT_BINDING", failure.phase) }
            assertEquals("W10=", store.chunk("original", "fixture").getString("data"))
        } finally { store.closeAll(); file.delete(); other.delete(); missing.delete() }
    }

    private data class Failure(val code:Int,val body:ByteArray,val limit:Int,val admitted:Boolean)

    @Test fun regressionOldIdleBudgetAcceptsProgressThatFifteenSecondsRejected() {
        // Virtual socket time: no sleeps, and the socket honors readTimeout.
        // Historical native default=20s, first bad disk transport=15s.
        val pauses = listOf(19000L, 19000L, 19000L, 19000L)
        fun run(idle: Int): Pair<Long, String?> {
            var time = 0L
            var index = 0
            lateinit var reply: Reply
            val stream = object : InputStream() {
                override fun read() = error("bulk only")
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (index == pauses.size) return -1
                    val pause = pauses[index++]
                    val bound = minOf(idle, reply.readTimeout)
                    time += minOf(pause, bound.toLong()) * 1000000
                    if (pause >= bound) throw java.net.SocketTimeoutException()
                    b[off] = 32; return 1
                }
            }
            reply = Reply(stream)
            val file = File.createTempFile("pink-progress", ".tmp")
            return try {
                PinkCatalogTransfer.download(reply,file,PinkCatalogTransfer.Policy(100,180), { time }) { true }
                file.length() to null
            } catch (failure: PinkCatalogFailure) {
                assertFalse(file.exists()); 0L to failure.phase
            } finally { file.delete() }
        }
        assertEquals("READ_IDLE", run(15000).second)
        assertEquals(4L to null, run(20000))
    }

    @Test fun realIdleDeadlineSizeAndLostVpnFailClosedWithSafePhases() {
        for ((pause, seconds, admitted, expected) in listOf(
            Bound(21000,180,true,"READ_IDLE"), Bound(19000,18,true,"TOTAL_DEADLINE"),
            Bound(19000,45,true,"TOTAL_DEADLINE"),
            Bound(1,180,false,"VPN_LOST"))) {
            var time = 0L
            val file = File.createTempFile("pink-bounds", ".tmp")
            val stream = object : InputStream() {
                override fun read() = error("bulk only")
                override fun read(b:ByteArray,off:Int,len:Int):Int { time += pause * 1000000; b[off]=91; return 1 }
            }
            val reply = Reply(stream)
            try {
                PinkCatalogTransfer.download(reply,file,PinkCatalogTransfer.Policy(100,seconds), { time }) { admitted }
                fail("Unbounded transfer admitted")
            } catch (failure: PinkCatalogFailure) { assertEquals(expected,failure.phase) }
            finally { assertFalse(file.exists()); assertTrue(reply.disconnected); file.delete() }
        }
    }
    @Test fun utf8BomAndProviderLengthAreNotRejectedBeforeWorkerValidation() {
        val bytes = "\uFEFF[{\"name\":\"Português 🎬\"}]".toByteArray(Charsets.UTF_8)
        val file = File.createTempFile("pink-json", ".tmp")
        try {
            PinkCatalogTransfer.download(Reply(ByteArrayInputStream(bytes),length=bytes.size.toLong()+1),file,PinkCatalogTransfer.policy("get_series")) { true }
            assertArrayEquals(bytes,file.readBytes())
        } finally { file.delete() }
    }
    private data class Bound(val pause:Long,val seconds:Long,val admitted:Boolean,val expected:String)

    @Test fun hardDeadlineCoversHeadersAndIsCancelledAfterEveryTransfer() {
        val file = File.createTempFile("pink-deadline", ".tmp")
        var expire: () -> Unit = {}
        var cancelled = false
        val reply = Reply(ByteArrayInputStream("[]".toByteArray()), headers={ expire() })
        try {
            PinkCatalogTransfer.download(reply,file,PinkCatalogTransfer.policy("get_series"),
                watchdog={ seconds, stop ->
                    assertEquals(215L,seconds)
                    expire=stop
                    val cancel = { cancelled=true }
                    cancel
                }) { true }
            fail("Expired headers admitted")
        } catch (failure: PinkCatalogFailure) { assertEquals("TOTAL_DEADLINE",failure.phase) }
        finally { assertTrue(cancelled); assertTrue(reply.disconnected); assertFalse(file.exists()); file.delete() }
    }

    @Test fun physicalFailureEvidenceRetainsOnlyPhaseStatusProgressAndDuration() {
        var time = 0L
        var reads = 0
        val file = File.createTempFile("pink-evidence", ".tmp")
        val stream = object : InputStream() {
            override fun read() = error("bulk only")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                time += 1000000000L
                if (++reads == 2) throw java.io.IOException("private URL, account, response")
                b[off] = 91
                return 1
            }
        }
        try {
            PinkCatalogTransfer.download(Reply(stream), file, PinkCatalogTransfer.policy("get_series"), {time}) {true}
            fail("Read failure was hidden")
        } catch (failure: PinkCatalogFailure) {
            assertEquals("READ", failure.phase)
            assertEquals(1L, failure.bytes ?: -1L)
            assertEquals(2000L, failure.elapsedMs ?: -1L)
            assertEquals(200, failure.httpStatus ?: -1)
            assertEquals("READ", failure.message)
            assertNull(failure.cause)
            assertFalse(file.exists())
        } finally { file.delete() }
    }
}
