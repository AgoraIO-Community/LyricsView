package io.agora.karaoke_view_ex.tme

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class TMEResourceDownloaderTest {
    @Test fun streamsIntoTemporaryFileAndCommitsOnlyCompleteNonemptyResponse() {
        val root = Files.createTempDirectory("tme-download-test").toFile()
        val connection = FakeConnection(200, ByteArrayInputStream(ByteArray(40_000) { (it % 251).toByte() }))
        val workers = Executors.newFixedThreadPool(2)
        val results = LinkedBlockingQueue<TMEResourceDownloadResult>()
        val destination = File(root, "song_pitch.json")
        val downloader = TMEHttpResourceDownloader(workers, { connection }, 10, 20)
        try {
            downloader.download("https://host/signed?Key=Case", destination) { results.add(it) }
            assertEquals(TMEResourceDownloadResult.Success, results.poll(5, TimeUnit.SECONDS))
            assertEquals(40_000L, destination.length())
            assertEquals(listOf(destination), root.listFiles()!!.toList())
            assertTrue(connection.disconnected)
            assertEquals(10, connection.connectTimeout); assertEquals(20, connection.readTimeout)
        } finally { downloader.close(); workers.awaitTermination(5, TimeUnit.SECONDS); root.deleteRecursively() }
    }

    @Test fun httpAndEmptyErrorsDoNotCommitFilesOrExposeRawData() {
        for ((code, body, error) in listOf(Triple(403, "secret", TMEResourceDownloadError.HTTP), Triple(200, "", TMEResourceDownloadError.EMPTY_FILE))) {
            val root = Files.createTempDirectory("tme-download-test").toFile()
            val workers = Executors.newFixedThreadPool(2)
            val results = LinkedBlockingQueue<TMEResourceDownloadResult>()
            val connection = FakeConnection(code, ByteArrayInputStream(body.toByteArray()))
            val downloader = TMEHttpResourceDownloader(workers, { connection }, 10, 20)
            try {
                downloader.download("https://host/secret", File(root, "song_lyric.lrc")) { results.add(it) }
                val failure = results.poll(5, TimeUnit.SECONDS) as TMEResourceDownloadResult.Failure
                assertEquals(error, failure.error)
                assertFalse(failure.toString().contains("secret")); assertTrue(root.listFiles()!!.isEmpty())
                assertTrue(connection.disconnected)
            } finally { downloader.close(); workers.awaitTermination(5, TimeUnit.SECONDS); root.deleteRecursively() }
        }
    }

    @Test fun cancellationClosesBlockedStreamAndCompletesAfterWriterStops() {
        val root = Files.createTempDirectory("tme-download-test").toFile()
        val stream = BlockingStream()
        val connection = FakeConnection(200, stream)
        val workers = Executors.newFixedThreadPool(2)
        val results = LinkedBlockingQueue<TMEResourceDownloadResult>()
        val downloader = TMEHttpResourceDownloader(workers, { connection }, 10, 20)
        try {
            val task = downloader.download("https://host/p", File(root, "song_pitch.json")) { results.add(it) }
            assertTrue(stream.started.await(5, TimeUnit.SECONDS))
            task.cancel(); task.cancel()
            assertEquals(TMEResourceDownloadError.CANCELLED, (results.poll(5, TimeUnit.SECONDS) as TMEResourceDownloadResult.Failure).error)
            assertTrue(stream.closed); assertTrue(connection.disconnected)
            assertTrue(root.listFiles()!!.isEmpty()); assertTrue(results.isEmpty())
        } finally { downloader.close(); workers.awaitTermination(5, TimeUnit.SECONDS); root.deleteRecursively() }
    }

    @Test fun cancellingQueuedTaskCompletesWithoutOpeningConnection() {
        val root = Files.createTempDirectory("tme-download-test").toFile()
        val workers = Executors.newSingleThreadExecutor()
        val release = CountDownLatch(1)
        workers.submit { release.await() }
        val results = LinkedBlockingQueue<TMEResourceDownloadResult>()
        var opened = false
        val downloader = TMEHttpResourceDownloader(workers, { opened = true; FakeConnection(200, ByteArrayInputStream(byteArrayOf(1))) }, 10, 20)
        try {
            downloader.download("https://host/p", File(root, "song_pitch.json")) { results.add(it) }.cancel()
            assertEquals(TMEResourceDownloadError.CANCELLED, (results.poll(5, TimeUnit.SECONDS) as TMEResourceDownloadResult.Failure).error)
            assertFalse(opened)
        } finally { release.countDown(); downloader.close(); workers.awaitTermination(5, TimeUnit.SECONDS); root.deleteRecursively() }
    }

    @Test fun twoDownloadsRunConcurrentlyAndCloseSettlesBothWriters() {
        val root = Files.createTempDirectory("tme-download-test").toFile()
        val streams = listOf(BlockingStream(), BlockingStream())
        val workers = Executors.newFixedThreadPool(2)
        val results = LinkedBlockingQueue<TMEResourceDownloadResult>()
        val downloader = TMEHttpResourceDownloader(workers, { FakeConnection(200, streams[if (it.endsWith("pitch")) 0 else 1]) }, 10, 20)
        try {
            downloader.download("https://host/pitch", File(root, "song_pitch.json")) { results.add(it) }
            downloader.download("https://host/lyric", File(root, "song_lyric.lrc")) { results.add(it) }
            streams.forEach { assertTrue(it.started.await(5, TimeUnit.SECONDS)) }
            downloader.close(); downloader.close()
            repeat(2) { assertEquals(TMEResourceDownloadError.CANCELLED, (results.poll(5, TimeUnit.SECONDS) as TMEResourceDownloadResult.Failure).error) }
            assertTrue(streams.all { it.closed }); assertTrue(root.listFiles()!!.isEmpty())
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
        } finally { downloader.close(); root.deleteRecursively() }
    }

    @Test fun networkAndFileIoFailuresAreTypedAndCleanTemporaryFiles() {
        val root = Files.createTempDirectory("tme-download-test").toFile()
        val workers = Executors.newFixedThreadPool(2)
        val results = LinkedBlockingQueue<TMEResourceDownloadResult>()
        val downloader = TMEHttpResourceDownloader(workers, { url ->
            if (url.endsWith("network")) throw java.io.IOException("secret signed URL")
            FakeConnection(200, ByteArrayInputStream(byteArrayOf(1)))
        }, 10, 20)
        try {
            downloader.download("https://host/network", File(root, "network")) { results.add(it) }
            val network = results.poll(5, TimeUnit.SECONDS) as TMEResourceDownloadResult.Failure
            assertEquals(TMEResourceDownloadError.NETWORK, network.error)
            assertFalse(network.toString().contains("secret"))
            downloader.download("https://host/file", File(root, "missing/song_pitch.json")) { results.add(it) }
            assertEquals(TMEResourceDownloadError.FILE_IO, (results.poll(5, TimeUnit.SECONDS) as TMEResourceDownloadResult.Failure).error)
            assertTrue(root.listFiles()!!.isEmpty())
        } finally { downloader.close(); workers.awaitTermination(5, TimeUnit.SECONDS); root.deleteRecursively() }
    }

    private class FakeConnection(private val code: Int, private val stream: InputStream) : HttpURLConnection(URL("https://host/p")) {
        @Volatile var disconnected = false
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream(): InputStream = stream
    }
    private class BlockingStream : InputStream() {
        val started = CountDownLatch(1)
        private val release = CountDownLatch(1)
        @Volatile var closed = false
        override fun read(): Int { started.countDown(); release.await(); return -1 }
        override fun close() { closed = true; release.countDown() }
    }
}
