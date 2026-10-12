package io.agora.karaoke_view_ex.tme

import java.io.Closeable
import java.lang.ref.WeakReference

/** Bind this to the caller's MCC sendExtRequest; no SDK object is created here. */
fun interface TMERequestSender {
    fun sendExtRequest(jsonOption: String): String?
}

/**
 * Owns one current TME request. Send, stop, close and delegate changes run on main;
 * SDK responses may arrive on any thread. Use separate managers for concurrent flows.
 */
class TMERequestManager internal constructor(
    private val sender: TMERequestSender,
    private val parser: TMEParser,
    private val requireMainThread: () -> Unit
) : TMEParserDelegate, Closeable {
    constructor(sender: TMERequestSender) : this(sender, TMEParser(), TMEMainThread::requireCurrent)

    private var delegateReference = WeakReference<TMEParserDelegate>(null)
    private var currentRequestId: String? = null
    private var generation = 0L
    private val responseLock = Any()
    @Volatile private var closed = false

    init { parser.delegate = this }

    var delegate: TMEParserDelegate?
        get() = delegateReference.get()
        set(value) {
            requireMainThread()
            delegateReference = WeakReference(value)
        }

    /** Null means MCC rejected the send. Any previous request is invalidated first. */
    fun sendRequest(jsonOption: String): String? {
        requireMainThread()
        check(!closed) { "TMERequestManager is closed" }
        stop()
        val requestGeneration = generation
        val id = sender.sendExtRequest(jsonOption)?.takeIf { it.isNotEmpty() }
        if (!closed && generation == requestGeneration) currentRequestId = id
        return id
    }

    /** Forward the original four MCC onExtResponse arguments without decoding or logging. */
    fun onExtResponse(requestId: String, jsonOption: String, httpCode: Int, responseBody: String) {
        synchronized(responseLock) {
            if (!closed) parser.parse(requestId, jsonOption, httpCode, responseBody)
        }
    }

    /** Invalidates queued success/error callbacks; does not destroy the caller's RTC/MCC. */
    fun stop() {
        requireMainThread()
        generation++
        currentRequestId = null
    }

    override fun close() {
        requireMainThread()
        synchronized(responseLock) {
            if (closed) return
            stop()
            closed = true
            parser.close()
        }
    }

    private inline fun deliver(id: String, callback: (TMEParserDelegate) -> Unit) {
        requireMainThread()
        if (closed || currentRequestId != id) return
        // Consume before invoking UI code so reentry cannot clear its next request.
        currentRequestId = null
        delegateReference.get()?.let(callback)
    }

    override fun onSongs(requestId: String, result: TMESongsResult) =
        deliver(requestId) { it.onSongs(requestId, result) }
    override fun onSearchSongs(requestId: String, result: TMESearchSongsResult) =
        deliver(requestId) { it.onSearchSongs(requestId, result) }
    override fun onSongInfo(requestId: String, result: TMESongInfoResult) =
        deliver(requestId) { it.onSongInfo(requestId, result) }
    override fun onSongUrl(requestId: String, result: TMESongUrlResult) =
        deliver(requestId) { it.onSongUrl(requestId, result) }
    override fun onSonglistPage(requestId: String, result: TMEPageResult) =
        deliver(requestId) { it.onSonglistPage(requestId, result) }
    override fun onSonglistDetail(requestId: String, result: TMEDetailResult) =
        deliver(requestId) { it.onSonglistDetail(requestId, result) }
    override fun onRanklistPage(requestId: String, result: TMEPageResult) =
        deliver(requestId) { it.onRanklistPage(requestId, result) }
    override fun onRanklistDetail(requestId: String, result: TMEDetailResult) =
        deliver(requestId) { it.onRanklistDetail(requestId, result) }
    override fun onParseError(requestId: String, jsonOption: String, responseBody: String, error: TMEParseError) =
        deliver(requestId) { it.onParseError(requestId, jsonOption, responseBody, error) }
}
