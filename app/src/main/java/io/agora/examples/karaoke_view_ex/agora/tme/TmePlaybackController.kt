package io.agora.examples.karaoke_view_ex.agora.tme

import java.io.Closeable

/** One song session. Every call and port callback is serialized on the UI thread by its owner. */
class TmePlaybackController<M : Any>(
    val sessionId: String,
    val songId: String,
    val songName: String,
    private val media: MediaPort,
    private val preparation: PreparationPort<M>,
    private val scheduler: Scheduler,
    private val timeouts: Timeouts = Timeouts()
) : Closeable {
    fun interface Cancel { fun cancel() }
    interface Scheduler { fun schedule(delayMs: Long, action: () -> Unit): Cancel }
    data class Timeouts(val preloadMs: Long = 120_000, val openMs: Long = 30_000, val playingMs: Long = 15_000)
    data class PreloadRequest(val songCode: Long, val requestId: String)
    /** Demo-owned ports return SDK failure codes instead of throwing; release is idempotent. */
    interface MediaPort {
        fun preload(sessionId: String, songId: String): PreloadRequest?
        fun open(sessionId: String, songCode: Long): Int
        fun play(): Int
        fun pause(): Int
        fun resume(): Int
        fun position(): Long
        fun release()
    }
    interface PreparationPort<M> {
        fun start(songId: String, listener: PreparationListener<M>)
        fun cancel()
    }
    interface PreparationListener<M> {
        fun onStage(stage: Stage)
        fun onReady(model: M, scorableLineCount: Int)
        fun onFailure(category: String)
    }
    enum class Stage { QUERYING, LYRIC_DOWNLOADING, LYRIC_DOWNLOADED, PITCH_DOWNLOADING, PITCH_DOWNLOADED, PARSING }
    enum class ResourceState { WAITING, DOWNLOADING, DOWNLOADED }
    enum class Phase { PRELOADING, PREPARING, WAITING_FOR_PAGE, STARTING, PLAYING, PAUSED, COMPLETED, FAILED, CANCELLED }
    enum class PlayerEvent { OPENED, PLAYING, PAUSED, COMPLETED, FAILED }
    data class State<M>(
        val sessionId: String, val songId: String, val songName: String,
        val phase: Phase = Phase.PRELOADING, val preloadPercent: Int = 0,
        val mediaOpened: Boolean = false, val stage: Stage? = null,
        val lyric: ResourceState = ResourceState.WAITING, val pitch: ResourceState = ResourceState.WAITING,
        val model: M? = null, val scoring: Boolean = false, val preparationError: String? = null,
        val playbackError: String? = null, val scorableLineCount: Int = 0,
        val lineScore: Int = 0, val cumulativeScore: Int = 0, val scoreLineCountMatches: Boolean = true,
        val positionMs: Long = 0
    ) { val fullScore: Int get() = scorableLineCount * 100 }
    interface Listener<M> {
        fun onState(state: State<M>)
        fun onPosition(positionMs: Long, scoring: Boolean)
        fun onPitch(pitch: Double)
    }

    var state = State<M>(sessionId, songId, songName)
        private set
    private var page: Listener<M>? = null
    private var started = false
    private var closed = false
    private var released = false
    private var request: PreloadRequest? = null
    private var preloaded = false
    private var preparationFinished = false
    private var pageApplied = false
    private var playSubmitted = false
    private var firstPlayCalled = false
    private var awaitingPlaying = false
    private var controlGeneration = 0L
    private var timer: Cancel? = null
    private var tick: Cancel? = null

    private val preparationListener = object : PreparationListener<M> {
        override fun onStage(stage: Stage) {
            if (!active() || preparationFinished) return
            state = state.copy(stage = stage,
                lyric = when (stage) { Stage.LYRIC_DOWNLOADING -> ResourceState.DOWNLOADING; Stage.LYRIC_DOWNLOADED -> ResourceState.DOWNLOADED; else -> state.lyric },
                pitch = when (stage) { Stage.PITCH_DOWNLOADING -> ResourceState.DOWNLOADING; Stage.PITCH_DOWNLOADED -> ResourceState.DOWNLOADED; else -> state.pitch })
            publish()
        }
        override fun onReady(model: M, scorableLineCount: Int) {
            if (!active() || preparationFinished) return
            if (scorableLineCount <= 0) { onFailure("PARSE"); return }
            preparationFinished = true
            state = state.copy(model = model, scoring = true, scorableLineCount = scorableLineCount,
                phase = Phase.WAITING_FOR_PAGE)
            publish(); tryPlay()
        }
        override fun onFailure(category: String) {
            if (!active() || preparationFinished) return
            preparationFinished = true
            state = state.copy(model = null, scoring = false, preparationError = category,
                phase = Phase.WAITING_FOR_PAGE)
            preparation.cancel()
            publish(); tryPlay()
        }
    }

    fun start() {
        if (started || !active()) return
        started = true
        timer = scheduler.schedule(timeouts.preloadMs) { if (active() && !preloaded) fail("PRELOAD_TIMEOUT") }
        request = media.preload(sessionId, songId)
        if (request == null || request!!.songCode <= 0 || request!!.requestId.isBlank()) fail("PRELOAD_REJECTED")
    }
    fun attach(listener: Listener<M>) {
        if (closed) return
        page = listener
        // A new page must install its own view before acknowledging readiness.
        pageApplied = false
        publish()
    }
    fun detach(listener: Listener<M>) {
        if (page !== listener) return
        page = null; pageApplied = false
        if (state.phase == Phase.PLAYING || state.phase == Phase.STARTING) pause()
    }
    fun acknowledgeModel(listener: Listener<M>) {
        if (page !== listener || !state.scoring || state.model == null || !active()) return
        pageApplied = true; tryPlay()
        if (inputsEnabled()) scheduleTick()
    }
    fun acknowledgeUnscored(listener: Listener<M>) {
        if (page !== listener || state.preparationError == null || state.scoring || !active()) return
        pageApplied = true; tryPlay()
        if (inputsEnabled()) scheduleTick()
    }
    fun onPreload(id: String, requestId: String, songCode: Long, percent: Int, status: Int, errorCode: Int) {
        val expected = request ?: return
        if (id != sessionId || expected.requestId != requestId || expected.songCode != songCode || !active() || preloaded) return
        if (status == 1 || errorCode != 0) { fail("PRELOAD_FAILED:$errorCode"); return }
        state = state.copy(preloadPercent = percent.coerceIn(0, 100))
        if (status != 0 || percent != 100) { publish(); return }
        // Consume completion before publishing: page callbacks may synchronously reenter.
        preloaded = true; timer?.cancel()
        state = state.copy(phase = Phase.PREPARING); publish()
        if (!active()) return
        timer = scheduler.schedule(timeouts.openMs) { if (active() && !state.mediaOpened) fail("OPEN_TIMEOUT") }
        if (media.open(sessionId, songCode) != 0) { fail("OPEN_REJECTED"); return }
        if (active()) preparation.start(songId, preparationListener)
    }
    fun onPlayer(id: String, event: PlayerEvent) {
        if (id != sessionId || !active()) return
        when (event) {
            PlayerEvent.OPENED -> {
                if (!preloaded || state.mediaOpened) return
                timer?.cancel(); state = state.copy(mediaOpened = true); publish(); tryPlay()
            }
            PlayerEvent.PLAYING -> {
                if (!playSubmitted) return
                if (!awaitingPlaying) {
                    if ((page == null || state.phase == Phase.PAUSED) && media.pause() != 0) fail("PAUSE_REJECTED")
                    return
                }
                awaitingPlaying = false; timer?.cancel()
                if (page == null) { pause(); return }
                state = state.copy(phase = Phase.PLAYING); publish(); scheduleTick()
            }
            PlayerEvent.PAUSED -> { tick?.cancel(); state = state.copy(phase = Phase.PAUSED); publish() }
            PlayerEvent.COMPLETED -> {
                if (!playSubmitted) return
                timer?.cancel(); tick?.cancel(); awaitingPlaying = false
                state = state.copy(phase = Phase.COMPLETED); publish()
            }
            PlayerEvent.FAILED -> fail("PLAYER_FAILED")
        }
    }
    private fun tryPlay() {
        if (!active() || playSubmitted || page == null || !pageApplied || !state.mediaOpened || !preparationFinished) return
        playSubmitted = true; awaitingPlaying = true
        state = state.copy(phase = Phase.STARTING)
        // Set flags before notifications and SDK call: both may synchronously reenter.
        publish()
        if (firstPlayCalled) return // A reentrant newer attempt already issued initial play.
        if (!active() || page == null || !pageApplied || state.phase != Phase.STARTING) {
            if (active() && !firstPlayCalled) {
                playSubmitted = false; awaitingPlaying = false
                state = state.copy(phase = Phase.WAITING_FOR_PAGE)
            }
            return
        }
        timer = scheduler.schedule(timeouts.playingMs) { if (active() && awaitingPlaying) fail("PLAYING_TIMEOUT") }
        firstPlayCalled = true
        if (media.play() != 0) fail("PLAY_REJECTED")
    }
    private fun scheduleTick() {
        tick?.cancel()
        if (!inputsEnabled()) return
        tick = scheduler.schedule(20) {
            if (!inputsEnabled()) return@schedule
            val listener = page ?: return@schedule
            val position = media.position().coerceAtLeast(0)
            if (!inputsEnabled() || page !== listener) return@schedule
            state = state.copy(positionMs = position)
            listener.onPosition(position, state.scoring)
            if (inputsEnabled()) scheduleTick()
        }
    }
    fun onLocalPitch(pitch: Double) {
        // A real zero voicePitch is silence, which also advances natural line completion.
        if (!inputsEnabled() || !state.scoring || !pitch.isFinite() || pitch < 0) return
        val listener = page ?: return
        val position = media.position().coerceAtLeast(0)
        if (!inputsEnabled() || page !== listener || !state.scoring) return
        state = state.copy(positionMs = position)
        listener.onPosition(position, true)
        if (inputsEnabled() && page === listener && state.scoring) listener.onPitch(pitch)
    }
    fun onLineFinished(score: Int, cumulativeScore: Int, lineCount: Int) {
        if (!inputsEnabled() || !state.scoring) return
        state = state.copy(lineScore = score, cumulativeScore = cumulativeScore,
            scoreLineCountMatches = lineCount == state.scorableLineCount)
        publish()
    }
    fun pause() {
        if (!active() || !playSubmitted || (state.phase != Phase.PLAYING && state.phase != Phase.STARTING)) return
        val operation = ++controlGeneration
        awaitingPlaying = false; timer?.cancel(); tick?.cancel()
        if (!firstPlayCalled) {
            playSubmitted = false
            state = state.copy(phase = Phase.WAITING_FOR_PAGE); publish()
            return
        }
        state = state.copy(phase = Phase.PAUSED); publish()
        if (active() && controlGeneration == operation && state.phase == Phase.PAUSED && media.pause() != 0) fail("PAUSE_REJECTED")
    }
    fun resume() {
        if (!active() || page == null || state.phase != Phase.PAUSED || !playSubmitted) return
        val operation = ++controlGeneration
        awaitingPlaying = true; state = state.copy(phase = Phase.STARTING); publish()
        if (!active() || controlGeneration != operation || page == null || state.phase != Phase.STARTING) return
        timer = scheduler.schedule(timeouts.playingMs) { if (active() && awaitingPlaying) fail("PLAYING_TIMEOUT") }
        if (media.resume() != 0) fail("RESUME_REJECTED")
    }
    private fun inputsEnabled() = active() && page != null && pageApplied && state.phase == Phase.PLAYING
    private fun active() = !closed && state.phase != Phase.FAILED && state.phase != Phase.COMPLETED && state.phase != Phase.CANCELLED
    private fun publish() { page?.onState(state) }
    private fun fail(category: String) {
        if (!active()) return
        timer?.cancel(); tick?.cancel(); awaitingPlaying = false
        state = state.copy(phase = Phase.FAILED, playbackError = category)
        preparation.cancel(); release(); publish()
    }
    private fun release() { if (!released) { released = true; media.release() } }
    override fun close() {
        if (closed) return
        closed = true; timer?.cancel(); tick?.cancel(); preparation.cancel(); release()
        state = state.copy(phase = Phase.CANCELLED, model = null, scoring = false)
        publish(); page = null
    }
}
