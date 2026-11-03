package io.agora.examples.karaoke_view_ex.agora.tme

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.agora.karaoke_view_ex.tme.TMEParser
import io.agora.karaoke_view_ex.tme.TMEParserDelegate
import io.agora.karaoke_view_ex.tme.TMEParseError
import io.agora.karaoke_view_ex.tme.TMESongsResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class TMEParserInstrumentedTest {
    @Test fun defaultParserPostsSuccessAndInvalidRequestToMainWithoutInlineDelivery() {
        val parser = TMEParser()
        val completion = CountDownLatch(2)
        val onMain = AtomicBoolean(true)
        val returned = AtomicBoolean(false)
        val asynchronous = AtomicBoolean(true)
        val ids = mutableListOf<String>()
        val listener = object : TMEParserDelegate {
            private fun record(id: String) {
                if (Looper.myLooper() != Looper.getMainLooper()) onMain.set(false)
                if (!returned.get()) asynchronous.set(false)
                ids.add(id)
                completion.countDown()
            }
            override fun onSongs(requestId: String, result: TMESongsResult) = record(requestId)
            override fun onParseError(requestId: String, jsonOption: String, responseBody: String, error: TMEParseError) = record(requestId)
        }
        parser.delegate = listener
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                parser.parse("success", """{"vendorId":2,"actionType":"songs"}""", 200,
                    """{"code":0,"data":{"songList":[]}}""")
                parser.parse("error", "invalid JSON", 200, "invalid JSON")
                assertTrue(ids.isEmpty())
                returned.set(true)
            }
            assertTrue("Missing Android main-thread callbacks", completion.await(5, TimeUnit.SECONDS))
            assertTrue(onMain.get())
            assertTrue(asynchronous.get())
            assertEquals(listOf("success", "error"), ids)
            // Keep the weakly-held listener alive until assertions finish.
            assertSame(listener, parser.delegate)
        } finally {
            parser.close()
        }
    }
}
