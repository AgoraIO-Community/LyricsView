package io.agora.examples.karaoke_view_ex

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import io.agora.examples.karaoke_view_ex.databinding.ActivityMainBinding
import io.agora.examples.karaoke_view_ex.utils.ExampleConstants
import io.agora.examples.karaoke_view_ex.utils.KeyCenter
import io.agora.examples.karaoke_view_ex.utils.NetworkClient
import io.agora.examples.karaoke_view_ex.utils.Utils
import io.agora.karaoke_view_ex.KaraokeEvent
import io.agora.karaoke_view_ex.KaraokeView
import io.agora.karaoke_view_ex.constants.Constants
import io.agora.karaoke_view_ex.internal.constants.LyricType
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel
import io.agora.karaoke_view_ex.model.LyricModel
import io.agora.karaoke_view_ex.utils.LyricsCutter
import io.agora.musiccontentcenter.MccConstants
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), IMccCallback {
    companion object {
        private const val TAG = Constants.TAG + "-Main"
        private const val TAG_PERMISSION_REQUEST_CODE = 1000
        private val PERMISSION = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private var binding: ActivityMainBinding? = null
    private var mKaraokeView: KaraokeView? = null
    private var mLyricsModel: LyricModel? = null
    private var mSetNoLyric = false

    private var mExecutor = Executors.newSingleThreadExecutor()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        enableView(false)
        initRtcMcc()
        initKaraokeView()
        loadPreferences()
        initViewClick()
        enableView(true)
    }

    private fun initRtcMcc() {
        val prefs: SharedPreferences =
            applicationContext.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
        val tokenTime =
            prefs.getLong(ExampleConstants.SP_KEY_VENDOR_2_TOKEN_TIME, 0L)
        if (tokenTime == 0L || System.currentTimeMillis() - tokenTime > ExampleConstants.TOKEN_EXPIRE_TIME) {

            mExecutor.execute(Runnable {
                NetworkClient.sendHttpsRequest(
                    BuildConfig.VENDOR_2_TOKEN_HOST + KeyCenter.userUid,
                    HashMap<Any?, Any?>(0),
                    "",
                    false,
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.d(TAG, "initRtcMcc onFailure: " + e.message)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseData = response.body?.string()
                            Log.d(TAG, "init vendor2 onResponse: $responseData")
                            try {
                                val responseJson = JSONObject(responseData)
                                val dataJson = responseJson.getJSONObject("data")
                                val token = dataJson.getString("token")
                                val userId = dataJson.getString("yinsuda_uid")

                                prefs.edit {
                                    putString(
                                        ExampleConstants.SP_KEY_VENDOR_2_TOKEN,
                                        token
                                    )
                                    putString(
                                        ExampleConstants.SP_KEY_VENDOR_2_USER_ID,
                                        userId
                                    )
                                    putLong(
                                        ExampleConstants.SP_KEY_VENDOR_2_TOKEN_TIME,
                                        System.currentTimeMillis()
                                    )
                                }

                                Log.d(TAG, "initRtcMcc onResponse: token=$token, userId=$userId")
                                MusicManager.init(application, token, userId, this@MainActivity)

                            } catch (e: Exception) {
                                Log.e(TAG, "vendor2 onResponse: " + e.message)
                            }
                        }
                    })
            })
        } else {
            val token = prefs.getString(ExampleConstants.SP_KEY_VENDOR_2_TOKEN, "")
            val userId =
                prefs.getString(ExampleConstants.SP_KEY_VENDOR_2_USER_ID, "")
            Log.d(TAG, "initRtcMcc token: $token, userId: $userId")
            if (token.isNullOrEmpty() || userId.isNullOrEmpty()) {
                Log.e(TAG, "initRtcMcc token or userId is null")
                return
            }
            MusicManager.init(application, token, userId, this@MainActivity)
        }
    }

    private fun initKaraokeView() {
        mKaraokeView = KaraokeView(
            if (binding?.enableLyrics?.isChecked == true) binding?.lyricsView else null,
            if (binding?.enableScoring?.isChecked == true) binding?.scoringView else null
        )

        mKaraokeView?.setKaraokeEvent(object : KaraokeEvent {
            override fun onDragTo(view: KaraokeView, position: Long) {
                mKaraokeView?.setProgress(position)
                MusicManager.setLyricsCurrentProgress(position)
                updateCallback("Dragging, new progress $position")
                MusicManager.seekMusic(position)
            }

            override fun onLineFinished(
                view: KaraokeView,
                line: LyricsLineModel,
                score: Int,
                cumulativeScore: Int,
                index: Int,
                lineCount: Int
            ) {
                runOnUiThread(Runnable { updateCallback("score=$score, cumulatedScore=$cumulativeScore, index=$index, lineCount=$lineCount") })
            }
        })


        binding?.enableLyrics?.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (MusicManager.getLyricsCurrentProgress() <= 0) {
                    return
                }

                mKaraokeView?.attachUi(
                    if (isChecked) binding?.lyricsView else null,
                    if (binding?.enableScoring?.isChecked == true) binding?.scoringView else null
                )
            }
        })

        binding?.enableScoring?.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (MusicManager.getLyricsCurrentProgress() <= 0) {
                    return
                }

                mKaraokeView?.attachUi(
                    if (binding?.enableLyrics?.isChecked == true) binding?.lyricsView else null,
                    if (isChecked) binding?.scoringView else null
                )
            }
        })
    }

    private fun loadPreferences() {
        Log.i(TAG, "loadPreferences")
        val prefs: SharedPreferences =
            getSharedPreferences("karaoke_sample_app", MODE_PRIVATE)
        val scoringLevel: Int =
            prefs.getInt(
                getString(R.string.prefs_key_scoring_level),
                mKaraokeView?.scoringLevel ?: 0
            )
        mKaraokeView?.scoringLevel = scoringLevel
        val scoringOffset: Int = prefs.getInt(
            getString(R.string.prefs_key_scoring_compensation_offset),
            mKaraokeView?.scoringCompensationOffset ?: 0
        )
        mKaraokeView?.scoringCompensationOffset = scoringOffset

        mSetNoLyric = prefs.getBoolean(getString(R.string.prefs_key_set_no_lyric), false)
        if (mSetNoLyric) {
            mKaraokeView?.setLyricData(null, false)
        } else {
            mKaraokeView?.setLyricData(mLyricsModel, false)
        }

        val lyricType: Int =
            prefs.getInt(getString(R.string.prefs_key_lyric_type), LyricType.XML.ordinal)
        MusicManager.setLyricType(MccConstants.LyricSourceType.fromValue(lyricType))

        val indicatorOn: Boolean =
            prefs.getBoolean(getString(R.string.prefs_key_start_of_verse_indicator_switch), true)
        binding?.lyricsView?.enablePreludeEndPositionIndicator(indicatorOn)
        val indicatorColor: String =
            prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_color), "Default")
                ?: "Default"
        binding?.lyricsView?.setPreludeEndPositionIndicatorColor(
            Utils.colorInStringToDex(
                indicatorColor
            )
        )
        val indicatorRadius: String =
            prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_radius), "6dp")
                ?: "6dp"
        binding?.lyricsView?.setPreludeEndPositionIndicatorRadius(
            Utils.dp2pix(
                this.applicationContext, indicatorRadius.replace("dp", "").toFloat()
            ).toFloat()
        )
        val indicatorPaddingTop: Int =
            prefs.getInt(getString(R.string.prefs_key_start_of_verse_indicator_padding_top), 6)
        binding?.lyricsView?.setPreludeEndPositionIndicatorPaddingTop(
            Utils.dp2pix(
                this.applicationContext, indicatorPaddingTop.toFloat()
            ).toFloat()
        )

        val defaultTextColor: String =
            prefs.getString(getString(R.string.prefs_key_normal_line_text_color), "Default")
                ?: "Default"
        binding?.lyricsView?.setPreviousLineTextColor(Utils.colorInStringToDex(defaultTextColor))
        binding?.lyricsView?.setUpcomingLineTextColor(Utils.colorInStringToDex(defaultTextColor))

        val defaultTextSize: Int =
            prefs.getInt(getString(R.string.prefs_key_normal_line_text_size), 13)
        binding?.lyricsView?.setTextSize(
            Utils.sp2pix(
                this.applicationContext,
                defaultTextSize.toFloat()
            ).toFloat()
        )

        val currentTextColor: String =
            prefs.getString(getString(R.string.prefs_key_current_line_text_color), "Yellow")
                ?: "Yellow"
        binding?.lyricsView?.setCurrentLineTextColor(Utils.colorInStringToDex(currentTextColor))

        val highlightedTextColor: String = prefs.getString(
            getString(R.string.prefs_key_current_line_highlighted_text_color),
            "Red"
        ) ?: "Red"
        binding?.lyricsView?.setCurrentLineHighlightedTextColor(
            Utils.colorInStringToDex(
                highlightedTextColor
            )
        )

        val currentTextSize: Int =
            prefs.getInt(getString(R.string.prefs_key_current_line_text_size), 24)
        binding?.lyricsView?.setCurrentLineTextSize(
            Utils.sp2pix(
                this.applicationContext,
                currentTextSize.toFloat()
            ).toFloat()
        )

        val lineSpacing: String = prefs.getString(getString(R.string.prefs_key_line_spacing), "6dp")
            ?: "6dp"
        binding?.lyricsView?.setLineSpacing(
            Utils.dp2pix(
                this.applicationContext,
                lineSpacing.replace("dp", "").toFloat()
            ).toFloat()
        )

        val lyricsDraggingOn: Boolean =
            prefs.getBoolean(getString(R.string.prefs_key_lyrics_dragging_switch), true)
        binding?.lyricsView?.enableDragging(lyricsDraggingOn)

        val labelWhenNoLyrics: String = prefs.getString(
            getString(R.string.prefs_key_lyrics_not_available_text), getString(
                R.string.no_lyrics_label
            )
        ) ?: getString(R.string.no_lyrics_label)
        binding?.lyricsView?.setLabelShownWhenNoLyrics(labelWhenNoLyrics)

        val labelWhenNoLyricsTextColor: String =
            prefs.getString(getString(R.string.prefs_key_lyrics_not_available_text_color), "Red")
                ?: "Red"
        binding?.lyricsView?.setLabelShownWhenNoLyricsTextColor(
            Utils.colorInStringToDex(
                labelWhenNoLyricsTextColor
            )
        )

        val labelWhenNoLyricsTextSize: Int =
            prefs.getInt(getString(R.string.prefs_key_lyrics_not_available_text_size), 26)
        binding?.lyricsView?.setLabelShownWhenNoLyricsTextSize(
            Utils.sp2pix(
                this.applicationContext, labelWhenNoLyricsTextSize.toFloat()
            ).toFloat()
        )

        val heightOfRefPitch: String =
            prefs.getString(getString(R.string.prefs_key_ref_pitch_stick_height), "6dp")
                ?: "6dp"
        binding?.scoringView?.setRefPitchStickHeight(
            Utils.dp2pix(
                this.applicationContext,
                heightOfRefPitch.replace("dp", "").toFloat()
            ).toFloat()
        )

        val defaultRefPitchStickColor: String =
            prefs.getString(getString(R.string.prefs_key_default_ref_pitch_stick_color), "Default")
                ?: "Default"
        binding?.scoringView?.setRefPitchStickDefaultColor(
            Utils.colorInStringToDex(
                defaultRefPitchStickColor
            )
        )

        val highlightedRefPitchStickColor: String = prefs.getString(
            getString(R.string.prefs_key_highlighted_ref_pitch_stick_color),
            "Default"
        ) ?: "Default"
        binding?.scoringView?.setRefPitchStickHighlightedColor(
            Utils.colorInStringToDex(
                highlightedRefPitchStickColor
            )
        )

        var drawables: Array<Drawable>? = null
        val customizedIndicatorAndParticleOn: Boolean = prefs.getBoolean(
            getString(R.string.prefs_key_customized_indicator_and_particle_switch),
            false
        )
