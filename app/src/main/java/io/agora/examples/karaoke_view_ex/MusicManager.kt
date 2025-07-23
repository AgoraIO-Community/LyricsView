package io.agora.examples.karaoke_view_ex

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import io.agora.examples.karaoke_view_ex.utils.ExampleConstants
import io.agora.examples.karaoke_view_ex.utils.LyricsResourcePool
import io.agora.musiccontentcenter.MccConstants
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object MusicManager {
    private const val TAG = ExampleConstants.TAG + "-MusicManager"
    private const val MUSIC_POSITION_UPDATE_INTERVAL = 20
    private var mRtcMccManager: RtcMccManager? = null

    @Volatile
    private var mStatus = ExampleConstants.Status.IDLE

    private var mPlayPosition: Long = 0

    private var mLyricType: MccConstants.LyricSourceType =
        MccConstants.LyricSourceType.LYRIC_SOURCE_XML
    private var mMusicPlayMode: MccConstants.MusicPlayMode =
        MccConstants.MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL

    private var mSongOffsetBegin = 0

    private var mScheduledExecutorService: ScheduledExecutorService? = null
    private var mCallback: IMccCallback? = null

    private var mLyricsCurrentProgress: Long = 0
    private var mCurrentSongCodeIndex = 0

    private var mIsOriginal = true

    private var mMusicResources: MutableList<LyricsResourcePool.MusicResource> = mutableListOf()


    fun init(application: Application, token: String, userId: String, callback: IMccCallback) {
        mCallback = callback
        initData()
        mRtcMccManager = RtcMccManager()
        mRtcMccManager?.init(application, token, userId, callback)
    }

    private fun initData() {
        mScheduledExecutorService = Executors.newScheduledThreadPool(5)
        mMusicResources.clear()
        mMusicResources.addAll(LyricsResourcePool.asMusicListVendor1())
    }

    fun updateMusicServiceType(type: ExampleConstants.ServiceType) {
        Log.d(TAG, "updateSongServiceType() called with: type = $type")
        mCurrentSongCodeIndex = 0
        when (type) {
            ExampleConstants.ServiceType.VENDOR_1 -> {
                mLyricType = MccConstants.LyricSourceType.LYRIC_SOURCE_XML
                mMusicPlayMode = MccConstants.MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL
                mMusicResources.clear()
                mMusicResources.addAll(LyricsResourcePool.asMusicListVendor1())
            }

            ExampleConstants.ServiceType.VENDOR_2 -> {
                mLyricType = MccConstants.LyricSourceType.LYRIC_SOURCE_KRC
                mMusicPlayMode = MccConstants.MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL
                mMusicResources.clear()
                mMusicResources.addAll(LyricsResourcePool.asMusicListVendor2())
            }
        }
    }

    fun openMusicBySongCode(internalSongCode: Long) {
        Log.d(TAG, "openMusic()  with: internalSongCode = $internalSongCode")
        setPlayMode(mMusicPlayMode)
        mRtcMccManager?.open(internalSongCode, 0)
    }

    fun openMusic() {
        Log.i(TAG, "openMusic")
        mRtcMccManager?.startScore(
            MccConstants.MusicContentCenterVendorId.fromValue(
                mMusicResources[mCurrentSongCodeIndex].vendorId
            ),
            mMusicResources[mCurrentSongCodeIndex].songCode,
            mMusicResources[mCurrentSongCodeIndex].songOptionJson
        )
    }

    fun stopMusic() {
        Log.i(TAG, "stopMusic() called")
        if (mStatus == ExampleConstants.Status.IDLE) {
            return
        }
        mLyricsCurrentProgress = 0
        mStatus = ExampleConstants.Status.IDLE
        mRtcMccManager?.stop()
    }

    private fun pauseMusic() {
        Log.i(TAG, "pauseMusic() called")
        if (!mStatus.isAtLeast(ExampleConstants.Status.Started)) {
            return
        }
        mStatus = ExampleConstants.Status.Paused
        mRtcMccManager?.pause()
    }

    private fun resumeMusic() {
        Log.i(TAG, "resumeMusic() called")
        if (!mStatus.isAtLeast(ExampleConstants.Status.Started)) {
            return
        }
        mStatus = ExampleConstants.Status.Started
        mRtcMccManager?.resume()
    }

    fun pauseOrResumeMusic() {
        Log.i(TAG, "pauseOrResumeMusic() called")
        if (mStatus == ExampleConstants.Status.Started) {
            pauseMusic()
        } else if (mStatus == ExampleConstants.Status.Paused) {
            resumeMusic()
        }
    }

    fun playMusic() {
        mIsOriginal = true
        mLyricsCurrentProgress = 0
        setPlayMode(MccConstants.MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL)
        if (MccConstants.MusicContentCenterVendorId.fromValue(
                mMusicResources[mCurrentSongCodeIndex].vendorId
            ) == MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2
        ) {
            mLyricType = MccConstants.LyricSourceType.LYRIC_SOURCE_KRC
        }
        mRtcMccManager?.preloadMusic(
            mMusicResources[mCurrentSongCodeIndex].songCode,
            MccConstants.MusicContentCenterVendorId.fromValue(
                mMusicResources[mCurrentSongCodeIndex].vendorId
            ),
            mLyricType,
            mMusicResources[mCurrentSongCodeIndex].songOptionJson
        )
    }

    fun seekMusic(position: Long) {
        mLyricsCurrentProgress = position
        mRtcMccManager?.seek(position)
        updateMusicPosition(position)
    }

    fun switchPlayOriginal() {
        if (mIsOriginal) {
            mIsOriginal = false
            setPlayMode(MccConstants.MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY)
        } else {
            mIsOriginal = true
            setPlayMode(MccConstants.MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL)
        }

    }

    fun switchMusic() {
        mLyricsCurrentProgress = 0
        mRtcMccManager?.stop()
        mCurrentSongCodeIndex++
        if (mCurrentSongCodeIndex >= mMusicResources.size) {
            mCurrentSongCodeIndex = 0
        }
    }

    fun onMusicOpenCompleted() {
        Log.d(TAG, "onMusicOpenCompleted() called")
        mRtcMccManager?.play()
        mStatus = ExampleConstants.Status.Opened
        startDisplayLrc()
    }

    fun onMusicPlaying() {
        Log.d(TAG, "onMusicPlaying() called")
        mStatus = ExampleConstants.Status.Started
        mCallback?.onMusicPlaying()
    }

    fun onMusicPause() {
        Log.d(TAG, "onMusicPause() called")
        mStatus = ExampleConstants.Status.Paused
    }

    fun onMusicStop() {
        Log.d(TAG, "onMusicStop() called")
        if (mStatus != ExampleConstants.Status.IDLE) {
            mStatus = ExampleConstants.Status.Stopped
        }
        mCallback?.onMusicStop()
        stopDisplayLrc()
        reset()
    }

    fun onMusicOpenError(error: Int) {
        Log.e(TAG, "onMusicOpenError() called with: error = $error")
    }

    fun onMusicCompleted() {
        Log.i(TAG, "onMusicCompleted() called")
        mCallback?.onMusicStop()
        stopDisplayLrc()
        reset()
    }

    @SuppressLint("DiscouragedApi")
    private fun startDisplayLrc() {
        maybeCreateNewScheduledService()
        mPlayPosition = -1L
        mScheduledExecutorService?.scheduleAtFixedRate({
            if (mStatus == ExampleConstants.Status.Started) {
                if (-1L == mPlayPosition || mPlayPosition % 1000 < MUSIC_POSITION_UPDATE_INTERVAL) {
                    mPlayPosition = mRtcMccManager?.getPlayPosition() ?: 0
                    mPlayPosition += mSongOffsetBegin.toLong()
                } else {
                    mPlayPosition += MUSIC_POSITION_UPDATE_INTERVAL.toLong()
                }
                mCallback?.onMusicPositionChange(mPlayPosition)
            }
        }, 0, MUSIC_POSITION_UPDATE_INTERVAL.toLong(), TimeUnit.MILLISECONDS)
    }

    private fun stopDisplayLrc() {
        mScheduledExecutorService?.shutdown()
    }

    private fun maybeCreateNewScheduledService() {
        if (null == mScheduledExecutorService || mScheduledExecutorService!!.isShutdown) {
            mScheduledExecutorService = Executors.newScheduledThreadPool(5)
        }
    }

    private fun reset() {
        mStatus = ExampleConstants.Status.IDLE
        mScheduledExecutorService?.shutdown()
        mScheduledExecutorService = null
    }

    private fun updateMusicPosition(position: Long) {
        if (mStatus == ExampleConstants.Status.Started) {
            mPlayPosition = position
        }
    }

    private fun setPlayMode(playMode: MccConstants.MusicPlayMode) {
        val ret = mRtcMccManager?.setPlayMode(playMode)
        if (ret == 0) {
            mMusicPlayMode = playMode
        }
        Log.d(TAG, "setPlayMode() called with: playMode = $playMode ret=$ret")
    }

    fun clearCache() {
        mRtcMccManager?.clearCache()
    }

    fun setLyricsCurrentProgress(progress: Long) {
        mLyricsCurrentProgress = progress
    }

    fun getLyricsCurrentProgress(): Long {
        return mLyricsCurrentProgress
    }

    fun isOriginal(): Boolean {
        return mIsOriginal
    }

    fun setLyricType(lyricType: MccConstants.LyricSourceType) {
        mLyricType = lyricType
    }

    fun isPlaying(): Boolean {
        return mStatus == ExampleConstants.Status.Started
    }

    fun isPaused(): Boolean {
        return mStatus == ExampleConstants.Status.Paused
    }

    fun destroy() {
        Log.d(TAG, "destroy() called")
        mScheduledExecutorService?.shutdownNow()
        mScheduledExecutorService = null
        mRtcMccManager?.destroy()
        mRtcMccManager = null
        mCallback = null
        reset()
    }
}