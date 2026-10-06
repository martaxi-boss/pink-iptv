package com.pinkiptv.extreme

/** Upstream exception/URL arguments are never forwarded to Android logs. */
@Suppress("UNUSED_PARAMETER")
object PinkSafeLog {
    fun d(tag: String, message: String?, error: Throwable? = null): Int = 0
    fun w(tag: String, message: String?, error: Throwable? = null): Int = android.util.Log.w("PINK", "Operation unavailable")
    fun e(tag: String, message: String?, error: Throwable? = null): Int = android.util.Log.e("PINK", "Operation unavailable")
}
