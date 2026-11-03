package io.agora.karaoke_view_ex.tme

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class TMERequestManagerTest {
    private fun manager(h: TMEAsyncTestHarness, sender: TMERequestSender): TMERequestManager {
        val main = Thread.currentThread()
        return TMERequestManager(sender, h.parser) { check(Thread.currentThread() === main) }
    }

    @Test fun retrySuppressesOldSuccessAndOldErrorAndDuplicates() {
        TMEAsyncTestHarness().use { h ->
            var nextId = 0
            val options = mutableListOf<String>()
            val manager = manager(h, TMERequestSender { options.add(it); "id-${++nextId}" })
            val listener = TMERecordingDelegate()
            manager.delegate = listener
            assertEquals("id-1", manager.sendRequest(tmeRequest()))
            manager.onExtResponse("id-1", tmeRequest(), 200, emptySongsResponse)
            assertEquals("id-2", manager.sendRequest(tmeRequest()))
            manager.onExtResponse("id-1", tmeRequest(), 500, "bad")
            manager.onExtResponse("id-2", tmeRequest(), 200, emptySongsResponse)
            manager.onExtResponse("id-2", tmeRequest(), 503, "duplicate")
            repeat(4) { h.deliverNext() }
            assertEquals(listOf("id-2"), listener.ids)
            assertTrue(listener.errors.isEmpty())
            assertEquals(listOf(tmeRequest(), tmeRequest()), options)
            manager.close()
        }
    }

    @Test fun currentErrorRetainsOriginalsAndIsDeliveredOnlyOnce() {
        TMEAsyncTestHarness().use { h ->
            val manager = manager(h, TMERequestSender { "current" })
            val listener = TMERecordingDelegate()
            manager.delegate = listener
            manager.sendRequest(tmeRequest())
            val response = """{"code":8,"msg":"denied","data":null}"""
            manager.onExtResponse("current", tmeRequest(), 200, response)
            manager.onExtResponse("current", tmeRequest(), 200, response)
            repeat(2) { h.deliverNext() }
            assertEquals(listOf("current"), listener.ids)
            assertTrue(listener.errors.single() is TMEParseError.BusinessError)
            assertEquals(listOf(tmeRequest() to response), listener.originals)
            manager.close()
        }
    }

    @Test fun stopInvalidatesQueuedResultsAndAllowsAnotherRequest() {
        TMEAsyncTestHarness().use { h ->
            var id = 0
            val manager = manager(h, TMERequestSender { "${++id}" })
            val listener = TMERecordingDelegate()
            manager.delegate = listener
            manager.sendRequest(tmeRequest())
            manager.onExtResponse("1", tmeRequest(), 200, emptySongsResponse)
            manager.stop()
            manager.onExtResponse("1", tmeRequest(), 500, "old failure")
            repeat(2) { h.deliverNext() }
            assertTrue(listener.ids.isEmpty())
            manager.sendRequest(tmeRequest())
            manager.onExtResponse("2", tmeRequest(), 200, emptySongsResponse)
            h.deliverNext()
            assertEquals(listOf("2"), listener.ids)
            manager.close()
        }
    }

    @Test fun rejectedSendInvalidatesPreviousRequest() {
        TMEAsyncTestHarness().use { h ->
            var first = true
            val manager = manager(h, TMERequestSender { if (first) { first = false; "old" } else null })
            val listener = TMERecordingDelegate()
            manager.delegate = listener
            manager.sendRequest(tmeRequest())
            assertNull(manager.sendRequest(tmeRequest()))
            manager.onExtResponse("old", tmeRequest(), 500, "late")
            h.deliverNext()
            assertTrue(listener.ids.isEmpty())
            manager.close()
        }
    }

    @Test fun delegateCanStartAnotherRequestWithoutOldDeliveryClearingIt() {
        TMEAsyncTestHarness().use { h ->
            var id = 0
            val manager = manager(h, TMERequestSender { "${++id}" })
            val seen = mutableListOf<String>()
            val listener = object : TMEParserDelegate {
                override fun onSongs(requestId: String, result: TMESongsResult) {
                    seen.add(requestId)
                    if (requestId == "1") manager.sendRequest(tmeRequest())
                }
            }
            manager.delegate = listener
            manager.sendRequest(tmeRequest())
            manager.onExtResponse("1", tmeRequest(), 200, emptySongsResponse)
            h.deliverNext()
            manager.onExtResponse("2", tmeRequest(), 200, emptySongsResponse)
            h.deliverNext()
            assertEquals(listOf("1", "2"), seen)
            manager.close()
        }
    }

    @Test fun closeSuppressesAcceptedAndLateSDKResponses() {
        TMEAsyncTestHarness().use { h ->
            val manager = manager(h, TMERequestSender { "current" })
            val listener = TMERecordingDelegate()
            manager.delegate = listener
            manager.sendRequest(tmeRequest())
            manager.onExtResponse("current", tmeRequest(), 200, emptySongsResponse)
            manager.close()
            h.deliverNext()
            manager.onExtResponse("current", tmeRequest(), 500, "after close")
            assertTrue(listener.ids.isEmpty())
            assertTrue(h.callbacks.isEmpty())
            assertThrows(IllegalStateException::class.java) { manager.sendRequest(tmeRequest()) }
            manager.close()
        }
    }

    @Test fun requestOperationsRequireUIThreadWhileSDKResponseMayArriveInBackground() {
        TMEAsyncTestHarness().use { h ->
            val manager = manager(h, TMERequestSender { "current" })
            val listener = TMERecordingDelegate()
            manager.delegate = listener
            manager.sendRequest(tmeRequest())
            val background = FutureTask {
                assertThrows(IllegalStateException::class.java) { manager.sendRequest(tmeRequest()) }
                manager.onExtResponse("current", tmeRequest(), 200, emptySongsResponse)
            }
            Thread(background).start()
            background.get(5, TimeUnit.SECONDS)
            h.deliverNext()
            assertEquals(listOf("current"), listener.ids)
            manager.close()
        }
    }
}
