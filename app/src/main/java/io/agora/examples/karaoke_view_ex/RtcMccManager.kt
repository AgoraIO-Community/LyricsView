package io.agora.examples.karaoke_view_ex

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.agora.examples.karaoke_view_ex.utils.ExampleConstants
import io.agora.examples.karaoke_view_ex.utils.KeyCenter
import io.agora.examples.karaoke_view_ex.utils.Utils
import io.agora.mediaplayer.Constants
import io.agora.mediaplayer.IMediaPlayerObserver
import io.agora.mediaplayer.data.CacheStatistics
import io.agora.mediaplayer.data.PlayerPlaybackStats
import io.agora.mediaplayer.data.PlayerUpdatedInfo
import io.agora.mediaplayer.data.SrcInfo
import io.agora.musiccontentcenter.IAgoraMusicContentCenter
import io.agora.musiccontentcenter.IAgoraMusicPlayer
import io.agora.musiccontentcenter.IMusicContentCenterEventHandler
import io.agora.musiccontentcenter.IScoreEventHandler
import io.agora.musiccontentcenter.LineScoreData
import io.agora.musiccontentcenter.LyricInfo
import io.agora.musiccontentcenter.MccConstants
import io.agora.musiccontentcenter.Music
import io.agora.musiccontentcenter.MusicCacheInfo
import io.agora.musiccontentcenter.MusicChartInfo
import io.agora.musiccontentcenter.MusicContentCenterConfiguration
import io.agora.musiccontentcenter.RawScoreData
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RtcMccManager {
    companion object {
        private const val TAG = ExampleConstants.TAG + "-MccManager"
        private val mCacheThreadPool: ExecutorService = Executors.newCachedThreadPool()
    }

    private var mApplication: Application? = null

    private var mRtcEngine: RtcEngine? = null
    private var mMcc: IAgoraMusicContentCenter? = null
    private var mAgoraMusicPlayer: IAgoraMusicPlayer? = null
    private var mConfig: MusicContentCenterConfiguration? = null

    private var mChannelId: String = ""
    private var mToken = ""
    private var mUserId = ""

    private var mCallback: IMccCallback? = null

    private val mMccEventHandler: IMusicContentCenterEventHandler =
        object : IMusicContentCenterEventHandler {
            override fun onPreLoadEvent(
                requestId: String,
                internalSongCode: Long,
                percent: Int,
                payload: String,
                state: Int,
                reason: Int
            ) {
                Log.d(
                    TAG,
                    "mcc onPreLoadEvent requestId:$requestId songCode:$internalSongCode percent:$percent payload:$payload state:$state reason:$reason"
                )
                mCallback?.onMusicPreloadResult(internalSongCode, percent)
                if (state == 0 && percent == 100) {
                    handleLyricResult(internalSongCode, payload)
                }
            }

            override fun onMusicCollectionResult(
                requestId: String,
                page: Int,
                pageSize: Int,
                total: Int,
                list: Array<Music>,
                errorCode: Int
            ) {
            }

            override fun onMusicChartsResult(
                requestId: String,
                list: Array<MusicChartInfo>,
                errorCode: Int
            ) {
            }

            override fun onLyricResult(
                requestId: String,
                internalSongCode: Long,
                payload: String,
                reason: Int
            ) {
                Log.d(
                    TAG,
                    "mcc onLyricResult()  requestId :$requestId songCode:$internalSongCode payload:$payload reason:$reason"
                )
                handleLyricResult(internalSongCode, payload)
            }

            override fun onLyricInfoResult(
                requestId: String,
                internalSongCode: Long,
                lyricInfo: LyricInfo,
                reason: Int
            ) {
            }

            override fun onSongSimpleInfoResult(
                requestId: String,
                songCode: Long,
                simpleInfo: String,
                errorCode: Int
            ) {
                Log.d(
                    TAG,
                    "onSongSimpleInfoResult() called with: requestId = [$requestId], songCode = [$songCode], simpleInfo = [$simpleInfo], errorCode = [$errorCode]"
                )
            }

            override fun onStartScoreResult(internalSongCode: Long, state: Int, reason: Int) {
                Log.d(
                    TAG,
                    "mcc onStartScoreResult() called with: internalSongCode = [$internalSongCode], state = [$state], reason = [$reason]"
                )

                if (MccConstants.MusicContentCenterState.MUSIC_CONTENT_CENTER_STATE_START_SCORE_COMPLETED.value() == state
                    && MccConstants.MusicContentCenterStateReason.MUSIC_CONTENT_CENTER_STATE_REASON_OK.value() == reason
                ) {
                    mCacheThreadPool.execute {
                        if (state == MccConstants.MusicContentCenterState.MUSIC_CONTENT_CENTER_STATE_START_SCORE_COMPLETED.value()) {
                            mMcc?.setScoreLevel(MccConstants.ScoreLevel.SCORE_LEVEL_5)
                        }
                        MusicManager.openMusicBySongCode(internalSongCode)
                    }
                }
            }
        }

    private fun handleLyricResult(internalSongCode: Long, payload: String) {
        /*
         * {"lyricPath":"/data/user/0/io.agora.examples.karaoke_view_ex/cache/mccex/111805482632310595.krc","pitchPath":"/data/user/0/io.agora.examples.karaoke_view_ex/cache/mccex/111805482632310595.pitch","songOffsetBegin":0,"songOffsetEnd":191000,"lyricOffset":2}
         * */
        try {
            val jsonObject: JSONObject = JSONObject(payload)
            var lyricPath = ""
            var pitchPath = ""
            var songOffsetBegin = 0
            var songOffsetEnd = 0
            var lyricOffset = 0

            if (jsonObject.has("lyricPath")) {
                lyricPath = jsonObject.getString("lyricPath")
            }
            if (jsonObject.has("pitchPath")) {
                pitchPath = jsonObject.getString("pitchPath")
            }
            if (jsonObject.has("songOffsetBegin")) {
                songOffsetBegin = jsonObject.getInt("songOffsetBegin")
            }
            if (jsonObject.has("songOffsetEnd")) {
                songOffsetEnd = jsonObject.getInt("songOffsetEnd")
            }
            if (jsonObject.has("lyricOffset")) {
                lyricOffset = jsonObject.getInt("lyricOffset")
            }

            mCallback?.onMusicLyricRequest(
                internalSongCode,
                lyricPath,
                pitchPath,
                songOffsetBegin,
                songOffsetEnd,
                lyricOffset
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mMccScoreEventHandler: IScoreEventHandler = object : IScoreEventHandler {
        override fun onPitch(internalSongCode: Long, rawScoreData: RawScoreData) {
            Log.d(
                TAG,
                "mcc onPitch() called with: internalSongCode = [$internalSongCode], rawScoreData = [$rawScoreData]"
            )
            mCallback?.onMusicPitchScore(
                internalSongCode,
                rawScoreData.speakerPitch.toDouble(),
                rawScoreData.pitchScore.toDouble(),
                rawScoreData.progressInMs
            )
        }

        override fun onLineScore(internalSongCode: Long, lineScoreData: LineScoreData) {
            Log.d(
                TAG,
                "mcc onLineScore() called with: internalSongCode = [$internalSongCode], lineScoreData = [$lineScoreData]"
            )
            mCallback?.onMusicLineScore(
                internalSongCode,
                lineScoreData.pitchScore,
                lineScoreData.cumulativePitchScore,
                lineScoreData.index,
                lineScoreData.totalLines
            )
        }
    }

    private val mMediaPlayerObserver: IMediaPlayerObserver = object : IMediaPlayerObserver {
        override fun onPlayerStateChanged(
            state: Constants.MediaPlayerState,
            reason: Constants.MediaPlayerReason
        ) {
            Log.d(TAG, "onPlayerStateChanged: $state $reason")
            if (Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED == state) {
                MusicManager.onMusicOpenCompleted()
            }
            if (Constants.MediaPlayerState.PLAYER_STATE_PLAYING == state) {
                MusicManager.onMusicPlaying()
            } else if (Constants.MediaPlayerState.PLAYER_STATE_PAUSED == state) {
                MusicManager.onMusicPause()
            } else if (Constants.MediaPlayerState.PLAYER_STATE_STOPPED == state) {
                MusicManager.onMusicStop()
            } else if (Constants.MediaPlayerState.PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED == state) {
                MusicManager.onMusicCompleted()
            } else if (Constants.MediaPlayerState.PLAYER_STATE_FAILED == state) {
                MusicManager.onMusicOpenError(Constants.MediaPlayerReason.getValue(reason))
            }
        }

        override fun onPositionChanged(positionMs: Long, timestampMs: Long) {
        }

        override fun onPlayerEvent(
            eventCode: Constants.MediaPlayerEvent,
            elapsedTime: Long,
            message: String
        ) {
        }

        override fun onMetaData(type: Constants.MediaPlayerMetadataType, data: ByteArray) {
        }

        override fun onPlayBufferUpdated(playCachedBuffer: Long) {
        }

        override fun onPreloadEvent(src: String, event: Constants.MediaPlayerPreloadEvent) {
        }

        override fun onAgoraCDNTokenWillExpire() {
        }

        override fun onPlayerSrcInfoChanged(from: SrcInfo, to: SrcInfo) {
        }

        override fun onPlayerInfoUpdated(info: PlayerUpdatedInfo) {
        }

        override fun onPlayerCacheStats(stats: CacheStatistics) {
        }

        override fun onPlayerPlaybackStats(stats: PlayerPlaybackStats) {
        }

        override fun onAudioVolumeIndication(volume: Int) {
        }
    }

    fun init(
        application: Application,
        token: String,
        userId: String,
        callback: IMccCallback
    ) {
        mApplication = application
        mToken = token
        mUserId = userId
        mCallback = callback

        initRtcMcc()
    }

    private fun initRtcMcc() {
        try {
            Log.d(TAG, "RtcEngine version:" + RtcEngine.getSdkVersion())
            val rtcEngineConfig = RtcEngineConfig()
            rtcEngineConfig.mContext = mApplication?.applicationContext
            rtcEngineConfig.mAppId = BuildConfig.APP_ID
            rtcEngineConfig.mChannelProfile =
                io.agora.rtc2.Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
            rtcEngineConfig.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                    Log.d(
                        TAG,
                        "onJoinChannelSuccess channel:$channel uid:$uid elapsed:$elapsed"
                    )
                }

                override fun onLeaveChannel(stats: RtcStats) {
                    Log.d(TAG, "onLeaveChannel")
                }
            }
            rtcEngineConfig.mAudioScenario = io.agora.rtc2.Constants.AUDIO_SCENARIO_CHORUS
            mRtcEngine = RtcEngine.create(rtcEngineConfig)

            mApplication?.applicationContext?.let {
                val prefs: SharedPreferences =
                    it.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
                if (prefs.getBoolean(
                        it.getString(R.string.prefs_key_rtc_audio_dump),
                        false
                    )
                ) {
                    mRtcEngine?.setParameters("{\"rtc.debug.enable\": true}")
                    mRtcEngine?.setParameters("{\"che.audio.apm_dump\": true}")
                }

                if (prefs.getBoolean(it.getString(R.string.prefs_key_rtc_ains), false)) {
                    mRtcEngine?.setParameters(
                        "{\n" +
                                "\n" +
                                "\"che.audio.enable.nsng\":true,\n" +
                                "\"che.audio.ains_mode\":2,\n" +
                                "\"che.audio.ns.mode\":2,\n" +
                                "\"che.audio.nsng.lowerBound\":80,\n" +
                                "\"che.audio.nsng.lowerMask\":50,\n" +
                                "\"che.audio.nsng.statisticalbound\":5,\n" +
                                "\"che.audio.nsng.finallowermask\":30\n" +
                                "}"
                    )
                }
            }


            mRtcEngine?.setParameters("{\"rtc.enable_debug_log\":true}")

            mRtcEngine?.enableAudio()

            mRtcEngine?.setDefaultAudioRoutetoSpeakerphone(true)

            mChannelId =
                Utils.getCurrentDateStr("yyyyMMddHHmmss") + Utils.getRandomString(2)
            var ret = mRtcEngine?.joinChannel(
                KeyCenter.getRtcToken(
                    mChannelId,
                    KeyCenter.userUid
                ),
                mChannelId,
                KeyCenter.userUid,
                object : ChannelMediaOptions() {
                    init {
                        publishMicrophoneTrack = true
                        autoSubscribeAudio = true
                        clientRoleType = io.agora.rtc2.Constants.CLIENT_ROLE_BROADCASTER
                    }
                })
            Log.d(TAG, "joinChannel channelId:$mChannelId ret:$ret")

            mMcc = IAgoraMusicContentCenter.create(mRtcEngine)

            mConfig = MusicContentCenterConfiguration()
            mConfig?.eventHandler = mMccEventHandler
            mConfig?.scoreEventHandler = mMccScoreEventHandler
            mMcc?.initialize(mConfig)

            var jsonObject = JSONObject()
            jsonObject.put("appId", BuildConfig.APP_ID)
            jsonObject.put("token", KeyCenter.getRtmToken2(KeyCenter.userUid))
            jsonObject.put("userId", KeyCenter.userUid)
            jsonObject.put("channelId", mChannelId)
            jsonObject.put("channelUserId", KeyCenter.userUid.toString())
            //jsonObject.put("domain", "api-test.agora.io");
            ret = mMcc?.addVendor(
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_DEFAULT,
                jsonObject.toString()
            )
            Log.d(TAG, "mcc init vendor1: $jsonObject ret:$ret")

            jsonObject = JSONObject()
            jsonObject.put("appId", BuildConfig.VENDOR_2_APP_ID)
            jsonObject.put("appKey", BuildConfig.VENDOR_2_APP_KEY)
            jsonObject.put("token", mToken)
            jsonObject.put("userId", mUserId)
            jsonObject.put("deviceId", Utils.getUuid())
            jsonObject.put("urlTokenExpireTime", 15 * 60)
            jsonObject.put("chargeMode", 2)
            jsonObject.put("channelId", mChannelId)
            jsonObject.put("channelUserId", KeyCenter.userUid.toString())
            ret = mMcc?.addVendor(
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2,
                jsonObject.toString()
            )
            Log.d(TAG, "mcc init vendor2: $jsonObject ret:$ret")

            mAgoraMusicPlayer = mMcc?.createMusicPlayer()

            mAgoraMusicPlayer?.registerPlayerObserver(mMediaPlayerObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        mAgoraMusicPlayer?.unRegisterPlayerObserver(mMediaPlayerObserver)
        mMcc?.unregisterEventHandler(mMccEventHandler)
        mMcc?.unregisterScoreEventHandler(mMccScoreEventHandler)
        mMcc?.destroyMusicPlayer(mAgoraMusicPlayer)
        IAgoraMusicContentCenter.destroy()
        mAgoraMusicPlayer = null
        mMcc = null
        mConfig?.eventHandler = null
        mConfig = null
        mCacheThreadPool.shutdownNow()
    }

    fun open(internalSongCode: Long, startPos: Long) {
        val ret: Int? = mAgoraMusicPlayer?.open(internalSongCode, startPos)
        //int ret = mAgoraMusicPlayer.open("http://agora.fronted.love/yyl.mov",0);
        Log.i(TAG, "open() called ret= $ret")
    }

    fun stop() {
        mAgoraMusicPlayer?.stop()
        mMcc?.stopScore()
    }

    fun pause() {
        mAgoraMusicPlayer?.pause()
    }

    fun resume() {
        mAgoraMusicPlayer?.resume()
    }

    fun seek(time: Long) {
        mAgoraMusicPlayer?.seek(time)
    }

    fun getPlayPosition(): Long {
        return mAgoraMusicPlayer?.playPosition ?: 0L
    }

    fun play() {
        mAgoraMusicPlayer?.play()
    }

    fun preloadMusic(
        songCode: String,
        vendorId: MccConstants.MusicContentCenterVendorId,
        lyricType: MccConstants.LyricSourceType,
        songOptionJson: String
    ) {
        Log.d(
            TAG,
            "mcc preloadMusic call with music songCode:$songCode lyricType:$lyricType vendorId:$vendorId songOptionJson:$songOptionJson"
        )
        try {
            val internalSongCode: Long =
                mMcc?.getInternalSongCode(vendorId, songCode, songOptionJson) ?: 0L
            Log.d(
                TAG,
                "mcc preloadMusic internalSongCode=$internalSongCode"
            )
            val ret = mMcc?.isPreloaded(internalSongCode)
            Log.i(
                TAG,
                "mcc  songCode=$internalSongCode preload state=$ret"
            )

            val requestId: String = mMcc?.preload(internalSongCode) ?: ""
            Log.d(
                TAG,
                "mcc preload song code requestId=$requestId"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startScore(
        vendorId: MccConstants.MusicContentCenterVendorId,
        songCode: String,
        jsonOption: String
    ) {
        Log.d(
            TAG,
            "mcc startScore() called with: vendorId = [$vendorId], songCode = [$songCode], jsonOption = [$jsonOption]"
        )
        try {
            val internalSongCode: Long =
                mMcc?.getInternalSongCode(vendorId, songCode, jsonOption) ?: 0L
            if (internalSongCode == 0L) {
                Log.e(
                    TAG,
                    "getInternalSongCode failed songCode=$songCode"
                )
                return
            }
            val ret: Int = mMcc?.startScore(internalSongCode) ?: -1
            Log.d(
                TAG,
                "mcc startScore() called with $internalSongCode ret = $ret"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setPlayMode(playMode: MccConstants.MusicPlayMode): Int {
        return mAgoraMusicPlayer?.setPlayMode(playMode) ?: -1

    }

    fun clearCache() {
        val caches: Array<MusicCacheInfo> = mMcc?.caches ?: arrayOf()
        for (cache in caches) {
            mMcc?.removeCache(cache.songCode)
        }
    }
}
