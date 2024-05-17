package io.agora.examples.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.agora.examples.karaoke_view_ex.BuildConfig
import io.agora.examples.karaoke_view_ex.R
import io.agora.mccex.utils.Utils
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.audio.AudioParams
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale

object RtcManager : IAudioFrameObserver {
    private const val TAG = "MccEx-RtcManager"

    private var mRtcEngine: RtcEngine? = null
    private var mCallback: RtcCallback? = null
    private var mChannelId: String = ""
    private var mTime: String = ""
    private val SAVE_AUDIO_RECORD_PCM = false

    fun initRtcEngine(context: Context, rtcCallback: RtcCallback) {
        mCallback = rtcCallback
        try {
            Log.d(TAG, "RtcEngine version:" + RtcEngine.getSdkVersion())
            val rtcEngineConfig = RtcEngineConfig()
            rtcEngineConfig.mContext = context
            rtcEngineConfig.mAppId = BuildConfig.APP_ID
            rtcEngineConfig.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
            rtcEngineConfig.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                    Log.d(TAG, "onJoinChannelSuccess channel:$channel uid:$uid elapsed:$elapsed")
                    mCallback?.onJoinChannelSuccess(channel, uid, elapsed)
                }

                override fun onLeaveChannel(stats: RtcStats) {
                    Log.d(TAG, "onLeaveChannel")
                    mCallback?.onLeaveChannel(stats)
                }

                override fun onLocalAudioStats(stats: LocalAudioStats?) {
                    super.onLocalAudioStats(stats)
                }

                override fun onLocalAudioStateChanged(state: Int, error: Int) {
                    super.onLocalAudioStateChanged(state, error)
                    Log.d(TAG, "onLocalAudioStateChanged state:$state error:$error")
                    if (Constants.LOCAL_AUDIO_STREAM_STATE_RECORDING == state) {
                        mCallback?.onUnMuteSuccess()
                    } else if (Constants.LOCAL_AUDIO_STREAM_STATE_STOPPED == state) {
                        mCallback?.onMuteSuccess()
                    }
                }

                override fun onAudioVolumeIndication(
                    speakers: Array<out AudioVolumeInfo>?,
                    totalVolume: Int
                ) {
                    super.onAudioVolumeIndication(speakers, totalVolume)
                    mCallback?.onAudioVolumeIndication(speakers, totalVolume)
                }
            }
            rtcEngineConfig.mAudioScenario = Constants.AUDIO_SCENARIO_DEFAULT
            mRtcEngine = RtcEngine.create(rtcEngineConfig)

            val prefs: SharedPreferences =
                context.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
            if (prefs.getBoolean(context.getString(R.string.prefs_key_rtc_audio_dump), false)) {
                mRtcEngine?.setParameters("{\"rtc.debug.enable\": true}")
                mRtcEngine?.setParameters("{\"che.audio.apm_dump\": true}")
            }

            mRtcEngine?.setParameters("{\"rtc.enable_debug_log\":true}")

            mRtcEngine?.enableAudio()

            mRtcEngine?.setDefaultAudioRoutetoSpeakerphone(true)

//            mRtcEngine?.setRecordingAudioFrameParameters(
//                16000,
//                1,
//                Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY,
//                640
//            )

            //min 50ms
            mRtcEngine?.enableAudioVolumeIndication(
                50,
                3,
                true
            )

            mChannelId = Utils.getCurrentDateStr("yyyyMMddHHmmss") + Utils.getRandomString(2)
            val ret = mRtcEngine?.joinChannel(
                KeyCenter.getRtcToken(
                    mChannelId,
                    KeyCenter.getUserUid()
                ),
                mChannelId,
                KeyCenter.getUserUid(),
                object : ChannelMediaOptions() {
                    init {
                        publishMicrophoneTrack = true
                        autoSubscribeAudio = true
                        clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                    }
                })
            Log.d(TAG, "initRtcEngine ret:$ret")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "initRtcEngine error:" + e.message)
        }
    }

    override fun onRecordAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsync_type: Int
    ): Boolean {
        val length = buffer!!.remaining()
        val origin = ByteArray(length)
        buffer[origin]
        buffer.flip()
        if (SAVE_AUDIO_RECORD_PCM) {
            try {
                val fos = FileOutputStream(
                    "/sdcard/Android/Data/io.agora.mccex_demo/cache/audio_" + mTime + ".pcm",
                    true
                )
                fos.write(origin)
                fos.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        return true
    }

    override fun onPlaybackAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsync_type: Int
    ): Boolean {

        return true
    }

    override fun onMixedAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsync_type: Int
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun onEarMonitoringAudioFrame(
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsync_type: Int
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun onPlaybackAudioFrameBeforeMixing(
        channelId: String?,
        userId: Int,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsync_type: Int
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun onPublishAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsync_type: Int
    ): Boolean {
        TODO("Not yet implemented")
    }


    override fun getObservedAudioFramePosition(): Int {
        TODO("Not yet implemented")
    }

    override fun getRecordAudioParams(): AudioParams {
        TODO("Not yet implemented")
    }

    override fun getPlaybackAudioParams(): AudioParams {
        TODO("Not yet implemented")
    }

    override fun getMixedAudioParams(): AudioParams {
        TODO("Not yet implemented")
    }

    override fun getEarMonitoringAudioParams(): AudioParams {
        TODO("Not yet implemented")
    }

    override fun getPublishAudioParams(): AudioParams {
        TODO("Not yet implemented")
    }

    fun mute(enable: Boolean) {
        val ret = mRtcEngine?.enableLocalAudio(!enable)
        Log.d(TAG, "mute enable:$enable ret:$ret")
        if (SAVE_AUDIO_RECORD_PCM) {
            if (!enable) {
                val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                mTime = format.format(System.currentTimeMillis())
            }
        }
    }

    fun leaveChannel() {
        Log.d(TAG, "RtcManager leaveChannel")
        mRtcEngine?.registerAudioFrameObserver(null)
        mRtcEngine?.leaveChannel()
    }

    fun destroy() {
        Log.d(TAG, "RtcManager destroy")
        RtcEngine.destroy()
    }


    fun getChannelId(): String {
        return mChannelId
    }

    @JvmStatic
    fun getRtcEngine(): RtcEngine {
        return mRtcEngine!!
    }

    interface RtcCallback {
        fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int)
        fun onLeaveChannel(stats: IRtcEngineEventHandler.RtcStats)
        fun onMuteSuccess() {

        }

        fun onUnMuteSuccess() {

        }

        fun onAudioVolumeIndication(
            speakers: Array<out IRtcEngineEventHandler.AudioVolumeInfo>?,
            totalVolume: Int
        ) {

        }
    }
}
