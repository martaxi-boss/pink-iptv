package com.pinkiptv.extreme

import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test

class PinkWebBridgeTest {
    @Test fun validatedAccountCacheSurvivesVaultRecreationOnlyInProcessMemory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("pink_account_v1", 0)
        prefs.edit().clear().commit()
        val blob = """{"entries":[{"_id":"fixture"}],"selectedId":"fixture"}"""
        try {
            val first = PinkVault(context)
            assertTrue(first.markValidated(blob))
            assertNull(prefs.getString("account", null))
            val second = PinkVault(context)
            assertEquals(blob, second.readValidated())
            assertNull(prefs.getString("account", null))
            assertTrue(second.markValidated(""))
            assertEquals("", PinkVault(context).readValidated())
        } finally {
            PinkVault(context).markValidated("")
            prefs.edit().clear().commit()
        }
    }

    private fun evaluate(view: WebView, script: String): String {
        val latch = CountDownLatch(1)
        var result: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.evaluateJavascript(script) { result = it; latch.countDown() }
        }
        check(latch.await(5, TimeUnit.SECONDS))
        return JSONTokener(checkNotNull(result)).nextValue().toString()
    }
    private fun waitValue(view: WebView, script: String): String {
        val deadline = System.currentTimeMillis()+30000
        while (System.currentTimeMillis()<deadline) {
            val value = evaluate(view, script)
            if (value.isNotEmpty() && value != "null") return value
            Thread.sleep(100)
        }
        throw AssertionError("Local message transport unavailable")
    }
    @Test fun localMainDocumentCanUseVaultAndForeignOriginCannotSeeNativeObject() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            view = WebView(context)
            view.settings.javaScriptEnabled = true
            PinkWebBridge.attach(context, view)
            view.loadDataWithBaseURL("https://tauri.localhost",
                """<script>window.result='';
                PinkNative.onmessage=(e)=>{window.result=e.data};
                PinkNative.postMessage(JSON.stringify({id:'1',operation:'vaultWrite',payload:{value:'synthetic-account-blob'}}));
                </script>""", "text/html", "UTF-8", null)
        }
        try {
            val saved = JSONObject(waitValue(view,"window.result||''"))
            assertTrue(saved.getBoolean("ok") && saved.getBoolean("result"))
            instrumentation.runOnMainSync {
                view.evaluateJavascript("""window.result='';
                    PinkNative.postMessage(JSON.stringify({id:'2',operation:'vaultRead',payload:{}}));""",null)
            }
            val loaded = JSONObject(waitValue(view,"window.result||''"))
            assertTrue(loaded.getBoolean("ok"))
            assertEquals("synthetic-account-blob",loaded.getString("result"))
            val beforePulse = PinkWebBridge.rendererPulseForTests()
            evaluate(view, "PinkNative.postMessage(JSON.stringify({id:'0',operation:'livePulse',payload:{}}));true")
            val pulseDeadline = System.currentTimeMillis()+2000
            while (PinkWebBridge.rendererPulseForTests() == beforePulse && System.currentTimeMillis()<pulseDeadline) Thread.sleep(50)
            assertTrue(PinkWebBridge.rendererPulseForTests()>beforePulse)
            instrumentation.runOnMainSync {
                view.loadDataWithBaseURL("https://tauri.localhost.foreign.example",
                    "<script>window.marker=String(typeof window.PinkNative)</script>",
                    "text/html","UTF-8",null)
            }
            assertEquals("undefined",waitValue(view,"window.marker||''"))
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
            check(context.getSharedPreferences("pink_account_v1",0).edit().clear().commit())
        }
    }
}
