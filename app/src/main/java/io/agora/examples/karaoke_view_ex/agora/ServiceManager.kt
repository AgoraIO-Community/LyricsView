package io.agora.examples.karaoke_view_ex.agora

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.agora.examples.karaoke_view_ex.BuildConfig
import io.agora.examples.karaoke_view_ex.agora.RtcManager.getRtcEngine
import io.agora.examples.utils.Constants
import io.agora.examples.utils.KeyCenter
import io.agora.examples.utils.LyricsResourcePool
import io.agora.examples.utils.NetworkClient.sendHttpsRequest
import io.agora.examples.utils.ServiceType
import io.agora.examples.utils.ToastUtils
import io.agora.karaoke_view_ex.downloader.LyricsFileDownloader
import io.agora.karaoke_view_ex.utils.Utils
import io.agora.mccex.constants.MccExState
import io.agora.mccex.constants.MccExStateReason
import io.agora.mccex.constants.MusicPlayMode
import io.agora.mccex.model.LineScoreData
import io.agora.mccex.model.RawScoreData
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors

object ServiceManager : MccManager.MccCallback, MccExManager.MccExCallback {
    private val TAG =
        io.agora.karaoke_view_ex.constants.Constants.TAG + "-" + ServiceManager::class.java.simpleName
    private var mExecutor = Executors.newSingleThreadExecutor()
    private var mMccManager: MccManager? = null
    private var mMccExManager: MccExManager? = null
    private var mServiceType: ServiceType? = ServiceType.MCC
    private var mCurrentSongCodeIndex = 0
    private var mIsOriginal = true
    private var mServiceCallback: ServiceCallback? = null
    private var mLyricType: Int = 0

    fun setServiceType(serviceType: ServiceType) {
        mServiceType = serviceType
    }

    fun getServiceType(): ServiceType? {
        return mServiceType
    }

    fun setLyricType(lyricType: Int) {
        mLyricType = lyricType
    }

    fun initService(context: Context, serviceCallback: ServiceCallback) {
        mCurrentSongCodeIndex = 0
        mServiceCallback = serviceCallback
        if (ServiceType.MCC_EX == mServiceType) {
            initMccEx(context)
        } else if (ServiceType.MCC == mServiceType) {
            initMcc(context)
        }
    }

