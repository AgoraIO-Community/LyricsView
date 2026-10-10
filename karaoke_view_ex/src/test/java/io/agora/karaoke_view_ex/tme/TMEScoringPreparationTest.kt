package io.agora.karaoke_view_ex.tme

import com.google.gson.JsonParser
import io.agora.karaoke_view_ex.internal.model.TMELyricModel
import io.agora.karaoke_view_ex.model.LyricModel
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

class TMEScoringPreparationTest {
    @Test fun originalIdIsStructuredAndRealParserStartsBothExactResources() = Fixture().use { f ->
        val id = "001\"\\\n曲"
        f.prepare(id)
        val request = JsonParser.parseString(f.option).asJsonObject
        assertEquals(2, request["vendorId"].asInt)
        assertEquals("song-info", request["actionType"].asString)
        assertEquals(id, request["actionParameter"].asJsonObject["songIdListStr"].asString)
        assertFalse(f.preparation.handleExtResponse("list", f.option, 200, "{}"))
        f.response(song(id, "https://Host/Pitch?Signature=AbC", "https://Host/Lyric?Signature=XyZ"))
        f.until { f.downloader.pending.size == 2 }
        assertEquals(setOf("https://Host/Pitch?Signature=AbC", "https://Host/Lyric?Signature=XyZ"), f.downloader.pending.map { it.url }.toSet())
        assertEquals(setOf("song_pitch.json", "song_lyric.lrc"), f.downloader.pending.map { it.file.name }.toSet())
        assertEquals(1, f.downloader.pending.map { it.file.parentFile }.toSet().size)
        assertTrue(f.preparation.handleExtResponse("req", f.option, 200, "invalid duplicate"))
        f.downloader.complete(0); f.downloader.complete(1)
        f.until { f.results.size == 1 }
        assertSame(f.model, f.results.single().lyricModel)
        assertEquals(0, f.results.single().scorableLineCount)
        assertTrue(f.stages.containsAll(TMEScoringPreparationStage.values().toList()))
        assertNotEquals(f.main, f.parseThread)
        assertTrue(f.callbackThreads.all { it == f.main })
    }

    @Test fun selectionRejectsWrongSongMissingPitchAndNonExactLrc() {
        val cases = listOf(
            song("other") to TMEScoringPreparationErrorCategory.SONG_NOT_FOUND,
            song("s", " ") to TMEScoringPreparationErrorCategory.MISSING_RESOURCE,
            song("s", type = "LRC") to TMEScoringPreparationErrorCategory.MISSING_RESOURCE,
            song("s", type = "krc") to TMEScoringPreparationErrorCategory.MISSING_RESOURCE,
            song("s", lyric = " ") to TMEScoringPreparationErrorCategory.MISSING_RESOURCE,
            song("s", pitch = "file:///secret") to TMEScoringPreparationErrorCategory.INVALID_URL,
            song("s", lyric = "https:///no-host") to TMEScoringPreparationErrorCategory.INVALID_URL
        )
        for ((body, category) in cases) Fixture().use { f ->
            f.prepare(); f.response(body); f.until { f.errors.size == 1 }
            assertEquals(category, f.errors.single().category)
            assertTrue(f.downloader.pending.isEmpty())
        }
    }

    @Test fun responseErrorsAreTypedAndExcludeVendorMessage() {
        val cases = listOf(
            Triple(403, "signed-secret", TMEScoringPreparationErrorCategory.HTTP),
            Triple(200, """{"code":432,"msg":"signed-secret"}""", TMEScoringPreparationErrorCategory.BUSINESS),
            Triple(200, "broken signed-secret", TMEScoringPreparationErrorCategory.INVALID_RESPONSE)
        )
        for ((code, body, category) in cases) Fixture().use { f ->
            f.prepare(); f.response(body, code); f.until { f.errors.isNotEmpty() }
            assertEquals(category, f.errors.single().category)
            assertFalse(f.errors.single().toString().contains("signed-secret"))
            if (code == 403) assertEquals(403, f.errors.single().httpCode)
            if (category == TMEScoringPreparationErrorCategory.BUSINESS) assertEquals(432, f.errors.single().businessCode)
        }
    }

