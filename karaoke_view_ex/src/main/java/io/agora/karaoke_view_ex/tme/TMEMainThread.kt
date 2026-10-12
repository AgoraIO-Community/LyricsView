package io.agora.karaoke_view_ex.tme

import android.os.Handler
import android.os.Looper

internal object TMEMainThread {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    fun post(callback: Runnable) {
        check(handler.post(callback)) { "Main thread is no longer accepting callbacks" }
    }

    fun requireCurrent() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "TME request operations require the main thread" }
    }
}