    private fun initMccEx(context: Context) {
        mMccExManager = MccExManager

        val prefs: SharedPreferences =
            context.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
        val tokenTime = prefs.getLong(Constants.SP_KEY_YSD_TOKEN_TIME, 0L)
        if (tokenTime == 0L || System.currentTimeMillis() - tokenTime > Constants.TOKEN_EXPIRE_TIME) {

            mExecutor.execute(Runnable {
                sendHttpsRequest(
                    BuildConfig.YSD_TOKEN_HOST + KeyCenter.getUserUid(),
                    HashMap<Any?, Any?>(0),
                    "",
                    false,
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.d(TAG, "initMccEx onFailure: " + e.message)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseData = response.body!!.string()
                            Log.d(TAG, "initMccEx onResponse: $responseData")
                            try {
                                val responseJson = JSONObject(responseData)
                                val dataJson = responseJson.getJSONObject("data")
                                val token = dataJson.getString("token")
                                val userId = dataJson.getString("yinsuda_uid")

                                val editor = prefs.edit()
                                editor.putString(Constants.SP_KEY_YSD_TOKEN, token)
                                editor.putString(Constants.SP_KEY_YSD_USER_ID, userId)
                                editor.putLong(
                                    Constants.SP_KEY_YSD_TOKEN_TIME,
                                    System.currentTimeMillis()
                                )
                                editor.apply()


                                mMccExManager!!.setTokenAndUserId(token, userId)
                                mMccExManager!!.initMccExService(
                                    getRtcEngine(), RtcManager,
                                    context,
                                    this@ServiceManager
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "initMccEx onResponse: " + e.message)
                                ToastUtils.toastLong(
                                    context,
                                    "initMccEx onResponse: " + e.message
                                )
                            }
                        }
                    })
            })
        } else {
            val token = prefs.getString(Constants.SP_KEY_YSD_TOKEN, "")
            val userId = prefs.getString(Constants.SP_KEY_YSD_USER_ID, "")
            mMccExManager!!.setTokenAndUserId(token!!, userId!!)
            mMccExManager!!.initMccExService(
                getRtcEngine(), RtcManager,
                context,
                this
            )
        }
    }

    private fun initMcc(context: Context) {
        mMccManager = MccManager(context, this)
        mMccManager?.init(getRtcEngine())
    }

    fun seek(position: Long) {
        if (ServiceType.MCC_EX == mServiceType) {
            mMccExManager?.seek(position)
            mMccExManager?.updateMusicPosition(position)
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.seek(position)
            mMccManager?.updateMusicPosition(position)
        }
    }

    fun destroy() {
        Log.i(TAG, "destroyService")
        if (ServiceType.MCC_EX == mServiceType) {
            mMccExManager?.destroy()
            mMccExManager = null
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.destroy()
            mMccManager = null
        }
    }

    fun openMusic() {
        Log.i(TAG, "playMusic")
        if (ServiceType.MCC_EX == mServiceType) {
            mMccExManager?.startScore(
                LyricsResourcePool.asMusicListEx()[mCurrentSongCodeIndex].songId,
                LyricsResourcePool.asMusicListEx()[mCurrentSongCodeIndex].mediaType
            )
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.openMusic(LyricsResourcePool.asMusicList()[mCurrentSongCodeIndex].songCode)
        }
    }

    fun switchMusic() {
        if (ServiceType.MCC_EX == mServiceType) {
            mCurrentSongCodeIndex++
            if (mCurrentSongCodeIndex >= LyricsResourcePool.asMusicListEx().size) {
                mCurrentSongCodeIndex = 0
            }
        } else if (ServiceType.MCC == mServiceType) {
            mCurrentSongCodeIndex++
            if (mCurrentSongCodeIndex >= LyricsResourcePool.asMusicList().size) {
                mCurrentSongCodeIndex = 0
            }
        }
    }

    fun play() {
        mIsOriginal = true
        if (ServiceType.MCC_EX == mServiceType) {
            mMccExManager?.setPlayMode(MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL)
            mMccExManager?.preloadMusic(
                LyricsResourcePool.asMusicListEx()[mCurrentSongCodeIndex].songId,
                LyricsResourcePool.asMusicListEx()[mCurrentSongCodeIndex].mediaType
            )
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.setPlayMode(
                MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL,
                LyricsResourcePool.asMusicList()[mCurrentSongCodeIndex].songType
            )
            mMccManager?.preloadMusic(
                LyricsResourcePool.asMusicList()[mCurrentSongCodeIndex].songCode,
                mLyricType
            )
        }
    }

    fun stop() {
        if (ServiceType.MCC_EX == mServiceType) {
            mMccExManager?.stop()
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.stop()
        }
    }

    fun doPlayOriginal() {
        if (ServiceType.MCC_EX == mServiceType) {
            if (mIsOriginal) {
                mIsOriginal = false
                mMccExManager?.setPlayMode(MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY)
            } else {
                mIsOriginal = true
                mMccExManager?.setPlayMode(MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL)
            }
        } else if (ServiceType.MCC == mServiceType) {
            if (mIsOriginal) {
                mIsOriginal = false
                mMccManager?.setPlayMode(
                    MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY,
                    LyricsResourcePool.asMusicList()[mCurrentSongCodeIndex].songType
                )
            } else {
                mIsOriginal = true
                mMccManager?.setPlayMode(
                    MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL,
                    LyricsResourcePool.asMusicList()[mCurrentSongCodeIndex].songType
                )
            }
        }
    }

    fun isOriginalPlay(): Boolean {
        return mIsOriginal
    }

    fun pause() {
        if (ServiceType.MCC_EX == mServiceType) {
            mMccExManager?.pause()
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.pause()
        }
    }

    fun resume() {
        if (ServiceType.MCC_EX == mServiceType) {
            mMccExManager?.resume()
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.resume()
        }
    }

    fun clearCache(context: Context) {
        if (ServiceType.MCC_EX == mServiceType) {
            Utils.deleteFolder(context.externalCacheDir?.path + "/song/")
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.clearCache()
            LyricsFileDownloader.getInstance(context).cleanAll()
        }
    }

    fun updateSpeakerPitch(speakerPitch: Double) {
        if (ServiceType.MCC == mServiceType) {
            mMccManager?.getPlayPosition()?.let { mServiceCallback?.onMusicPitch(speakerPitch, it) }
        }
    }


    //////////////////////////// MccManager.MccCallback ////////////////////////////
    override fun onMusicLyricRequest(songCode: Long, lyricUrl: String?) {
        mServiceCallback?.onMusicLyricRequest(songCode, lyricUrl, null, 0, 0, 0)
    }

    override fun onMusicPreloadResult(songCode: Long, percent: Int) {
        mServiceCallback?.onMusicPreloadResult(songCode, percent)
    }

    override fun onMusicPositionChange(position: Long) {
        mServiceCallback?.onMusicPositionChange(position)
    }

    override fun onMusicPitch(voicePitch: Double, progressInMs: Long) {
        mServiceCallback?.onMusicPitch(voicePitch, progressInMs)
    }

    override fun onMusicPlaying() {
        mServiceCallback?.onMusicPlaying()
    }

    override fun onMusicStop() {
        mServiceCallback?.onMusicStop()
    }

    //////////////////////////// MccExManager.MccCallback ////////////////////////////
    override fun onInitializeResult(state: MccExState, reason: MccExStateReason) {
        super.onInitializeResult(state, reason)
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
        super.onPreLoadEvent(
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
        mServiceCallback?.onMusicPreloadResult(songCode, percent)
        if (percent == 100 && state == MccExState.PRELOAD_STATE_COMPLETED) {
            mServiceCallback?.onMusicLyricRequest(
                songCode,
                lyricPath,
                pitchPath,
                songOffsetBegin,
                songOffsetEnd,
                lyricOffset
            )
        }
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
        super.onLyricResult(
            requestId,
            songCode,
            lyricPath,
            songOffsetBegin,
            songOffsetEnd,
            lyricOffset,
            reason
        )
    }

    override fun onPitchResult(
        requestId: String,
        songCode: Long,
        pitchPath: String,
        songOffsetBegin: Int,
        songOffsetEnd: Int,
        reason: MccExStateReason
    ) {
        super.onPitchResult(requestId, songCode, pitchPath, songOffsetBegin, songOffsetEnd, reason)
    }


    override fun onPlayStateChange() {
        super.onPlayStateChange()
    }

    override fun onMusicPositionChangeEx(position: Long) {
        super.onMusicPositionChangeEx(position)
        mServiceCallback?.onMusicPositionChange(position)
    }

    override fun onMusicPlayingEx() {
        super.onMusicPlayingEx()
        mServiceCallback?.onMusicPlaying()
    }

    override fun onMusicStopEx() {
        super.onMusicStopEx()
        mServiceCallback?.onMusicStop()
    }

    override fun onPitch(songCode: Long, data: RawScoreData) {
        super.onPitch(songCode, data)
        mServiceCallback?.onMusicPitch(data.speakerPitch.toDouble(), data.progressInMs.toLong())
    }

    override fun onLineScore(songCode: Long, value: LineScoreData) {
        super.onLineScore(songCode, value)
        mServiceCallback?.onLineScore(
            songCode,
            value.linePitchScore,
            value.cumulativeTotalLinePitchScores.toInt(),
            value.performedLineIndex,
            value.performedTotalLines
        )
    }


    interface ServiceCallback {
        fun onMusicLyricRequest(
            songCode: Long,
            lyricUrl: String?,
            pitchUrl: String?,
            songOffsetBegin: Int,
            songOffsetEnd: Int,
            lyricOffset: Int
        ) {
        }

        fun onMusicPreloadResult(songCode: Long, percent: Int) {
        }

        fun onMusicPositionChange(position: Long) {
        }

        fun onMusicPitch(speakerPitch: Double, progressInMs: Long) {
        }

        fun onMusicPlaying() {
        }

        fun onMusicStop() {
        }

        fun onLineScore(
            songCode: Long,
            score: Int,
            cumulatedScore: Int,
            lineIndex: Int,
            totalLine: Int
        ) {

        }
    }
}