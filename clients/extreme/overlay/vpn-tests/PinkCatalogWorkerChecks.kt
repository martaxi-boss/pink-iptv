package com.pinkiptv.extreme

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs the bundled production worker under the actual Tauri/WebView origin. */
class PinkCatalogWorkerChecks {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun descendants(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) } else emptyList()

    fun verify(activity: ActivityScenario<MainActivity>) {
        lateinit var web: WebView
        activity.onActivity { host ->
            web = descendants(host.window.decorView).filterIsInstance<WebView>().single()
        }
        val readyDeadline = System.currentTimeMillis() + 15000
        var ready = false
        while (!ready && System.currentTimeMillis() < readyDeadline) {
            instrumentation.runOnMainSync { ready = web.progress == 100 && web.url?.startsWith("http") == true }
            if (!ready) Thread.sleep(100)
        }
        assertTrue("Recreated WebView did not load its local document", ready)
        val path = instrumentation.context.assets.open("pink-catalog-worker-path.txt").bufferedReader().use { it.readText().trim() }
        val script = """
          (() => {
            window.__pinkCatalogFixture = null;
            const run = async () => {
              let ticks = 0;
              const timer = setInterval(() => ticks++, 5);
              try {
                for (const kind of ['vod', 'series']) {
                  const raw = Array.from({length:5000}, (_,i) => ({stream_id:i+1,series_id:i+1,name:'Português 🎬 '+i,category_id:'7',genre:'Drama'}));
                  const rows = [];
                  const key = '__pink_catalog_test__';
                  await new Promise((resolve,reject) => {
                    const worker = new Worker(${JSONObject.quote(path)}, {type:'module'});
                    worker.onerror = () => {worker.terminate();reject(Error('worker load'));};
                    worker.onmessage = ({data}) => {
                      if (data.error) {worker.terminate();reject(Error('worker parse'));return;}
                      if (data.done) {
                        worker.terminate();
                        if (!data.persisted || rows.length!==5000 || new Set(rows.map(r=>r.id)).size!==5000 || rows.some(r=>r.category!=='Português') || (kind==='series' && rows.some(r=>r.genre!=='Drama'))) {reject(Error('worker completeness'));return;}
                        resolve();return;
                      }
                      rows.push(...data.rows);
                      setTimeout(()=>worker.postMessage({next:true}),0);
                    };
                    worker.postMessage({body:JSON.stringify(raw),kind,categories:new Map([['7','Português']]),entryId:key,ttl:86400000});
                  });
                  await new Promise((resolve,reject) => {
                    const req = indexedDB.open('xt_cache',4);
                    req.onerror=()=>reject(Error('cache open'));
                    req.onsuccess=()=>{
                      const db=req.result, tx=db.transaction('entries','readwrite'), store=tx.objectStore('entries');
                      const get=store.get('xt_cache:'+key+':'+kind);
                      get.onsuccess=()=>{if(get.result?.data?.length!==5000)tx.abort();else store.delete('xt_cache:'+key+':'+kind);};
                      tx.oncomplete=()=>{db.close();resolve();};
                      tx.onabort=()=>{db.close();reject(Error('cache completeness'));};
                    };
                  });
                }
                if (!ticks) throw Error('UI did not tick');
                window.__pinkCatalogFixture={ok:true,ticks,rows:10000};
              } finally {clearInterval(timer);}
            };
            run().catch(()=>{window.__pinkCatalogFixture={ok:false};});
          })();
        """.trimIndent()
        instrumentation.runOnMainSync { web.evaluateJavascript(script, null) }
        val deadline = System.currentTimeMillis() + 20000
        while (System.currentTimeMillis() < deadline) {
            val latch = CountDownLatch(1)
            var value: String? = null
            instrumentation.runOnMainSync {
                web.evaluateJavascript("JSON.stringify(window.__pinkCatalogFixture)") { value = it; latch.countDown() }
            }
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            val decoded = value?.let { JSONTokener(it).nextValue() }
            if (decoded is String && decoded != "null") {
                val result = JSONObject(decoded)
                assertTrue("Bundled Android catalog worker/cache failed", result.optBoolean("ok"))
                assertEquals(10000, result.getInt("rows"))
                assertTrue(result.getInt("ticks") > 0)
                instrumentation.sendStatus(2, android.os.Bundle().apply {
                    putString("stream", "\nPINK_ANDROID_CATALOG_WORKER=PASS;ROWS=10000;UI_TICKS=" + result.getInt("ticks") + "\n")
                })
                instrumentation.runOnMainSync { web.evaluateJavascript("delete window.__pinkCatalogFixture", null) }
                return
            }
            Thread.sleep(100)
        }
        throw AssertionError("Bundled Android catalog worker did not complete")
    }
}