//        if (customizedIndicatorAndParticleOn) {
//            val bitmap: Bitmap = Utils.drawableToBitmap(getDrawable(R.drawable.pitch_indicator))
//            binding?.scoringView.setLocalPitchIndicator(bitmap)
//            drawables = arrayOf<Drawable>(
//                getDrawable(R.drawable.pitch_indicator), getDrawable(
//                    R.drawable.pitch_indicator_yellow
//                ), getDrawable(R.drawable.ic_launcher_background), getDrawable(
//                    R.drawable.star7
//                ), getDrawable(R.drawable.star8)
//            )
//        } else {
//            binding?.scoringView.setLocalPitchIndicator(null)
//        }

//        val particleEffectOn: Boolean =
//            prefs.getBoolean(getString(R.string.prefs_key_particle_effect_switch), true)
//        binding?.scoringView.enableParticleEffect(particleEffectOn, drawables)


        val particleHitOnThreshold: Float =
            prefs.getFloat(getString(R.string.prefs_key_hit_score_threshold), 0.8f)
        binding?.scoringView?.setThresholdOfHitScore(particleHitOnThreshold)

        val serviceType = prefs.getInt(
            getString(R.string.prefs_key_service_type), ExampleConstants.ServiceType.VENDOR_1.value
        )
        MusicManager.updateMusicServiceType(
            ExampleConstants.ServiceType.getServiceVendor(
                serviceType
            )
        )
    }

    private fun initViewClick() {
        binding?.play?.setOnClickListener {
            if (MusicManager.isPlaying()) {
                updateCallback("停止")
                doStop()
            } else {
                updateCallback("播放")
                doPlay()
            }
        }

        binding?.pause?.setOnClickListener {
            MusicManager.pauseOrResumeMusic()
        }

        binding?.switchToNext?.setOnClickListener {
            updateCallback("Next")
            MusicManager.switchMusic()
            doPlay()
        }

        binding?.skipTheIntro?.setOnClickListener {
            updateCallback("Skip Intro")
            skipTheIntro()
        }
        binding?.playOriginal?.setOnClickListener {
            if (MusicManager.isOriginal()) {
                binding?.playOriginal?.text =
                    this.getResources().getString(R.string.play_original)
            } else {
                binding?.playOriginal?.text =
                    this.getResources().getString(R.string.play_accompany)
            }
            MusicManager.switchPlayOriginal()
        }
        binding?.settings?.setOnClickListener {
            forwardToSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!EasyPermissions.hasPermissions(this, *PERMISSION)) {
            EasyPermissions.requestPermissions(
                this, getString(R.string.error_permission),
                TAG_PERMISSION_REQUEST_CODE, *PERMISSION
            )
        }
    }

    private fun loadTheLyrics(
        lrcUri: String,
        pitchUri: String,
        songOffsetBegin: Int,
        songOffsetEnd: Int,
        lyricOffset: Int
    ) {
        Log.i(
            TAG,
            "loadTheLyrics $lrcUri $pitchUri $lyricOffset"
        )
        mKaraokeView?.reset()
        mLyricsModel = null

        val tvDescription: TextView =
            findViewById<TextView>(R.id.lyrics_description)
        tvDescription.text = "Try to load $lrcUri"
        if (TextUtils.isEmpty(lrcUri)) {
            mLyricsModel = null

            // Call this will trigger no/invalid lyrics ui
            mKaraokeView?.setLyricData(null, false)
            MusicManager.openMusic()
            updateLyricsDescription()
        } else {
            val lrc = File(lrcUri)
            val pitch = File(pitchUri)
            mLyricsModel = KaraokeView.parseLyricData(lrc, pitch, true, lyricOffset)
            if (mSetNoLyric) {
                mKaraokeView?.setLyricData(null, false)
            } else {
                if (BuildConfig.ENABLE_LYRIC_CUTTER && songOffsetBegin != 0) {
                    mLyricsModel = LyricsCutter.cut(mLyricsModel, songOffsetBegin, songOffsetEnd)
                }
                mKaraokeView?.setLyricData(mLyricsModel, false)
            }
            MusicManager.openMusic()
            updateLyricsDescription()
        }
    }

    private fun updateLyricsDescription() {
        val lyricsSummary =
            if (mLyricsModel != null) (mLyricsModel?.name + ": " + mLyricsModel?.singer + " " + mLyricsModel?.preludeEndPosition + " " + mLyricsModel?.lines?.size + " " + mLyricsModel?.duration) else "Invalid lyrics"
        Log.d(TAG, "lyricsSummary: $lyricsSummary")
        val finalDescription = lyricsSummary + "\n"
        runOnUiThread(Runnable {
            val tvDescription: TextView =
                findViewById<TextView>(R.id.lyrics_description)
            tvDescription.text = finalDescription
        })
    }

    private fun updatePlayingProgress(progress: Long) {
        binding?.playingProgress?.text = progress.toString()
    }

    private fun updateCallback(callbackMessage: String) {
        binding?.callBack?.text = callbackMessage
    }

    private fun doPlay() {
        binding?.play?.text = this.getResources().getString(R.string.stop)
        binding?.playOriginal?.text = this.getResources().getString(R.string.play_accompany)
        MusicManager.playMusic()
    }

    private fun doStop() {
        binding?.play?.text = this.getResources().getString(R.string.play)
        MusicManager.stopMusic()
    }

    private fun forwardToSettings() {
        val intent: Intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun skipTheIntro() {
        val endTime = mLyricsModel?.lines?.lastOrNull()?.endTime ?: 0L
        if (mLyricsModel == null || MusicManager.getLyricsCurrentProgress() <= 0 || (endTime != 0L && MusicManager.getLyricsCurrentProgress() > endTime)) {
            Toast.makeText(
                applicationContext,
                "Not READY for SKIP INTRO, please Play first or no lyrics content",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // Jump to slight earlier
        val lyricsCurrentProgress = (mLyricsModel?.preludeEndPosition ?: 0L) - 1000
        if (lyricsCurrentProgress > 0) {
            MusicManager.seekMusic(lyricsCurrentProgress)
        }
    }


    private fun enableView(enable: Boolean) {
        binding?.play?.isEnabled = enable
        binding?.pause?.isEnabled = enable
        binding?.skipTheIntro?.isEnabled = enable
        binding?.switchToNext?.isEnabled = enable
        binding?.playOriginal?.isEnabled = enable
        binding?.settings?.isEnabled = enable
    }


    override fun onMusicLyricRequest(
        songCode: Long,
        lyricUrl: String,
        pitchUrl: String,
        songOffsetBegin: Int,
        songOffsetEnd: Int,
        lyricOffset: Int
    ) {
        runOnUiThread {
            loadTheLyrics(
                lyricUrl,
                pitchUrl,
                songOffsetBegin,
                songOffsetEnd,
                lyricOffset
            )
        }

    }

    override fun onMusicPreloadResult(songCode: Long, percent: Int) {
        updateCallback("Preload: $songCode  $percent%")
    }

    override fun onMusicPositionChange(position: Long) {
        MusicManager.setLyricsCurrentProgress(position)
        runOnUiThread(Runnable { updatePlayingProgress(position) })
        if (MusicManager.isPlaying()) {
            runOnUiThread(Runnable { mKaraokeView?.setProgress(position) })
        }
    }

    override fun onMusicPitchScore(
        internalSongCode: Long,
        voicePitch: Double,
        pitchScore: Double,
        progressInMs: Long
    ) {
        if (MusicManager.isPlaying()) {
            runOnUiThread(Runnable {
                mKaraokeView?.setPitch(
                    voicePitch.toFloat(),
                    progressInMs.toInt()
                )
            })
        }
    }

    override fun onMusicPlaying() {
        updateCallback("Playing")
    }

    override fun onMusicStop() {
        updateCallback("Stop")
    }

    override fun onMusicLineScore(
        internalSongCode: Long,
        linePitchScore: Float,
        cumulativeTotalLinePitchScores: Float,
        performedLineIndex: Int,
        performedTotalLines: Int
    ) {
        runOnUiThread(Runnable { updateCallback("linePitchScore=$linePitchScore, cumulativeTotalLinePitchScores=$cumulativeTotalLinePitchScores, performedLineIndex=$performedLineIndex, performedTotalLines=$performedTotalLines") })
    }

    override fun onDestroy() {
        super.onDestroy()

        if (mKaraokeView != null) {
            mKaraokeView?.setProgress(0)
            mKaraokeView?.setPitch(0f, 0)
            mKaraokeView?.reset()
            mKaraokeView = null
        }

        MusicManager.destroy()
    }
}