    @Test fun downloadFailureCancelsPeerAndLateDuplicateCannotComplete() = Fixture().use { f ->
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
        f.downloader.fail(0)
        f.until { f.errors.size == 1 }
        assertTrue(f.downloader.pending[1].cancelled)
        f.downloader.complete(1); f.downloader.complete(1)
        f.drain()
        assertEquals(1, f.errors.size)
        assertTrue(f.results.isEmpty())
        f.until { !f.downloader.pending[0].file.parentFile!!.exists() }
    }

    @Test fun cancellationWaitsForWriterBeforeOwnDirectoryCleanupAndNeverCompletes() = Fixture().use { f ->
        val unrelated = File(f.root, "unrelated").apply { mkdirs() }
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
        val own = f.downloader.pending[0].file.parentFile!!
        f.preparation.cancel()
        assertTrue(f.downloader.pending.all { it.cancelled })
        assertTrue(own.exists())
        f.downloader.complete(0); f.downloader.complete(1)
        f.until { !own.exists() }
        assertTrue(unrelated.exists())
        assertTrue(f.errors.isEmpty()); assertTrue(f.results.isEmpty())
        assertFalse(f.preparation.handleExtResponse("req", f.option, 200, song("s")))
    }

    @Test fun sameSongRetryAndReentrantStageInvalidateOldWork() = Fixture().use { f ->
        f.stageHook = { if (it == TMEScoringPreparationStage.LYRIC_DOWNLOADING) f.preparation.cancel() }
        f.prepare(); f.response(song("s")); f.until { f.stages.contains(TMEScoringPreparationStage.LYRIC_DOWNLOADING) }
        assertTrue(f.downloader.pending.isEmpty())
        f.stageHook = null
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
        f.preparation.prepare("s", TMERequestSender { "next" })
        assertTrue(f.downloader.pending.all { it.cancelled })
        f.downloader.complete(0); f.downloader.complete(1); f.drain()
        assertTrue(f.results.isEmpty()); assertTrue(f.errors.isEmpty())
    }

    @Test fun timeoutsAreDeterministicAndCancelledCallbacksStayInvalid() {
        for ((timeout, category) in listOf(30L to TMEScoringPreparationErrorCategory.QUERY_TIMEOUT, 120L to TMEScoringPreparationErrorCategory.OVERALL_TIMEOUT)) Fixture().use { f ->
            f.prepare(); f.clock.fire(timeout); assertEquals(category, f.errors.single().category)
            f.response(song("s")); f.drain(); assertTrue(f.downloader.pending.isEmpty())
        }
        Fixture().use { f ->
            f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
            f.clock.fire(60); assertEquals(TMEScoringPreparationErrorCategory.DOWNLOAD_TIMEOUT, f.errors.single().category)
            f.downloader.complete(0); f.downloader.complete(1)
        }
    }

    @Test fun emptyFilesAndNullModelFailOnce() {
        Fixture().use { f ->
            f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
            f.downloader.complete(0, ""); f.downloader.complete(1)
            f.until { f.errors.size == 1 }
            assertEquals(TMEScoringPreparationErrorCategory.EMPTY_FILE, f.errors.single().category)
        }
        Fixture().use { f ->
            f.parseResult = null
            f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
            f.downloader.complete(0); f.downloader.complete(1)
            f.until { f.errors.size == 1 }
            assertEquals(TMEScoringPreparationErrorCategory.PARSE, f.errors.single().category)
        }
    }

    @Test fun rejectedSendAndCloseAreTerminalWithoutClosingInjectedDownloader() = Fixture().use { f ->
        f.preparation.prepare("s", TMERequestSender { null })
        assertEquals(TMEScoringPreparationErrorCategory.REQUEST_REJECTED, f.errors.single().category)
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
        f.preparation.close(); f.preparation.close()
        assertNull(f.preparation.listener)
        f.downloader.complete(0); f.downloader.complete(1); f.drain()
        assertEquals(1, f.errors.size); assertTrue(f.results.isEmpty()); assertFalse(f.downloader.closed)
    }

