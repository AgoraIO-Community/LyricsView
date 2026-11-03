package io.agora.examples.karaoke_view_ex

import android.content.Context
import android.os.Bundle
import io.agora.examples.karaoke_view_ex.agora.ServiceManager
import io.agora.examples.karaoke_view_ex.agora.tme.TmePlaybackController
import io.agora.examples.karaoke_view_ex.databinding.ActivityMainBinding
import io.agora.karaoke_view_ex.KaraokeView
import io.agora.karaoke_view_ex.model.LyricModel

/** Adapts a TME session to the same controls and KaraokeView used by native MCC. */
class TmeSingingPresenter(
    private val activity: MainActivity,
    private val views: ActivityMainBinding,
    private val karaoke: KaraokeView,
    savedState: Bundle?,
    extras: Bundle?
) : TmePlaybackController.Listener<LyricModel> {
    private var controller: TmePlaybackController<LyricModel>? = null
    private var sessionId = ""
    private var songId = ""
    private var songName = ""
    private var installedModel: LyricModel? = null
    private var modelInstalled = false
    private var foreground = false
    private var hideLyrics = false
    private var interrupted = false

    init {
        val source = savedState ?: extras
        sessionId = source?.getString("sessionId").orEmpty()
        songId = source?.getString("songId").orEmpty()
        songName = source?.getString("songName").orEmpty()
        if (savedState != null && sessionId.isNotEmpty()) ServiceManager.endTmeSong(sessionId)
        controller = if (savedState == null) ServiceManager.getTmeController(sessionId) else null
        controller?.let { songId = it.songId; songName = it.songName }
        interrupted = controller == null && songId.isNotEmpty()
    }

    fun start() { foreground = true; controller?.attach(this) }
    fun background() { foreground = false; controller?.detach(this) }
    fun save(out: Bundle) {
        out.putString("sessionId", sessionId); out.putString("songId", songId); out.putString("songName", songName)
    }
    fun clear() {
        val oldId = sessionId
        controller?.detach(this); controller = null
        if (oldId.isNotEmpty()) ServiceManager.endTmeSong(oldId)
        sessionId = ""; songId = ""; songName = ""; installedModel = null; modelInstalled = false; interrupted = false
    }
    fun destroy() { clear() }

    fun selectSong(id: String, name: String): Boolean {
        controller?.detach(this)
        val next = ServiceManager.startTmeSong(activity.applicationContext, id, name)
        if (next == null) { if (foreground) controller?.attach(this); return false }
        karaoke.reset(); install(null)
        sessionId = next; songId = id; songName = name; interrupted = false
        controller = ServiceManager.getTmeController(next)
        views.playOriginal.setText(R.string.play_accompany)
        if (foreground) controller?.attach(this)
        return true
    }
    fun play(): Boolean {
        if (songId.isEmpty()) {
            val song = ServiceManager.currentTmeSong()
            if (song == null) {
                views.callBack.setText(R.string.tme_loading_songs)
                ServiceManager.retryTmeSongs()
                return false
            }
            return selectSong(song.songId, song.name)
        }
        return selectSong(songId, songName)
    }
    fun stop() {
        controller?.detach(this)
        if (sessionId.isNotEmpty()) ServiceManager.endTmeSong(sessionId)
        controller = null; sessionId = ""; installedModel = null; interrupted = false
        karaoke.reset()
        install(null)
        views.playingProgress.text = "0"
        renderIdle()
    }
    fun next() {
        if (!ServiceManager.switchMusic()) return
        ServiceManager.currentTmeSong()?.let { selectSong(it.songId, it.name) }
    }
    fun togglePlay() { if (isRunning()) stop() else play() }
    fun pauseResume() {
        val current = controller ?: return
        if (current.state.phase == TmePlaybackController.Phase.PAUSED) current.resume() else current.pause()
    }
    private fun isRunning(): Boolean = controller?.state?.phase?.let {
        it != TmePlaybackController.Phase.COMPLETED && it != TmePlaybackController.Phase.FAILED && it != TmePlaybackController.Phase.CANCELLED
    } ?: false
    fun seek(position: Long): Boolean = controller?.seek(position) ?: false
    fun skipIntro() {
        val model = installedModel ?: return
        seek((model.preludeEndPosition - 1000).coerceAtLeast(0))
    }
    fun lineFinished(score: Int, cumulative: Int, count: Int) { controller?.onLineFinished(score, cumulative, count) }

    fun applyPreferences(noLyrics: Boolean) {
        val changed = hideLyrics != noLyrics
        hideLyrics = noLyrics
        if (changed) install(controller?.state?.model)
        attachUi()
        controller?.let { onState(it.state) } ?: renderIdle()
    }
    fun attachUi() {
        if (!modelInstalled) install(controller?.state?.model)
        karaoke.attachUi(if (views.enableLyrics.isChecked) views.lyricsView else null,
            if (views.enableScoring.isChecked) views.scoringView else null)
    }
    private fun install(model: LyricModel?) {
        karaoke.setLyricData(if (hideLyrics) null else model, true)
        installedModel = model
        modelInstalled = true
    }
    private fun renderIdle() {
        views.lyricsDescription.text = if (songId.isEmpty()) activity.getString(R.string.choose_song_prompt) else "[TME] $songName"
        views.callBack.setText(if (interrupted) R.string.tme_interrupted else R.string.tme_choose_song)
        views.play.setText(R.string.play); views.play.isEnabled = true
        views.pause.setText(R.string.pause); views.pause.isEnabled = false
        views.skipTheIntro.isEnabled = false; views.playOriginal.isEnabled = false
        views.switchToNext.isEnabled = true
        updateDragging()
    }
    override fun onState(state: TmePlaybackController.State<LyricModel>) {
        if (state.sessionId != sessionId) return
        val current = controller ?: return
        if (!modelInstalled || installedModel !== state.model) install(state.model)
        val ready = state.phase == TmePlaybackController.Phase.PLAYING || state.phase == TmePlaybackController.Phase.PAUSED
        views.play.setText(if (isRunning()) R.string.stop else R.string.play)
        views.play.isEnabled = true; views.pause.isEnabled = ready
        views.pause.setText(if (state.phase == TmePlaybackController.Phase.PAUSED) R.string.resume else R.string.pause)
        views.playOriginal.isEnabled = ready
        views.switchToNext.isEnabled = true
        views.skipTheIntro.isEnabled = ready && !hideLyrics && state.model?.lines?.isNotEmpty() == true
        updateDragging()
        val status = activity.getString(when (state.phase) {
            TmePlaybackController.Phase.PRELOADING -> R.string.tme_preparing
            TmePlaybackController.Phase.PREPARING -> R.string.tme_preparing
            TmePlaybackController.Phase.WAITING_FOR_PAGE -> R.string.tme_waiting_media
            TmePlaybackController.Phase.STARTING -> R.string.tme_starting
            TmePlaybackController.Phase.PLAYING -> if (state.scoring) R.string.tme_playing_scored else R.string.tme_playing_unscored
            TmePlaybackController.Phase.PAUSED -> R.string.tme_paused
            TmePlaybackController.Phase.COMPLETED -> R.string.tme_completed
            TmePlaybackController.Phase.FAILED -> R.string.tme_playback_failed
            TmePlaybackController.Phase.CANCELLED -> R.string.tme_interrupted
        })
        val error = when {
            state.playbackError != null -> activity.getString(R.string.tme_media_failure_reason, playbackReason(state.playbackError))
            state.preparationError != null -> activity.getString(R.string.tme_scoring_unavailable, preparationReason(state.preparationError))
            !state.scoreLineCountMatches -> activity.getString(R.string.tme_line_count_mismatch)
            else -> ""
        }
        views.lyricsDescription.text = "[TME] ${state.songName}\n$status" + if (error.isEmpty()) "" else "\n$error"
        val resources = activity.getString(R.string.tme_resource_state, activity.getString(R.string.tme_lyric), resourceText(state.lyric)) + " · " +
            activity.getString(R.string.tme_resource_state, activity.getString(R.string.tme_pitch), resourceText(state.pitch))
        views.callBack.text = when {
            state.phase == TmePlaybackController.Phase.PRELOADING -> activity.getString(R.string.tme_preloading, state.preloadPercent)
            state.scoring -> activity.getString(R.string.tme_scores, state.lineScore, state.cumulativeScore, state.fullScore)
            else -> resources + if (state.phase == TmePlaybackController.Phase.PREPARING) preparationStage(state.stage) else ""
        }
        if (foreground) {
            if (state.scoring && state.model != null) current.acknowledgeModel(this)
            else if (state.preparationError != null) current.acknowledgeUnscored(this)
        }
    }
    fun updateDragging() {
        val state = controller?.state
        val ready = state?.phase == TmePlaybackController.Phase.PLAYING || state?.phase == TmePlaybackController.Phase.PAUSED
        val enabled = activity.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
            .getBoolean(activity.getString(R.string.prefs_key_lyrics_dragging_switch), true)
        views.lyricsView.enableDragging(enabled && ready && !hideLyrics && state?.model != null)
    }
    private fun preparationStage(stage: TmePlaybackController.Stage?): String {
        val resource = when (stage) {
            TmePlaybackController.Stage.QUERYING -> R.string.tme_querying
            TmePlaybackController.Stage.PARSING -> R.string.tme_parsing
            null -> return ""
            else -> R.string.tme_downloading
        }
        return "\n" + activity.getString(resource)
    }
    private fun preparationReason(category: String) = activity.getString(when (category) {
        "QUERY_TIMEOUT", "DOWNLOAD_TIMEOUT", "OVERALL_TIMEOUT" -> R.string.tme_reason_timeout
        "SONG_NOT_FOUND", "MISSING_RESOURCE" -> R.string.tme_reason_missing
        "PARSE", "INVALID_RESPONSE", "EMPTY_FILE" -> R.string.tme_reason_invalid
        else -> R.string.tme_reason_download
    })
    private fun playbackReason(category: String) = activity.getString(when {
        category.endsWith("TIMEOUT") -> R.string.tme_reason_timeout
        category.startsWith("PRELOAD") -> R.string.tme_reason_preload
        category.startsWith("OPEN") -> R.string.tme_reason_open
        else -> R.string.tme_reason_player
    })
    private fun resourceText(state: TmePlaybackController.ResourceState) = activity.getString(when (state) {
        TmePlaybackController.ResourceState.WAITING -> R.string.tme_resource_waiting
        TmePlaybackController.ResourceState.DOWNLOADING -> R.string.tme_resource_downloading
        TmePlaybackController.ResourceState.DOWNLOADED -> R.string.tme_resource_downloaded
    })
    override fun onPosition(positionMs: Long, scoring: Boolean) {
        views.playingProgress.text = positionMs.toString()
        karaoke.setProgress(positionMs)
    }
    override fun onPitch(pitch: Double) { karaoke.setPitch(pitch.toFloat(), 0F, 0) }
}
