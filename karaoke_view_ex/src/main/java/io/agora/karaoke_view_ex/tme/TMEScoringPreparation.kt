package io.agora.karaoke_view_ex.tme

import android.os.Handler
import android.os.Looper
import com.google.gson.JsonObject
import io.agora.karaoke_view_ex.KaraokeView
import io.agora.karaoke_view_ex.internal.model.TMELyricModel
import io.agora.karaoke_view_ex.model.LyricModel
import java.io.Closeable
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/**
 * Prepares one song's scoring data without owning any SDK object or Context. Entry points,
 * listener changes and notifications run on main; network, files and model parsing do not.
 * Keep one instance for the scoring flow and use a separate request manager for song lists.
 */
class TMEScoringPreparation internal constructor(
    private val cacheRoot: File, private val downloader: TMEResourceDownloader,
    private val timeouts: TMEScoringPreparationTimeouts, private val workers: ExecutorService,
    private val parserFactory: () -> TMEParser, private val requireMainThread: () -> Unit,
    private val postCallback: (Runnable) -> Unit,
    private val schedule: (Long, Runnable) -> TMEResourceDownloadTask,
    private val parseModel: (String, String) -> LyricModel?
) : Closeable {
    @JvmOverloads constructor(
        cacheRoot: File, downloader: TMEResourceDownloader? = null,
        timeouts: TMEScoringPreparationTimeouts = TMEScoringPreparationTimeouts()
    ) : this(cacheRoot, downloader, timeouts, tmeBoundedWorkers("TMEScoringPreparation"))

    private constructor(cacheRoot: File, downloader: TMEResourceDownloader?,
                        timeouts: TMEScoringPreparationTimeouts, workers: ExecutorService)
        : this(cacheRoot, downloader ?: TMEHttpResourceDownloader(), timeouts, workers,
        { TMEParser(TMEParserWorkerLease(workers), TMEMainThread::post) }, TMEMainThread::requireCurrent,
        TMEMainThread::post, ::scheduleOnMain, KaraokeView::parseTMEToneData) {
        ownsDownloader = downloader == null
    }

    private class Session(val songId: String, val sender: TMERequestSender, val directory: File) {
        @Volatile var terminal = false
        var requestId: String? = null
        var option = ""
        var sending = false
        var responseConsumed = false
        var ioCount = 0
        var cleanupSubmitted = false
        var cleanupAttempts = 0
        val tasks = mutableMapOf<TMEScoringResource, TMEResourceDownloadTask>()
        val settled = mutableSetOf<TMEScoringResource>()
        val ready = mutableSetOf<TMEScoringResource>()
        val timers = mutableListOf<TMEResourceDownloadTask>()
        var queryTimer: TMEResourceDownloadTask? = null
        // TMERequestManager stores its delegate weakly, so each session retains it strongly.
        var delegate: TMEParserDelegate? = null
        var manager: TMERequestManager? = null
        fun file(resource: TMEScoringResource) = File(directory,
            if (resource == TMEScoringResource.PITCH) "song_pitch.json" else "song_lyric.lrc")
    }

    @Volatile private var current: Session? = null
    private val ownedSessions = mutableSetOf<Session>()
    private val pendingCleanupDirectories = mutableSetOf<File>()
    private var closed = false
    private var ownsDownloader = false

    var listener: TMEScoringPreparationListener? = null
        set(value) { requireMainThread(); check(!closed || value == null) { "Preparation is closed" }; field = value }

    /** Cancels previous work. songId retains its original string identity, including zeros. */
    fun prepare(songId: String, sender: TMERequestSender) {
        requireMainThread()
        check(!closed) { "Preparation is closed" }
        cancel()
        val session = Session(songId, sender, File(File(cacheRoot, "tme-scoring"), UUID.randomUUID().toString()))
        current = session
        ownedSessions.add(session)
        val manager = TMERequestManager(sender, parserFactory(), requireMainThread)
        session.manager = manager
        session.delegate = object : TMEParserDelegate {
            override fun onSongInfo(requestId: String, result: TMESongInfoResult) {
                if (active(session)) selectResources(session, result)
            }
            override fun onParseError(requestId: String, jsonOption: String, responseBody: String, error: TMEParseError) {
                if (!active(session)) return
                val safeError = when (error) {
                    is TMEParseError.HttpError -> TMEScoringPreparationError(TMEScoringPreparationErrorCategory.HTTP, httpCode = error.httpCode)
                    is TMEParseError.BusinessError -> TMEScoringPreparationError(TMEScoringPreparationErrorCategory.BUSINESS, businessCode = error.code)
                    else -> TMEScoringPreparationError(TMEScoringPreparationErrorCategory.INVALID_RESPONSE)
                }
                fail(session, safeError)
            }
        }
        manager.delegate = session.delegate
        timer(session, timeouts.overallMs, TMEScoringPreparationErrorCategory.OVERALL_TIMEOUT)
        session.queryTimer = timer(session, timeouts.queryMs, TMEScoringPreparationErrorCategory.QUERY_TIMEOUT)
        if (!stage(session, TMEScoringPreparationStage.QUERYING)) return
        session.option = JsonObject().apply {
            addProperty("vendorId", 2)
            addProperty("actionType", "song-info")
            add("actionParameter", JsonObject().apply { addProperty("songIdListStr", songId) })
        }.toString()
        session.sending = true
        val id = try { manager.sendRequest(session.option) } catch (_: Exception) { null }
        session.sending = false
        if (!active(session)) return
        session.requestId = id
        if (id == null) fail(session, TMEScoringPreparationError(TMEScoringPreparationErrorCategory.REQUEST_REJECTED))
    }

    /** Consume current and duplicate scoring responses; return false for another flow. */
    fun handleExtResponse(requestId: String, jsonOption: String, httpCode: Int, responseBody: String): Boolean {
        requireMainThread()
        val session = current ?: return false
        if (closed) return false
        // A sender may synchronously invoke MCC callbacks before returning its request ID.
        if (session.sending && jsonOption == session.option) {
            postCallback(Runnable { if (current === session) handleExtResponse(requestId, jsonOption, httpCode, responseBody) })
            return true
        }
        if (session.requestId != requestId) return false
        if (session.terminal || session.responseConsumed) return true
        session.responseConsumed = true
        session.queryTimer?.cancel()
        try { session.manager?.onExtResponse(requestId, jsonOption, httpCode, responseBody) }
        catch (_: RejectedExecutionException) {
            fail(session, TMEScoringPreparationError(TMEScoringPreparationErrorCategory.INVALID_RESPONSE))
        }
        return true
    }

    /** Invalidates all pending notifications, cancels transport, and emits no completion. */
    fun cancel() {
        requireMainThread()
        val session = current ?: return
        current = null
        terminate(session)
    }

    override fun close() {
        requireMainThread()
        if (closed) return
        cancel()
        closed = true
        listener = null
        if (ownsDownloader) (downloader as? Closeable)?.close()
        ownedSessions.toList().forEach { cleanupWhenSettled(it) }
        shutdownWhenSettled()
    }

    private fun active(session: Session) = current === session && !session.terminal

    private fun stage(session: Session, stage: TMEScoringPreparationStage): Boolean {
        requireMainThread()
        if (!active(session)) return false
        listener?.onStage(stage)
        return active(session)
    }

    private fun timer(session: Session, delay: Long, category: TMEScoringPreparationErrorCategory,
                      resource: TMEScoringResource? = null): TMEResourceDownloadTask {
        val task = schedule(delay, Runnable {
            requireMainThread()
            if (active(session)) fail(session, TMEScoringPreparationError(category, resource))
        })
        session.timers.add(task)
        return task
    }

    private fun selectResources(session: Session, result: TMESongInfoResult) {
        val song = result.songList.firstOrNull { it.songId == session.songId }
            ?: return fail(session, TMEScoringPreparationError(TMEScoringPreparationErrorCategory.SONG_NOT_FOUND))
        val pitch = song.pitchUrl?.takeIf { it.isNotBlank() }
        val lyric = song.lrcList?.firstOrNull { it.type == "lrc" && it.url.isNotBlank() }?.url
        if (pitch == null || lyric == null) return fail(session, TMEScoringPreparationError(TMEScoringPreparationErrorCategory.MISSING_RESOURCE))
        for ((resource, url) in listOf(TMEScoringResource.LYRIC to lyric, TMEScoringResource.PITCH to pitch)) {
            if (!validUrl(url)) return fail(session, TMEScoringPreparationError(TMEScoringPreparationErrorCategory.INVALID_URL, resource))
        }
        io(session, { if (active(session) && !session.directory.mkdirs() && !session.directory.isDirectory) throw java.io.IOException() }) { error ->
            if (!active(session)) return@io
            if (error != null) { fail(session, TMEScoringPreparationError(TMEScoringPreparationErrorCategory.FILE_IO)); return@io }
            if (!stage(session, TMEScoringPreparationStage.LYRIC_DOWNLOADING)) return@io
            if (!stage(session, TMEScoringPreparationStage.PITCH_DOWNLOADING)) return@io
            startDownload(session, TMEScoringResource.LYRIC, lyric)
            if (active(session)) startDownload(session, TMEScoringResource.PITCH, pitch)
        }
    }

    private fun startDownload(session: Session, resource: TMEScoringResource, url: String) {
        session.ioCount++
        val timeout = timer(session, timeouts.downloadMs, TMEScoringPreparationErrorCategory.DOWNLOAD_TIMEOUT, resource)
        val callback: (TMEResourceDownloadResult) -> Unit = { result -> postCallback(Runnable {
            requireMainThread()
            if (session.settled.add(resource)) {
                timeout.cancel()
                session.ioCount--
                if (active(session)) {
                    when (result) {
                        is TMEResourceDownloadResult.Failure -> fail(session, downloadError(resource, result))
                        TMEResourceDownloadResult.Success -> validateFile(session, resource)
                    }
                }
                cleanupWhenSettled(session)
            }
        }) }
        try {
            session.tasks[resource] = downloader.download(url, session.file(resource), callback)
        } catch (_: Exception) {
            callback(TMEResourceDownloadResult.Failure(TMEResourceDownloadError.NETWORK))
        }
    }

    private fun validateFile(session: Session, resource: TMEScoringResource) {
        var empty = false
        io(session, {
            val file = session.file(resource)
            if (!file.isFile) throw java.io.IOException()
            empty = file.length() == 0L
        }) { error ->
            if (!active(session)) return@io
            if (error != null || empty) {
                fail(session, TMEScoringPreparationError(if (error != null) TMEScoringPreparationErrorCategory.FILE_IO else TMEScoringPreparationErrorCategory.EMPTY_FILE, resource))
                return@io
            }
            session.ready.add(resource)
            val completedStage = if (resource == TMEScoringResource.LYRIC) TMEScoringPreparationStage.LYRIC_DOWNLOADED else TMEScoringPreparationStage.PITCH_DOWNLOADED
            if (stage(session, completedStage) && session.ready.size == 2) parse(session)
        }
    }

    private fun parse(session: Session) {
        if (!stage(session, TMEScoringPreparationStage.PARSING)) return
        var model: LyricModel? = null
        io(session, {
            if (active(session)) model = parseModel(session.file(TMEScoringResource.PITCH).path, session.file(TMEScoringResource.LYRIC).path)
        }) { error ->
            if (!active(session)) return@io
            val result = model as? TMELyricModel
            if (error != null || result == null) {
                fail(session, TMEScoringPreparationError(TMEScoringPreparationErrorCategory.PARSE)); return@io
            }
            terminate(session)
            listener?.onSuccess(TMEScoringPreparationResult(result, result.scoringLines.size))
        }
    }

    private fun io(session: Session, work: () -> Unit, completion: (Exception?) -> Unit) {
        session.ioCount++
        try {
            workers.execute {
                val error = try { work(); null } catch (e: Exception) { e }
                postCallback(Runnable {
                    requireMainThread()
                    session.ioCount--
                    completion(error)
                    cleanupWhenSettled(session)
                })
            }
        } catch (e: RejectedExecutionException) {
            session.ioCount--
            completion(e)
            cleanupWhenSettled(session)
        }
    }

    private fun fail(session: Session, error: TMEScoringPreparationError) {
        if (!active(session)) return
        terminate(session)
        listener?.onFailure(error)
    }

    private fun terminate(session: Session) {
        session.terminal = true
        session.manager?.delegate = null
        session.manager?.close()
        session.manager = null
        session.delegate = null
        session.timers.forEach { it.cancel() }
        session.tasks.values.forEach { try { it.cancel() } catch (_: Exception) { /* Already invalidated. */ } }
        cleanupWhenSettled(session)
    }

    private fun cleanupWhenSettled(session: Session) {
        if (session !in ownedSessions || !session.terminal || session.ioCount != 0 || session.cleanupSubmitted) return
        session.cleanupSubmitted = true
        val cleanup = Runnable {
            // The directory is unique and contains only this preparation's files. Never sweep
            // tme-scoring or cacheRoot, which may contain other instances' active preparations.
            val cleaned = deleteOwnedFiles(session.directory)
            postCallback(Runnable {
                session.cleanupAttempts++
                if (cleaned) {
                    pendingCleanupDirectories.remove(session.directory)
                    ownedSessions.remove(session)
                } else pendingCleanupDirectories.add(session.directory)
                session.cleanupSubmitted = false
                if (closed && !cleaned) {
                    // Retry only recorded directories once all writers have settled.
                    if (session.cleanupAttempts < 2) cleanupWhenSettled(session)
                    else ownedSessions.remove(session)
                }
                shutdownWhenSettled()
            })
        }
        try { workers.execute(cleanup) }
        catch (_: RejectedExecutionException) {
            session.cleanupSubmitted = false
            // Queue saturation must never make cancel/close throw or run filesystem I/O on
            // main. The session remains recorded until a worker accepts its cleanup.
            schedule(50L, Runnable { cleanupWhenSettled(session) })
        }
    }

    private fun shutdownWhenSettled() { if (closed && ownedSessions.isEmpty()) workers.shutdown() }

    private fun downloadError(resource: TMEScoringResource, failure: TMEResourceDownloadResult.Failure): TMEScoringPreparationError {
        val category = when (failure.error) {
            TMEResourceDownloadError.EMPTY_FILE -> TMEScoringPreparationErrorCategory.EMPTY_FILE
            TMEResourceDownloadError.FILE_IO -> TMEScoringPreparationErrorCategory.FILE_IO
            else -> TMEScoringPreparationErrorCategory.DOWNLOAD
        }
        return TMEScoringPreparationError(category, resource, httpCode = failure.httpCode)
    }

    companion object {
        private fun validUrl(value: String) = try {
            val uri = URI(value)
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) && !uri.host.isNullOrBlank()
        } catch (_: Exception) { false }

        private fun deleteOwnedFiles(directory: File): Boolean {
            return try {
                if (!directory.exists()) true else {
                    directory.listFiles()?.forEach { it.delete() }
                    directory.delete()
                }
            } catch (_: Exception) { false }
        }

        private fun scheduleOnMain(delay: Long, action: Runnable): TMEResourceDownloadTask {
            val handler = Handler(Looper.getMainLooper())
            check(handler.postDelayed(action, delay)) { "Main thread is unavailable" }
            return TMEResourceDownloadTask { handler.removeCallbacks(action) }
        }
    }
}

/** The preparation owns the bounded executor; closing a session parser closes its inputs
 * without shutting down workers still used by another session's files or cleanup. */
private class TMEParserWorkerLease(private val workers: ExecutorService) : ExecutorService by workers {
    override fun shutdown() {}
    override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
}
