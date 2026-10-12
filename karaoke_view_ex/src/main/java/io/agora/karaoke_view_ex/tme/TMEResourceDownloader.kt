package io.agora.karaoke_view_ex.tme

import java.io.File
import java.io.Closeable
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun interface TMEResourceDownloadTask { fun cancel() }
enum class TMEResourceDownloadError { NETWORK, HTTP, EMPTY_FILE, FILE_IO, CANCELLED }
sealed class TMEResourceDownloadResult {
    object Success : TMEResourceDownloadResult()
    data class Failure(val error: TMEResourceDownloadError, val httpCode: Int? = null) : TMEResourceDownloadResult()
}

/**
 * Writes destination through a temporary file, then atomically renames it. Completion must
 * occur after all writes stop, including when cancelled; it may arrive on any thread.
 * Injected downloaders remain owned by their caller and are never closed by preparation.
 */
interface TMEResourceDownloader {
    fun download(url: String, destination: File, callback: (TMEResourceDownloadResult) -> Unit): TMEResourceDownloadTask
}

internal fun tmeBoundedWorkers(name: String): ExecutorService = ThreadPoolExecutor(
    2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64),
    { task -> Thread(task, name).apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy()
)

/** Streamed HTTP(S) downloader. Cancellation disconnects transport and settles after writes stop. */
class TMEHttpResourceDownloader internal constructor(
    private val workers: ExecutorService,
    private val connectionFactory: (String) -> HttpURLConnection,
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int
) : TMEResourceDownloader, Closeable {
    constructor() : this(tmeBoundedWorkers("TMEResourceDownload"),
        { java.net.URL(it).openConnection() as HttpURLConnection }, 30_000, 30_000)
    private val lifecycleLock = Any()
    private val tasks = mutableSetOf<Download>()
    private var closed = false

    override fun download(url: String, destination: File, callback: (TMEResourceDownloadResult) -> Unit): TMEResourceDownloadTask {
        val task = Download(url, destination, callback)
        synchronized(lifecycleLock) {
            check(!closed) { "Downloader is closed" }
            tasks.add(task)
            try { task.attach(workers.submit(task)) }
            catch (_: RejectedExecutionException) {
                task.finish(TMEResourceDownloadResult.Failure(TMEResourceDownloadError.NETWORK))
            }
        }
        return task
    }

    override fun close() {
        val pending = synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            tasks.toList()
        }
        pending.forEach { it.cancel() }
        workers.shutdown()
    }

    private inner class Download(
        val url: String, val destination: File, val callback: (TMEResourceDownloadResult) -> Unit
    ) : Runnable, TMEResourceDownloadTask {
        private val lock = Any()
        private var future: Future<*>? = null
        private var started = false
        private var finished = false
        private var cancelled = false
        private var connection: HttpURLConnection? = null
        private var input: InputStream? = null
        private var output: FileOutputStream? = null

        fun attach(value: Future<*>) {
            val stop = synchronized(lock) { future = value; cancelled }
            if (stop) value.cancel(true)
        }

        override fun cancel() {
            val state = synchronized(lock) {
                if (finished || cancelled) return
                cancelled = true
                future to started
            }
            closeTransport()
            state.first?.cancel(true)
            if (!state.second) finish(TMEResourceDownloadResult.Failure(TMEResourceDownloadError.CANCELLED))
        }

        override fun run() {
            synchronized(lock) {
                if (cancelled) return
                started = true
            }
            val temporary = File(destination.parentFile, ".${destination.name}.${UUID.randomUUID()}.tmp")
            var result: TMEResourceDownloadResult = TMEResourceDownloadResult.Failure(TMEResourceDownloadError.NETWORK)
            try {
                val http = connectionFactory(url)
                synchronized(lock) { connection = http }
                checkCancelled()
                http.connectTimeout = connectTimeoutMs
                http.readTimeout = readTimeoutMs
                val code = http.responseCode
                if (code !in 200..299) {
                    result = TMEResourceDownloadResult.Failure(TMEResourceDownloadError.HTTP, code)
                } else {
                    val source = http.inputStream
                    synchronized(lock) { input = source }
                    checkCancelled()
                    val target = try { FileOutputStream(temporary) }
                    catch (_: IOException) { throw FileFailure() }
                    synchronized(lock) { output = target }
                    checkCancelled()
                    val buffer = ByteArray(16 * 1024)
                    var size = 0L
                    while (true) {
                        checkCancelled()
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        try { target.write(buffer, 0, read) }
                        catch (_: IOException) { throw FileFailure() }
                        size += read
                    }
                    try { target.flush(); target.close() }
                    catch (_: IOException) { throw FileFailure() }
                    synchronized(lock) {
                        output = null
                        checkCancelled()
                        result = if (size == 0L) TMEResourceDownloadResult.Failure(TMEResourceDownloadError.EMPTY_FILE)
                        else if (!temporary.renameTo(destination)) TMEResourceDownloadResult.Failure(TMEResourceDownloadError.FILE_IO)
                        else TMEResourceDownloadResult.Success
                    }
                }
            } catch (_: FileFailure) {
                result = TMEResourceDownloadResult.Failure(TMEResourceDownloadError.FILE_IO)
            } catch (_: Exception) {
                result = TMEResourceDownloadResult.Failure(TMEResourceDownloadError.NETWORK)
            } finally {
                closeTransport()
                try { temporary.delete() } catch (_: Exception) { }
                finish(result)
            }
        }

        private fun checkCancelled() {
            if (synchronized(lock) { cancelled } || Thread.currentThread().isInterrupted) throw IOException()
        }

        private fun closeTransport() {
            val resources = synchronized(lock) {
                val snapshot = Triple(input, output, connection)
                input = null; output = null; connection = null
                snapshot
            }
            try { resources.first?.close() } catch (_: Exception) { }
            try { resources.second?.close() } catch (_: Exception) { }
            try { resources.third?.disconnect() } catch (_: Exception) { }
        }

        fun finish(result: TMEResourceDownloadResult) {
            val completion = synchronized(lock) {
                if (finished) return
                finished = true
                if (cancelled) TMEResourceDownloadResult.Failure(TMEResourceDownloadError.CANCELLED) else result
            }
            synchronized(lifecycleLock) { tasks.remove(this) }
            callback(completion)
        }
    }

    private class FileFailure : IOException()
}
