package io.agora.examples.karaoke_view_ex.agora

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import io.agora.examples.karaoke_view_ex.BuildConfig
import io.agora.mccex.IMusicContentCenterEx
import io.agora.mccex.IMusicContentCenterExEventHandler
import io.agora.mccex.IMusicContentCenterExScoreEventHandler
import io.agora.mccex.IMusicPlayer
import io.agora.mccex.MusicContentCenterExConfiguration
import io.agora.mccex.constants.ChargeMode
import io.agora.mccex.constants.LyricType
import io.agora.mccex.constants.MccExState
import io.agora.mccex.constants.MccExStateReason
import io.agora.mccex.constants.MusicPlayMode
import io.agora.mccex.constants.ScoreHardLevel
import io.agora.mccex.model.LineScoreData
import io.agora.mccex.model.RawScoreData
import io.agora.mccex.model.YsdVendorConfigure
import io.agora.mccex.utils.Utils
import io.agora.mediaplayer.Constants
import io.agora.mediaplayer.IMediaPlayerObserver
import io.agora.mediaplayer.data.PlayerUpdatedInfo
import io.agora.mediaplayer.data.SrcInfo
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.RtcEngine
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object MccExManager : IMusicContentCenterExEventHandler, IMusicContentCenterExScoreEventHandler {

    private const val TAG = "LyricsViewEx-MccExManager"

    private var mMccExService: IMusicContentCenterEx? = null
    private var mCallback: MccExCallback? = null
    private var mMusicPlayer: IMusicPlayer? = null
    private var mCurrentMusicPosition: Long = 0
    private var mToken: String = ""
    private var mUserId: String = ""
    private var mLyricFilePath = ""
    private var mPitchFilePath = ""
    private var mLyricOffset = 0
    private var mSongOffsetBegin = 0

    private const val MUSIC_POSITION_UPDATE_INTERVAL = 20

    private var mMusicPlayMode = MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL

    private var mStatus = Status.IDLE

    internal enum class Status(var value: Int) {
        IDLE(0),
        Opened(1),
        Started(2),
        Paused(3),
        Stopped(4);

        fun isAtLeast(state: Status): Boolean {
            return compareTo(state) >= 0
        }
    }

    private var mScheduledExecutorService: ScheduledExecutorService =
        Executors.newScheduledThreadPool(5);

    private val mMediaPlayerObserver: IMediaPlayerObserver = object : IMediaPlayerObserver {
        override fun onPlayerStateChanged(
            state: Constants.MediaPlayerState,
            error: Constants.MediaPlayerError
        ) {
            Log.d(TAG, "onPlayerStateChanged: $state $error")

            if (Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED == state) {
                if (mStatus == Status.IDLE) {
                    onMusicOpenCompleted()
                }
            }
            if (Constants.MediaPlayerState.PLAYER_STATE_PLAYING == state) {
                onMusicPlaying()
            } else if (Constants.MediaPlayerState.PLAYER_STATE_PAUSED == state) {
                onMusicPause()
            } else if (Constants.MediaPlayerState.PLAYER_STATE_STOPPED == state) {
                onMusicStop()
            } else if (Constants.MediaPlayerState.PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED == state) {
                onMusicCompleted()
            } else if (Constants.MediaPlayerState.PLAYER_STATE_FAILED == state) {
                onMusicOpenError(error.ordinal)
            }
        }

        override fun onPositionChanged(positionMs: Long, timestampMs: Long) {
        }


        override fun onPlayerEvent(
            eventCode: Constants.MediaPlayerEvent?,
            elapsedTime: Long,
            message: String?
        ) {
        }

        override fun onMetaData(type: Constants.MediaPlayerMetadataType, data: ByteArray) {}
        override fun onPlayBufferUpdated(playCachedBuffer: Long) {}
        override fun onPreloadEvent(src: String, event: Constants.MediaPlayerPreloadEvent) {}
        override fun onAgoraCDNTokenWillExpire() {}
        override fun onPlayerSrcInfoChanged(from: SrcInfo, to: SrcInfo) {}
        override fun onPlayerInfoUpdated(info: PlayerUpdatedInfo) {}
        override fun onAudioVolumeIndication(volume: Int) {}
    }

    fun setTokenAndUserId(token: String, userId: String) {
        Log.d(TAG, "setTokenAndUserId: token = $token, userId = $userId")
        mToken = token
        mUserId = userId
    }

    fun initMccExService(
        rtcEngine: RtcEngine,
        audioFrameObserver: IAudioFrameObserver,
        context: Context,
        callback: MccExCallback
    ) {
        Log.d(TAG, "MccEx sdk version = ${IMusicContentCenterEx.getSdkVersion()}")
        mCallback = callback
        mMccExService = IMusicContentCenterEx.create(rtcEngine)
        val configuration = MusicContentCenterExConfiguration()
        configuration.context = context
        configuration.vendorConfigure = YsdVendorConfigure(
            BuildConfig.YSD_APP_ID,
            BuildConfig.YSD_APP_KEY,
            mToken,
            mUserId,
            Utils.getUuid(),
            ChargeMode.ONCE,
            60 * 15
        )
        configuration.eventHandler = this
        configuration.scoreEventHandler = this
        configuration.audioFrameObserver = audioFrameObserver
        configuration.enableLog = true
        configuration.enableSaveLogToFile = true
        configuration.logFilePath = context.getExternalFilesDir(null)!!.path
        mMccExService?.initialize(configuration)
    }

    fun destroy() {
        reset()
        stopDisplayLrc()
        mMusicPlayer?.unRegisterPlayerObserver(mMediaPlayerObserver)
        mMusicPlayer?.let { mMccExService?.destroyMusicPlayer(it) ?: "" }
        IMusicContentCenterEx.destroy()
    }

    override fun onInitializeResult(state: MccExState, reason: MccExStateReason) {
        Log.d(TAG, "onInitializeResult: state = $state, reason = $reason")
        mMusicPlayer = mMccExService?.createMusicPlayer()
        mMusicPlayer?.registerPlayerObserver(mMediaPlayerObserver)
        //mMusicPlayer?.let { RtcManager.enableMediaPlayerOptions(it.mediaPlayerId, true) }
        mCallback?.onInitializeResult(state, reason)
    }

    override fun onStartScoreResult(songCode: Long, state: MccExState, reason: MccExStateReason) {
        Log.d(TAG, "onStartScoreResult: songCode = $songCode, state = $state, reason = $reason")
        if (state == MccExState.START_SCORE_STATE_COMPLETED) {
            mMccExService?.setScoreLevel(ScoreHardLevel.LEVEL5)
        }
        openMusic(songCode)
    }

    override fun onPreLoadEvent(
        requestId: String,
        songCode: Long,
        percent: Int,
        lyricPath: String,
        pitchPath: String,
        songOffsetBegin: Int,
        songOffsetEnd: Int,
        lyricOffset: Int,
        state: MccExState,
        reason: MccExStateReason
    ) {
        Log.d(
            TAG,
            "onPreLoadEvent: requestId = $requestId, songCode = $songCode, percent = $percent, lyricPath = $lyricPath, pitchPath = $pitchPath, songOffsetBegin = $songOffsetBegin, songOffsetEnd = $songOffsetEnd, lyricOffset = $lyricOffset, state = $state, reason = $reason"
        )
        if (state == MccExState.PRELOAD_STATE_COMPLETED && percent == 100) {
            mSongOffsetBegin = songOffsetBegin
            mMccExService?.startScore(songCode)
        }
        mCallback?.onPreLoadEvent(
            requestId,
            songCode,
            percent,
            lyricPath,
            pitchPath,
            songOffsetBegin,
            songOffsetEnd,
            lyricOffset,
            state,
            reason
        )
    }

    override fun onLyricResult(
        requestId: String,
        songCode: Long,
        lyricPath: String,
        songOffsetBegin: Int,
        songOffsetEnd: Int,
        lyricOffset: Int,
        reason: MccExStateReason
    ) {
        Log.d(
            TAG,
            "onLyricResult: requestId = $requestId, songCode = $songCode, lyricPath = $lyricPath, songOffsetBegin = $songOffsetBegin, songOffsetEnd = $songOffsetEnd, lyricOffset = $lyricOffset, reason = $reason"
        )
        mLyricFilePath = lyricPath
        mLyricOffset = lyricOffset
        mSongOffsetBegin = songOffsetBegin
        if (mLyricFilePath.isNotEmpty() && mPitchFilePath.isNotEmpty()) {
            mCallback?.onPreLoadEvent(
                requestId,
                songCode,
                100,
                mLyricFilePath,
                mPitchFilePath,
                songOffsetBegin,
                songOffsetEnd,
                lyricOffset,
                MccExState.PRELOAD_STATE_COMPLETED,
                reason
            )
        }
    }

    override fun onPitchResult(
        requestId: String,
        songCode: Long,
        pitchPath: String,
        songOffsetBegin: Int,
        songOffsetEnd: Int,
        reason: MccExStateReason
    ) {
        Log.d(
            TAG,
            "onPitchResult: requestId = $requestId, songCode = $songCode, pitchPath = $pitchPath, songOffsetBegin = $songOffsetBegin, songOffsetEnd = $songOffsetEnd, reason = $reason"
        )
        mPitchFilePath = pitchPath
        mSongOffsetBegin = songOffsetBegin
        if (mLyricFilePath.isNotEmpty() && mPitchFilePath.isNotEmpty()) {
            mCallback?.onPreLoadEvent(
                requestId,
                songCode,
                100,
                mLyricFilePath,
                mPitchFilePath,
                songOffsetBegin,
                songOffsetEnd,
                mLyricOffset,
                MccExState.PRELOAD_STATE_COMPLETED,
                reason
            )
        }
    }


    override fun onPitch(songCode: Long, data: RawScoreData) {
        Log.d(TAG, "onPitch: songCode = $songCode, data = $data")
        mCallback?.onPitch(songCode, data)
    }


    override fun onLineScore(songCode: Long, value: LineScoreData) {
        Log.d(TAG, "onLineScore: songCode = $songCode, value = $value")
        mCallback?.onLineScore(songCode, value)
    }


    private fun onMusicOpenCompleted() {
        Log.d(TAG, "onMusicOpenCompleted")
        mMusicPlayer?.play()

        mStatus = Status.Opened
        mCallback?.onPlayStateChange()

        startDisplayLrc()
    }

    private fun onMusicPlaying() {
        Log.d(TAG, "onMusicPlaying")
        mStatus = Status.Started
        mCallback?.onPlayStateChange()
        mCallback?.onMusicPlayingEx()
    }

    private fun onMusicPause() {
        Log.d(TAG, "onMusicPause")
        mStatus = Status.Paused
        mCallback?.onPlayStateChange()
    }

    private fun onMusicStop() {
        Log.d(TAG, "onMusicStop")
        if (mStatus != Status.IDLE) {
            mStatus = Status.Stopped
        }

        mCallback?.onMusicStopEx();
        stopDisplayLrc()
        reset()
        mCallback?.onPlayStateChange()
    }

    private fun onMusicCompleted() {
        Log.d(TAG, "onMusicCompleted")
        mCallback?.onMusicStopEx()
        stopDisplayLrc()
        reset()
        mCallback?.onPlayStateChange()
    }

    private fun onMusicOpenError(error: Int) {
        Log.d(TAG, "onMusicOpenError: $error")
        mStatus = Status.IDLE
        mCallback?.onPlayStateChange()
    }

    private fun openMusic(songCode: Long) {
        Log.d(TAG, "openMusic() called songCode=$songCode")
        mMusicPlayer?.setPlayMode(mMusicPlayMode)
        RtcManager.setPlayingSongCode(songCode)
        mMusicPlayer?.open(songCode, 0)
    }

    fun stop() {
        Log.d(TAG, "stop() called")
        if (mStatus == Status.IDLE) {
            return
        }
        mStatus = Status.IDLE
        mMusicPlayer?.stop()
        RtcManager.setPlayingSongCode(0L)
    }

    fun pause() {
        Log.d(TAG, "pause() called")

        if (!mStatus.isAtLeast(Status.Started)) {
            return
        }
        mStatus = Status.Paused
        mMusicPlayer?.pause()
    }

    fun resume() {
        Log.d(TAG, "resume() called")

        if (!mStatus.isAtLeast(Status.Started)) {
            return
        }
        mStatus = Status.Started
        mMusicPlayer?.resume()
    }

    fun seek(time: Long) {
        mMusicPlayer?.seek(time)
    }

    fun preloadMusic(songId: String, jsonOption: String) {
        Log.d(TAG, "preloadMusic() called")
        try {
            val songCode = mMccExService?.getInternalSongCode(songId, jsonOption) ?: 0L
            if (songCode == 0L) {
                Log.e(TAG, "getInternalSongCode failed songId=$songId")
                return
            }
            if (0 == mMccExService?.isPreloaded(songCode)) {
                Log.i(TAG, "mccEx is preloaded songCode=$songCode")
                mLyricFilePath = ""
                mPitchFilePath = ""
                mMccExService?.getLyric(songCode, LyricType.KRC)
                mMccExService?.getPitch(songCode)
            } else {
                val requestId = mMccExService?.preload(songCode) ?: ""
                Log.i(TAG, "preload requestId=$requestId")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startScore(songId: String, jsonOption: String) {
        Log.d(TAG, "preloadMusic() called")
        try {
            val songCode = mMccExService?.getInternalSongCode(songId, jsonOption) ?: 0L
            if (songCode == 0L) {
                Log.e(TAG, "getInternalSongCode failed songId=$songId")
                return
            }
            mMccExService?.startScore(songCode)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun reset() {
        mStatus = Status.IDLE
    }

    fun updateMusicPosition(position: Long) {
        if (mStatus == Status.Started) {
            mCurrentMusicPosition = position
        }
    }

    fun setPlayMode(playMode: MusicPlayMode) {
        mMusicPlayMode = playMode
        mMusicPlayer?.setPlayMode(playMode)
    }

    fun isMusicPlaying(): Boolean {
        return mStatus == Status.Started
    }

    fun isMusicPause(): Boolean {
        return mStatus == Status.Paused
    }

    fun isPlayOriginal(): Boolean {
        return mMusicPlayMode == MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL
    }

    @SuppressLint("DiscouragedApi")
    private fun startDisplayLrc() {
        maybeCreateNewScheduledService()
        mCurrentMusicPosition = -1
        mScheduledExecutorService.scheduleAtFixedRate({
            if (mStatus == Status.Started) {
                if (-1L == mCurrentMusicPosition || mCurrentMusicPosition % 1000 < MUSIC_POSITION_UPDATE_INTERVAL) {
                    mCurrentMusicPosition =
                        mMusicPlayer?.playPosition ?: 0
                    mCurrentMusicPosition += mSongOffsetBegin.toLong()
                } else {
                    mCurrentMusicPosition += MUSIC_POSITION_UPDATE_INTERVAL.toLong()
                }
                mCallback?.onMusicPositionChangeEx(mCurrentMusicPosition)
            }
        }, 0, MUSIC_POSITION_UPDATE_INTERVAL.toLong(), TimeUnit.MILLISECONDS)
    }

    private fun stopDisplayLrc() {
        mScheduledExecutorService.shutdown()
    }

    private fun maybeCreateNewScheduledService() {
        if (mScheduledExecutorService.isShutdown) {
            mScheduledExecutorService = Executors.newScheduledThreadPool(5)
        }
    }

    interface MccExCallback {
        fun onInitializeResult(state: MccExState, reason: MccExStateReason) {

        }

        fun onPreLoadEvent(
            requestId: String,
            songCode: Long,
            percent: Int,
            lyricPath: String,
            pitchPath: String,
            songOffsetBegin: Int,
            songOffsetEnd: Int,
            lyricOffset: Int,
            state: MccExState,
            reason: MccExStateReason
        ) {

        }

        fun onLyricResult(
            requestId: String,
            songCode: Long,
            lyricPath: String,
            songOffsetBegin: Int,
            songOffsetEnd: Int,
            lyricOffset: Int,
            reason: MccExStateReason
        ) {

        }

        fun onPitchResult(
            requestId: String,
            songCode: Long,
            pitchPath: String,
            songOffsetBegin: Int,
            songOffsetEnd: Int,
            reason: MccExStateReason
        ) {
        }

        fun onPlayStateChange() {

        }

        fun onMusicPositionChangeEx(position: Long) {

        }

        fun onMusicPlayingEx() {}

        fun onMusicStopEx() {}

        fun onPitch(songCode: Long, data: RawScoreData) {

        }

        fun onLineScore(songCode: Long, value: LineScoreData) {

        }

    }

}