    @Test fun queuedOldDecodeCannotSatisfyRetryEvenWhenSdkReusesRequestId() = Fixture().use { f ->
        f.prepare()
        f.response(song("s"))
        val oldCallback = f.queue.poll(5, TimeUnit.SECONDS)!!
        f.prepare("next")
        oldCallback.run()
        assertTrue(f.errors.isEmpty())
        assertTrue(f.downloader.pending.isEmpty())
        f.response(song("next"))
        f.until { f.downloader.pending.size == 2 }
    }

    @Test fun synchronousSenderResponseIsDeferredUntilRequestIdIsAssigned() = Fixture().use { f ->
        f.preparation.prepare("s", TMERequestSender { option ->
            f.option = option
            assertTrue(f.preparation.handleExtResponse("req", option, 200, song("s")))
            "req"
        })
        f.until { f.downloader.pending.size == 2 }
    }

    @Test fun successNotificationCanPrepareAnotherSongWithoutOldCleanupAffectingIt() = Fixture().use { f ->
        f.successHook = { f.prepare("next") }
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
        f.downloader.complete(0); f.downloader.complete(1)
        f.until { f.results.size == 1 }
        f.response(song("next")); f.until { f.downloader.pending.size == 4 }
        assertNotEquals(f.downloader.pending[0].file.parentFile, f.downloader.pending[2].file.parentFile)
        assertTrue(f.downloader.pending[2].file.parentFile!!.exists())
    }

    @Test fun failureNotificationCanPrepareAnotherSong() = Fixture().use { f ->
        f.failureHook = { f.prepare("next") }
        f.prepare(); f.response(song("other")); f.until { f.errors.size == 1 }
        f.response(song("next")); f.until { f.downloader.pending.size == 2 }
    }

    @Test fun cancelDuringParsingWaitsUntilParserStopsAndSuppressesItsLateSuccess() = Fixture().use { f ->
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.parseHook = { _, _ -> started.countDown(); release.await(); f.model }
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
        f.downloader.complete(0); f.downloader.complete(1)
        f.until { started.count == 0L }
        val own = f.downloader.pending[0].file.parentFile!!
        f.preparation.close()
        assertTrue(own.exists())
        release.countDown()
        f.until { !own.exists() }
        assertTrue(f.results.isEmpty()); assertTrue(f.errors.isEmpty())
    }

    @Test fun realToneParserCountsOnlyScorableLinesAndPreservesDisplayMetadata() = Fixture().use { f ->
        f.parseHook = io.agora.karaoke_view_ex.internal.lyric.parse.TMEToneParser::parse
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
        val lyric = "[ti:歌名]\n[ar:歌手]\n[00:00.00]歌名 - 歌手\n[00:01.00]作词：某人\n[00:02.00]演唱一\n[00:03.00]\n[00:04.00]没有音符\n[00:05.00]演唱二"
        val pitch = """[{"st":500,"d":100,"p":40},{"st":1500,"d":100,"p":40},{"st":2200,"d":1800,"p":40},{"st":5000,"d":200,"p":50}]"""
        f.downloader.complete(0, lyric); f.downloader.complete(1, pitch)
        f.until { f.results.size == 1 }
        assertEquals(2, f.results.single().scorableLineCount)
        assertEquals(6, f.results.single().lyricModel.lines.size)
    }

    @Test fun modelParserExceptionAndCacheDirectoryFailureAreSanitized() {
        Fixture().use { f ->
            f.parseHook = { _, _ -> throw IllegalStateException("secret signed URL") }
            f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
            f.downloader.complete(0); f.downloader.complete(1); f.until { f.errors.size == 1 }
            assertEquals(TMEScoringPreparationErrorCategory.PARSE, f.errors.single().category)
            assertFalse(f.errors.single().toString().contains("secret"))
        }
        Fixture().use { f ->
            File(f.root, "tme-scoring").writeText("belongs to caller")
            f.prepare(); f.response(song("s")); f.until { f.errors.size == 1 }
            assertEquals(TMEScoringPreparationErrorCategory.FILE_IO, f.errors.single().category)
            assertEquals("belongs to caller", File(f.root, "tme-scoring").readText())
        }
    }

