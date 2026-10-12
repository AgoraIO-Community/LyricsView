package io.agora.examples.karaoke_view_ex

import android.os.Bundle
import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.agora.examples.karaoke_view_ex.agora.ServiceManager
import io.agora.examples.karaoke_view_ex.agora.tme.TmePlaybackController
import io.agora.examples.karaoke_view_ex.databinding.ActivityTmeSingingBinding
import io.agora.karaoke_view_ex.KaraokeEvent
import io.agora.karaoke_view_ex.KaraokeView
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel
import io.agora.karaoke_view_ex.model.LyricModel
import java.util.Locale

class TmeSingingActivity : AppCompatActivity(), TmePlaybackController.Listener<LyricModel> {
    private lateinit var binding: ActivityTmeSingingBinding
    private lateinit var karaoke: KaraokeView
    private var controller: TmePlaybackController<LyricModel>? = null
    private var sessionId = ""
    private var installedModel: LyricModel? = null
    private var foreground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTmeSingingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        sessionId = savedInstanceState?.getString("sessionId") ?: intent.getStringExtra("sessionId").orEmpty()
        binding.songName.text = intent.getStringExtra("songName").orEmpty()
        KaraokeViewStyle.apply(this, binding.lyricsView, binding.scoringView)
        karaoke = KaraokeView(binding.lyricsView, binding.scoringView)
        binding.lyricsView.enableDragging(false)
        karaoke.setKaraokeEvent(object : KaraokeEvent {
            override fun onDragTo(view: KaraokeView, progress: Long) {}
            override fun onLineFinished(view: KaraokeView, line: LyricsLineModel, score: Int, cumulativeScore: Int, index: Int, lineCount: Int) {
                controller?.onLineFinished(score, cumulativeScore, lineCount)
            }
        })
        // Rotation is handled in place. Other recreation cannot restore the scorer's private samples.
        if (savedInstanceState != null) ServiceManager.endTmeSong(sessionId)
        controller = if (savedInstanceState == null) ServiceManager.getTmeController(sessionId) else null
        binding.backToSongs.setOnClickListener { finish() }
        binding.pauseResume.setOnClickListener {
            val current = controller ?: return@setOnClickListener
            if (current.state.phase == TmePlaybackController.Phase.PAUSED) current.resume() else current.pause()
        }
        binding.toggleTrack.setOnClickListener {
            ServiceManager.doPlayOriginal()
            binding.toggleTrack.setText(if (ServiceManager.isOriginalPlay()) R.string.play_accompany else R.string.play_original)
        }
        binding.restartSong.setOnClickListener { restart() }
        if (controller == null) showInterrupted()
    }
    override fun onStart() {
        super.onStart(); foreground = true
        controller?.attach(this)
    }
    override fun onStop() {
        foreground = false
        controller?.detach(this)
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("sessionId", sessionId)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        controller?.detach(this)
        ServiceManager.endTmeSong(sessionId)
        controller = null
        karaoke.reset()
        super.onDestroy()
    }
    private fun showInterrupted() {
        binding.singingStatus.setText(R.string.tme_interrupted)
        binding.scoringArea.visibility = View.GONE
        binding.restartSong.visibility = View.VISIBLE
        binding.restartSong.isEnabled = !intent.getStringExtra("songId").isNullOrEmpty()
    }
    private fun restart() {
        val id = intent.getStringExtra("songId") ?: return
        val name = intent.getStringExtra("songName").orEmpty()
        val next = ServiceManager.startTmeSong(applicationContext, id, name)
        if (next == null) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
            return
        }
        karaoke.reset(); installedModel = null
        sessionId = next
        intent.putExtra("sessionId", next)
        binding.restartSong.visibility = View.GONE
        binding.failureReason.text = ""
        binding.toggleTrack.setText(R.string.play_accompany)
        controller = ServiceManager.getTmeController(next)
        if (foreground) controller?.attach(this)
    }
    override fun onState(state: TmePlaybackController.State<LyricModel>) {
        if (state.sessionId != sessionId) return
        val current = controller ?: return
        binding.songName.text = state.songName
        binding.singingStatus.text = when (state.phase) {
            TmePlaybackController.Phase.PRELOADING -> getString(R.string.tme_preloading, state.preloadPercent)
            TmePlaybackController.Phase.PREPARING -> getString(R.string.tme_preparing)
            TmePlaybackController.Phase.WAITING_FOR_PAGE -> getString(R.string.tme_waiting_media)
            TmePlaybackController.Phase.STARTING -> getString(R.string.tme_starting)
            TmePlaybackController.Phase.PLAYING -> getString(if (state.scoring) R.string.tme_playing_scored else R.string.tme_playing_unscored)
            TmePlaybackController.Phase.PAUSED -> getString(R.string.tme_paused)
            TmePlaybackController.Phase.COMPLETED -> getString(R.string.tme_completed)
            TmePlaybackController.Phase.FAILED -> getString(R.string.tme_playback_failed)
            TmePlaybackController.Phase.CANCELLED -> getString(R.string.tme_interrupted)
        }
        binding.preparationStatus.text = if (state.model != null) getString(R.string.tme_preparation_ready)
        else if (state.preparationError != null) getString(R.string.tme_preparation_failed)
        else when (state.stage) {
            TmePlaybackController.Stage.QUERYING -> getString(R.string.tme_querying)
            TmePlaybackController.Stage.PARSING -> getString(R.string.tme_parsing)
            null -> ""
            else -> getString(R.string.tme_downloading)
        }
        binding.lyricStatus.text = getString(R.string.tme_resource_state, getString(R.string.tme_lyric), resourceText(state.lyric))
        binding.pitchStatus.text = getString(R.string.tme_resource_state, getString(R.string.tme_pitch), resourceText(state.pitch))
        binding.failureReason.text = when {
            state.playbackError != null -> getString(R.string.tme_media_failure_reason, playbackReason(state.playbackError))
            state.preparationError != null -> getString(R.string.tme_scoring_unavailable, preparationReason(state.preparationError))
            !state.scoreLineCountMatches -> getString(R.string.tme_line_count_mismatch)
            else -> ""
        }
        binding.pauseResume.isEnabled = state.phase == TmePlaybackController.Phase.PLAYING || state.phase == TmePlaybackController.Phase.PAUSED
        binding.pauseResume.setText(if (state.phase == TmePlaybackController.Phase.PAUSED) R.string.resume else R.string.pause)
        binding.toggleTrack.isEnabled = binding.pauseResume.isEnabled
        binding.scoreSummary.text = getString(R.string.tme_scores, state.lineScore, state.cumulativeScore, state.fullScore)
        if (state.scoring && state.model != null) {
            if (installedModel !== state.model) {
                karaoke.setLyricData(state.model, true)
                installedModel = state.model
            }
            binding.scoringArea.visibility = View.VISIBLE
            if (foreground) current.acknowledgeModel(this)
        } else {
            binding.scoringArea.visibility = View.GONE
            if (installedModel != null) { karaoke.reset(); installedModel = null }
            if (foreground && state.preparationError != null) current.acknowledgeUnscored(this)
        }
    }
    private fun resourceText(state: TmePlaybackController.ResourceState) = getString(when (state) {
        TmePlaybackController.ResourceState.WAITING -> R.string.tme_resource_waiting
        TmePlaybackController.ResourceState.DOWNLOADING -> R.string.tme_resource_downloading
        TmePlaybackController.ResourceState.DOWNLOADED -> R.string.tme_resource_downloaded
    })
    private fun preparationReason(category: String) = getString(when (category) {
        "QUERY_TIMEOUT", "DOWNLOAD_TIMEOUT", "OVERALL_TIMEOUT" -> R.string.tme_reason_timeout
        "SONG_NOT_FOUND", "MISSING_RESOURCE" -> R.string.tme_reason_missing
        "PARSE", "INVALID_RESPONSE", "EMPTY_FILE" -> R.string.tme_reason_invalid
        else -> R.string.tme_reason_download
    })
    private fun playbackReason(category: String) = getString(when {
        category.endsWith("TIMEOUT") -> R.string.tme_reason_timeout
        category.startsWith("PRELOAD") -> R.string.tme_reason_preload
        category.startsWith("OPEN") -> R.string.tme_reason_open
        else -> R.string.tme_reason_player
    })
    override fun onPosition(positionMs: Long, scoring: Boolean) {
        binding.playPosition.text = String.format(Locale.ROOT, "%02d:%02d", positionMs / 60_000, positionMs / 1000 % 60)
        if (scoring) karaoke.setProgress(positionMs)
    }
    override fun onPitch(pitch: Double) { karaoke.setPitch(pitch.toFloat(), 0F, 0) }
}
