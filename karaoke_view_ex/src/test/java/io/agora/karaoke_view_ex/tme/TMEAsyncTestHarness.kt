package io.agora.karaoke_view_ex.tme

import org.junit.Assert.*
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class TMEAsyncTestHarness : AutoCloseable {
    val worker = Executors.newSingleThreadExecutor { Thread(it, "test-tme-decoder") }
    val callbacks = LinkedBlockingQueue<Pair<Thread, Runnable>>()
    val parser = TMEParser(worker) { callbacks.add(Thread.currentThread() to it) }

    fun deliverNext() {
        val callback = callbacks.poll(5, TimeUnit.SECONDS)
        assertNotNull("Decoder did not post a callback", callback)
        assertNotEquals(Thread.currentThread(), callback!!.first)
        callback.second.run()
    }

    override fun close() {
        parser.close()
        assertTrue("Decoder did not terminate", worker.awaitTermination(5, TimeUnit.SECONDS))
    }
}

internal class TMERecordingDelegate : TMEParserDelegate {
    val ids = mutableListOf<String>()
    val actions = mutableListOf<String>()
    val results = mutableListOf<Any>()
    val errors = mutableListOf<TMEParseError>()
    val originals = mutableListOf<Pair<String, String>>()
    val threads = mutableListOf<Thread>()

    private fun success(id: String, action: String, result: Any) {
        ids.add(id)
        actions.add(action)
        results.add(result)
        threads.add(Thread.currentThread())
    }

    override fun onSongs(requestId: String, result: TMESongsResult) = success(requestId, "songs", result)
    override fun onSearchSongs(requestId: String, result: TMESearchSongsResult) = success(requestId, "search-song", result)
    override fun onSongInfo(requestId: String, result: TMESongInfoResult) = success(requestId, "song-info", result)
    override fun onSongUrl(requestId: String, result: TMESongUrlResult) = success(requestId, "song-url", result)
    override fun onSonglistPage(requestId: String, result: TMEPageResult) = success(requestId, "songlist-page", result)
    override fun onSonglistDetail(requestId: String, result: TMEDetailResult) = success(requestId, "songlist-detail", result)
    override fun onRanklistPage(requestId: String, result: TMEPageResult) = success(requestId, "ranklist-page", result)
    override fun onRanklistDetail(requestId: String, result: TMEDetailResult) = success(requestId, "ranklist-detail", result)
    override fun onParseError(requestId: String, jsonOption: String, responseBody: String, error: TMEParseError) {
        ids.add(requestId)
        actions.add("error")
        errors.add(error)
        originals.add(jsonOption to responseBody)
        threads.add(Thread.currentThread())
    }
}

internal fun tmeRequest(action: String = "songs") = """{"vendorId":2,"actionType":"$action"}"""
internal val emptySongsResponse = """{"code":0,"data":{"songList":[]}}"""