    @Test fun downloadFileIoErrorIsTypedAndParseReentryCannotStartOldParser() = Fixture().use { f ->
        f.stageHook = { if (it == TMEScoringPreparationStage.PARSING) f.preparation.cancel() }
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
        f.downloader.complete(0); f.downloader.complete(1)
        f.until { f.stages.contains(TMEScoringPreparationStage.PARSING) }
        assertNull(f.parseThread)
        f.stageHook = null
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 4 }
        f.downloader.fail(2, TMEResourceDownloadError.FILE_IO)
        f.until { f.errors.size == 1 }
        assertEquals(TMEScoringPreparationErrorCategory.FILE_IO, f.errors.single().category)
    }

    @Test fun firstNonblankExactLrcIsSelectedAcrossMultipleSongResults() = Fixture().use { f ->
        val gson = com.google.gson.Gson()
        val songs = listOf(
            mapOf("songId" to "other", "songName" to "other", "pitchUrl" to "https://wrong/p", "lrcList" to listOf(mapOf("type" to "lrc", "url" to "https://wrong/l"))),
            mapOf("songId" to "s", "songName" to "song", "pitchUrl" to "HTTP://Host/p?Token=Case", "lrcList" to listOf(mapOf("type" to "krc", "url" to "https://host/k"), mapOf("type" to "lrc", "url" to " "), mapOf("type" to "lrc", "url" to "https://host/first"), mapOf("type" to "lrc", "url" to "https://host/second")))
        )
        f.prepare(); f.response(gson.toJson(mapOf("code" to 0, "data" to mapOf("songList" to songs))))
        f.until { f.downloader.pending.size == 2 }
        assertEquals(listOf("https://host/first", "HTTP://Host/p?Token=Case"), f.downloader.pending.map { it.url })
    }

    @Test fun downloaderSuccessWithoutCommittedFileIsFileIoFailure() = Fixture().use { f ->
        f.prepare(); f.response(song("s")); f.until { f.downloader.pending.size == 2 }
        val pending = f.downloader.pending[0]
        pending.completed = true
        pending.callback(TMEResourceDownloadResult.Success)
        f.until { f.errors.size == 1 }
        assertEquals(TMEScoringPreparationErrorCategory.FILE_IO, f.errors.single().category)
    }

    @Test fun cancellationAndCloseDeferCleanupWhenBoundedWorkerQueueIsFull() {
        for (close in listOf(false, true)) Fixture().use { f ->
            f.prepare()
            val started = CountDownLatch(2)
            val release = CountDownLatch(1)
            val drained = CountDownLatch(64)
            repeat(2) { f.workers.execute { started.countDown(); release.await() } }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repeat(64) { f.workers.execute { drained.countDown() } }
            try { if (close) f.preparation.close() else f.preparation.cancel() } finally { release.countDown() }
            assertTrue(drained.await(5, TimeUnit.SECONDS))
            f.clock.fire(50)
            assertTrue(f.errors.isEmpty()); assertTrue(f.results.isEmpty())
        }
    }

    private class Fixture : java.io.Closeable {
        val root = Files.createTempDirectory("tme-preparation-test").toFile()
        val main = Thread.currentThread()
        val queue = LinkedBlockingQueue<Runnable>()
        val workers = tmeBoundedWorkers("test-preparation-worker")
        val decoders = mutableListOf<java.util.concurrent.ExecutorService>()
        val downloader = FakeDownloader()
        val clock = FakeClock()
        val model = TMELyricModel()
        var parseResult: LyricModel? = model
        var parseThread: Thread? = null
        var parseHook: ((String, String) -> LyricModel?)? = null
        var option = ""
        val stages = mutableListOf<TMEScoringPreparationStage>()
        val errors = mutableListOf<TMEScoringPreparationError>()
        val results = mutableListOf<TMEScoringPreparationResult>()
        val callbackThreads = mutableListOf<Thread>()
        var stageHook: ((TMEScoringPreparationStage) -> Unit)? = null
        var successHook: (() -> Unit)? = null
        var failureHook: (() -> Unit)? = null
        val preparation = TMEScoringPreparation(root, downloader, TMEScoringPreparationTimeouts(30, 60, 120), workers,
            { val decoder = Executors.newSingleThreadExecutor(); decoders.add(decoder); TMEParser(decoder) { queue.add(it) } }, { check(Thread.currentThread() == main) },
            { queue.add(it) }, clock::schedule, { pitch, lyric -> parseThread = Thread.currentThread(); parseHook?.invoke(pitch, lyric) ?: if (parseHook == null) parseResult else null })
        init {
            preparation.listener = object : TMEScoringPreparationListener {
                override fun onStage(stage: TMEScoringPreparationStage) { callbackThreads.add(Thread.currentThread()); stages.add(stage); stageHook?.invoke(stage) }
                override fun onSuccess(result: TMEScoringPreparationResult) { callbackThreads.add(Thread.currentThread()); results.add(result); successHook?.invoke() }
                override fun onFailure(error: TMEScoringPreparationError) { callbackThreads.add(Thread.currentThread()); errors.add(error); failureHook?.invoke() }
            }
        }
        fun prepare(id: String = "s") = preparation.prepare(id, TMERequestSender { option = it; "req" })
        fun response(body: String, code: Int = 200) { assertTrue(preparation.handleExtResponse("req", option, code, body)) }
        fun until(condition: () -> Boolean) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!condition() && System.nanoTime() < end) queue.poll(20, TimeUnit.MILLISECONDS)?.run()
            assertTrue("Expected state was not reached", condition())
        }
        fun drain() { while (true) (queue.poll() ?: break).run() }
        override fun close() {
            preparation.close()
            downloader.pending.filter { !it.completed }.forEach { it.callback(TMEResourceDownloadResult.Failure(TMEResourceDownloadError.CANCELLED)); it.completed = true }
            drain()
            workers.shutdown(); decoders.forEach { it.shutdown() }
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS)); decoders.forEach { assertTrue(it.awaitTermination(5, TimeUnit.SECONDS)) }
            drain(); root.deleteRecursively()
        }
    }

    private class FakeClock {
        data class Timer(val delay: Long, val action: Runnable, var cancelled: Boolean = false)
        val timers = mutableListOf<Timer>()
        fun schedule(delay: Long, action: Runnable): TMEResourceDownloadTask {
            val timer = Timer(delay, action); timers.add(timer)
            return TMEResourceDownloadTask { timer.cancelled = true }
        }
        fun fire(delay: Long) { timers.toList().filter { it.delay == delay && !it.cancelled }.forEach { it.action.run() } }
    }

    private class FakeDownloader : TMEResourceDownloader, java.io.Closeable {
        data class Pending(val url: String, val file: File, val callback: (TMEResourceDownloadResult) -> Unit, var cancelled: Boolean = false, var completed: Boolean = false)
        val pending = mutableListOf<Pending>()
        var closed = false
        override fun download(url: String, destination: File, callback: (TMEResourceDownloadResult) -> Unit): TMEResourceDownloadTask {
            val task = Pending(url, destination, callback); pending.add(task)
            return TMEResourceDownloadTask { task.cancelled = true }
        }
        fun complete(index: Int, content: String = "data") {
            val task = pending[index]; if (!task.completed) task.file.writeText(content); task.completed = true
            task.callback(TMEResourceDownloadResult.Success)
        }
        fun fail(index: Int, error: TMEResourceDownloadError = TMEResourceDownloadError.NETWORK) { pending[index].completed = true; pending[index].callback(TMEResourceDownloadResult.Failure(error, 503)) }
        override fun close() { closed = true }
    }

    companion object {
        private fun song(id: String, pitch: String = "https://host/p", lyric: String = "https://host/l", type: String = "lrc"): String {
            val gson = com.google.gson.Gson()
            return gson.toJson(mapOf("code" to 0, "data" to mapOf("songList" to listOf(mapOf("songId" to id, "songName" to "name", "pitchUrl" to pitch, "lrcList" to listOf(mapOf("type" to type, "url" to lyric)))))))
        }
    }
}
