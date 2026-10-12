package io.agora.karaoke_view_ex.tme

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TMEParserTest {
    @Test fun allEightActionsDispatchDistinctTypedCallbacksInSubmissionOrder() {
        TMEAsyncTestHarness().use { h ->
            val listener = TMERecordingDelegate()
            h.parser.delegate = listener
            val cases = listOf(
                "songs" to """{"songList":[]}""",
                "search-song" to """{"total":0,"songList":[]}""",
                "song-info" to """{"songList":[]}""",
                "song-url" to """{"mediaList":[]}""",
                "songlist-page" to """{"total":0,"list":[]}""",
                "songlist-detail" to """{"code":"c","title":"t","songList":[]}""",
                "ranklist-page" to """{"total":0,"list":[]}""",
                "ranklist-detail" to """{"code":"r","title":"t","songList":[]}"""
            )
            cases.forEachIndexed { index, (action, data) ->
                h.parser.parse("id-$index", tmeRequest(action), 200, """{"code":0,"data":$data}""")
            }
            assertTrue("No inline callback is allowed", listener.ids.isEmpty())
            repeat(cases.size) { h.deliverNext() }
            assertEquals(cases.map { it.first }, listener.actions)
            assertEquals(cases.indices.map { "id-$it" }, listener.ids)
            assertTrue(listener.errors.isEmpty())
            assertTrue(listener.threads.all { it === Thread.currentThread() })
            assertTrue(h.callbacks.isEmpty())
        }
    }

    @Test fun invalidInputsAreAlsoAsynchronousAndRetainOriginalStrings() {
        TMEAsyncTestHarness().use { h ->
            val listener = TMERecordingDelegate()
            h.parser.delegate = listener
            val option = " invalid request \n"
            val response = " original response ?signature=a%2Fb \n"
            h.parser.parse("bad", option, 500, response)
            assertTrue(listener.errors.isEmpty())
            h.deliverNext()
            assertEquals(listOf("bad"), listener.ids)
            assertEquals(listOf(option to response), listener.originals)
            assertTrue(listener.errors.single() is TMEParseError.InvalidRequest)
            assertFalse(listener.errors.single().toString().contains("signature"))
            assertTrue(listener.results.isEmpty())
        }
    }

    @Test fun validAndFailedResponsesStayOrderedAndCallbackExactlyOnce() {
        TMEAsyncTestHarness().use { h ->
            val listener = TMERecordingDelegate()
            h.parser.delegate = listener
            for (index in 0..39) {
                h.parser.parse("$index", tmeRequest(), if (index % 2 == 0) 200 else 503, emptySongsResponse)
            }
            repeat(40) { h.deliverNext() }
            assertEquals((0..39).map { "$it" }, listener.ids)
            assertEquals(20, listener.results.size)
            assertEquals(20, listener.errors.size)
            assertTrue(listener.errors.all { it is TMEParseError.HttpError })
            assertTrue(h.callbacks.isEmpty())
        }
    }

    @Test fun parseReturnsWhileWorkerIsBusyAndCloseDrainsAcceptedInputs() {
        TMEAsyncTestHarness().use { h ->
            val listener = TMERecordingDelegate()
            h.parser.delegate = listener
            val release = CountDownLatch(1)
            val running = CountDownLatch(1)
            h.worker.execute { running.countDown(); release.await(5, TimeUnit.SECONDS) }
            assertTrue(running.await(5, TimeUnit.SECONDS))
            try {
                h.parser.parse("queued", tmeRequest(), 200, emptySongsResponse)
                h.parser.close()
                assertTrue(listener.ids.isEmpty())
                assertThrows(IllegalStateException::class.java) {
                    h.parser.parse("after-close", tmeRequest(), 200, emptySongsResponse)
                }
            } finally {
                release.countDown()
            }
            h.deliverNext()
            assertEquals(listOf("queued"), listener.ids)
        }
    }

    @Test fun removedDelegateIsNotCapturedByQueuedCallbacks() {
        TMEAsyncTestHarness().use { h ->
            val listener = TMERecordingDelegate()
            h.parser.delegate = listener
            h.parser.parse("orphan", tmeRequest(), 200, emptySongsResponse)
            h.parser.delegate = null
            h.deliverNext()
            assertTrue(listener.ids.isEmpty())
        }
    }
}
