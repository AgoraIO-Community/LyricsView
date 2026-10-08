package io.agora.karaoke_view_ex.tme

import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Decodes each submitted response on a dedicated serial worker, then posts one typed
 * success or failure to main. The caller must retain its delegate. No payload is logged.
 */
class TMEParser internal constructor(
    private val worker: ExecutorService,
    private val postCallback: (Runnable) -> Unit
) : Closeable {
    constructor() : this(
        Executors.newSingleThreadExecutor { task -> Thread(task, "TMEParser").apply { isDaemon = true } },
        TMEMainThread::post
    )

    @Volatile private var delegateReference = WeakReference<TMEParserDelegate>(null)
    private val lifecycleLock = Any()
    private var closed = false

    var delegate: TMEParserDelegate?
        get() = delegateReference.get()
        set(value) { delegateReference = WeakReference(value) }

    /** Returns after submission, including for malformed inputs. Not valid after close(). */
    fun parse(requestId: String, jsonOption: String, httpCode: Int, responseBody: String) {
        synchronized(lifecycleLock) {
            check(!closed) { "TMEParser is closed" }
            worker.execute {
                val result = try {
                    Result.success(TMEJsonDecoder.decode(jsonOption, httpCode, responseBody))
                } catch (error: TMEParseError) {
                    Result.failure(error)
                }
                postCallback(Runnable {
                    val listener = delegate ?: return@Runnable
                    result.fold(
                        onSuccess = { deliver(listener, requestId, it) },
                        onFailure = { listener.onParseError(requestId, jsonOption, responseBody, it as TMEParseError) }
                    )
                })
            }
        }
    }

    /** Idempotent. Accepted responses still finish; no new inputs can be submitted. */
    override fun close() {
        synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            worker.shutdown()
        }
    }

    private fun deliver(listener: TMEParserDelegate, id: String, response: TMEParsedResponse) {
        when (response) {
            is TMEParsedResponse.Songs -> listener.onSongs(id, response.result)
            is TMEParsedResponse.SearchSongs -> listener.onSearchSongs(id, response.result)
            is TMEParsedResponse.SongInfo -> listener.onSongInfo(id, response.result)
            is TMEParsedResponse.SongUrl -> listener.onSongUrl(id, response.result)
            is TMEParsedResponse.SonglistPage -> listener.onSonglistPage(id, response.result)
            is TMEParsedResponse.SonglistDetail -> listener.onSonglistDetail(id, response.result)
            is TMEParsedResponse.RanklistPage -> listener.onRanklistPage(id, response.result)
            is TMEParsedResponse.RanklistDetail -> listener.onRanklistDetail(id, response.result)
        }
    }
}
