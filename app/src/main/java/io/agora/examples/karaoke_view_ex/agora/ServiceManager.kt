package io.agora.examples.karaoke_view_ex.agora

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.agora.examples.karaoke_view_ex.agora.tme.TmePlaybackController
import io.agora.karaoke_view_ex.model.LyricModel
import io.agora.karaoke_view_ex.tme.*
import java.util.UUID
import android.util.Log
import io.agora.examples.karaoke_view_ex.agora.RtcManager.getRtcEngine
import io.agora.examples.utils.LyricsResourcePool
import io.agora.examples.utils.ServiceType
import io.agora.karaoke_view_ex.downloader.LyricsFileDownloader
import io.agora.musiccontentcenter.IAgoraMusicPlayer.MusicPlayMode

object ServiceManager : MccManager.MccCallback {
    private val TAG =
        io.agora.karaoke_view_ex.constants.Constants.TAG + "-" + ServiceManager::class.java.simpleName
    private var mMccManager: MccManager? = null
    private var mServiceType: ServiceType? = ServiceType.MCC
    @Volatile private var mTmeSongs: List<MccManager.TmeSong> = emptyList()
    private var mCurrentSongCodeIndex = 0
    private var mIsOriginal = true
    private var mServiceCallback: ServiceCallback? = null
    private var mLyricType: Int = 0
    private val tmeMain = Handler(Looper.getMainLooper())
    private var tmeController: TmePlaybackController<LyricModel>? = null
    private var tmePreparation: TMEScoringPreparation? = null
    private var tmeListListener: TmeListListener? = null
    private var tmeListLoading = false
    private var tmeListError: String? = null
    private var serviceContext: Context? = null

    fun interface TmeListListener {
        fun onSongs(songs: List<MccManager.TmeSong>, loading: Boolean, error: String?)
    }
    fun attachTmeList(listener: TmeListListener) { tmeListListener = listener; publishTmeList() }
    fun detachTmeList(listener: TmeListListener) { if (tmeListListener === listener) tmeListListener = null }
    private fun publishTmeList() { tmeListListener?.onSongs(mTmeSongs, tmeListLoading, tmeListError) }
    fun retryTmeSongs() {
        if (mServiceType != ServiceType.TME) return
        tmeListLoading = true; tmeListError = null; publishTmeList()
        if (mMccManager?.isTmeReady == true) mMccManager?.requestTmeSongs()
        else {
            val context = serviceContext
            if (context == null) {
                tmeListLoading = false; tmeListError = "TME service is unavailable"; publishTmeList()
                return
            }
            mMccManager?.destroy()
            mMccManager = null
            initMcc(context, true)
        }
    }
    fun getTmeController(sessionId: String): TmePlaybackController<LyricModel>? =
        tmeController?.takeIf { it.sessionId == sessionId }

    fun startTmeSong(context: Context, songId: String, songName: String): String? {
        val manager = mMccManager ?: return null
        if (mServiceType != ServiceType.TME) return null
        endTmeSong()
        mTmeSongs.indexOfFirst { it.songId == songId }.takeIf { it >= 0 }?.let { mCurrentSongCodeIndex = it }
        mIsOriginal = true
        val preparation = TMEScoringPreparation(context.applicationContext.cacheDir)
        preparation.allowLyricsOnly = true
        tmePreparation = preparation
        val media = object : TmePlaybackController.MediaPort {
            override fun preload(sessionId: String, songId: String) = manager.preloadTmeSession(sessionId, songId)
            override fun open(sessionId: String, songCode: Long) = manager.openTmeSession(sessionId, songCode)
            override fun play() = manager.playTme()
            override fun pause() = manager.pauseTme()
            override fun resume() = manager.resumeTme()
            override fun seek(positionMs: Long) = manager.seekTme(positionMs)
            override fun position() = manager.tmePlayPosition
            override fun release() { manager.releaseTmePlayer() }
        }
        val resources = object : TmePlaybackController.PreparationPort<LyricModel> {
            override fun start(songId: String, listener: TmePlaybackController.PreparationListener<LyricModel>) {
                preparation.listener = object : TMEScoringPreparationListener {
                    override fun onStage(stage: TMEScoringPreparationStage) {
                        listener.onStage(TmePlaybackController.Stage.valueOf(stage.name))
                    }
                    override fun onSuccess(result: TMEScoringPreparationResult) {
                        listener.onReady(result.lyricModel, result.scorableLineCount)
                    }
                    override fun onFailure(error: TMEScoringPreparationError) { listener.onFailure(error.category.name) }
                    override fun onLyricsOnly(model: LyricModel, error: TMEScoringPreparationError) {
                        listener.onUnscored(model, error.category.name)
                    }
                }
                preparation.prepare(songId, TMERequestSender { manager.sendTmeRequest(it) })
            }
            override fun cancel() { preparation.cancel() }
        }
        val scheduler = object : TmePlaybackController.Scheduler {
            override fun schedule(delayMs: Long, action: () -> Unit): TmePlaybackController.Cancel {
                val runnable = Runnable { action() }
                tmeMain.postDelayed(runnable, delayMs)
                return TmePlaybackController.Cancel { tmeMain.removeCallbacks(runnable) }
            }
        }
        val id = UUID.randomUUID().toString()
        val controller = TmePlaybackController(id, songId, songName, media, resources, scheduler)
        tmeController = controller
        tmeMain.post { if (tmeController === controller) controller.start() }
        return id
    }
    fun endTmeSong(sessionId: String? = null) {
        if (sessionId != null && tmeController?.sessionId != sessionId) return
        val old = tmeController
        tmeController = null
        old?.close()
        tmePreparation?.close(); tmePreparation = null
    }

    fun currentTmeSong(): MccManager.TmeSong? = mTmeSongs.getOrNull(mCurrentSongCodeIndex)

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
        serviceContext = context.applicationContext
        mServiceCallback = serviceCallback
        if (mMccManager != null) return
        mCurrentSongCodeIndex = 0
        mTmeSongs = emptyList()
        mServiceCallback = serviceCallback
        initMcc(context, ServiceType.TME == mServiceType)
    }

    private fun initMcc(context: Context, isTme: Boolean) {
        mMccManager = MccManager(context, this, isTme)
        if (isTme) {
            tmeListLoading = true; tmeListError = null
            mMccManager?.setTmeSessionCallback(object : MccManager.TmeSessionCallback {
                override fun onExtResponse(requestId: String, jsonOption: String, httpCode: Int, response: String): Boolean =
                    tmePreparation?.handleExtResponse(requestId, jsonOption, httpCode, response) ?: false
                override fun onPreload(sessionId: String, requestId: String, songCode: Long, percent: Int, status: Int, errorCode: Int) {
                    tmeController?.onPreload(sessionId, requestId, songCode, percent, status, errorCode)
                }
                override fun onPlayer(sessionId: String, event: TmePlaybackController.PlayerEvent) {
                    tmeController?.onPlayer(sessionId, event)
                }
            })
        }
        mMccManager?.init(getRtcEngine())
    }

    fun seek(position: Long) {
        if (ServiceType.TME == mServiceType) {
            tmeController?.seek(position)
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.seek(position)
            mMccManager?.updateMusicPosition(position)
        }
    }

    fun destroy() {
        endTmeSong()
        tmeListListener = null; mTmeSongs = emptyList(); tmeListError = null; tmeListLoading = false
        mServiceCallback = null
        serviceContext = null
        Log.i(TAG, "destroyService")
        mMccManager?.destroy()
        mMccManager = null
    }

    fun openMusic() {
        Log.i(TAG, "playMusic")
        if (ServiceType.TME == mServiceType) {
            mMccManager?.currentTmeSongCode?.takeIf { it > 0 }?.let { mMccManager?.openMusic(it) }
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.openMusic(LyricsResourcePool.asMusicList()[mCurrentSongCodeIndex].songCode)
        }
    }

    fun switchMusic(): Boolean {
        if (ServiceType.TME == mServiceType) {
            if (mTmeSongs.isEmpty()) {
                mServiceCallback?.onTmeError("TME song list is not ready")
                return false
            }
            mCurrentSongCodeIndex = (mCurrentSongCodeIndex + 1) % mTmeSongs.size
            mServiceCallback?.onTmeSongChanged("${mTmeSongs[mCurrentSongCodeIndex].name} (${mCurrentSongCodeIndex + 1}/${mTmeSongs.size})")
        } else if (ServiceType.MCC == mServiceType) {
            mCurrentSongCodeIndex++
            if (mCurrentSongCodeIndex >= LyricsResourcePool.asMusicList().size) {
                mCurrentSongCodeIndex = 0
            }
        }
        return true
    }

    fun selectMccSong(position: Int): Boolean {
        if (mServiceType != ServiceType.MCC || position !in LyricsResourcePool.asMusicList().indices) return false
        mCurrentSongCodeIndex = position
        return true
    }

    fun play(): Boolean {
        if (ServiceType.TME == mServiceType) {
            val song = mTmeSongs.getOrNull(mCurrentSongCodeIndex)
            if (song == null) {
                mMccManager?.requestTmeSongs()
                mServiceCallback?.onTmeError("Loading TME songs")
                return false
            }
            val preloaded = mMccManager?.preloadTmeMusic(song) ?: false
            if (preloaded) mIsOriginal = true
            return preloaded
        } else if (ServiceType.MCC == mServiceType) {
            if (mMccManager?.setPlayMode(MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL) != 0) return false
            mIsOriginal = true
            mMccManager?.preloadMusic(
                LyricsResourcePool.asMusicList()[mCurrentSongCodeIndex].songCode,
                mLyricType
            )
            return true
        }
        return false
    }

    fun stop() {
        if (ServiceType.TME == mServiceType) {
            endTmeSong()
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.stop()
        }
    }

    fun doPlayOriginal() {
        val mode = if (mIsOriginal) MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY
            else MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL
        if (mMccManager?.setPlayMode(mode) == 0) mIsOriginal = !mIsOriginal
    }

    fun isOriginalPlay(): Boolean {
        return mIsOriginal
    }

    fun pause() {
        if (ServiceType.TME == mServiceType) {
            tmeController?.pause()
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.pause()
        }
    }

    fun resume() {
        if (ServiceType.TME == mServiceType) {
            tmeController?.resume()
        } else if (ServiceType.MCC == mServiceType) {
            mMccManager?.resume()
        }
    }

    fun clearCache(context: Context) {
        mMccManager?.clearCache()
        LyricsFileDownloader.getInstance(context).cleanAll()
    }

    fun updateSpeakerPitch(speakerPitch: Double) {
        if (ServiceType.TME == mServiceType) {
            val current = tmeController
            tmeMain.post { if (current != null && tmeController === current) current.onLocalPitch(speakerPitch) }
            return
        }
        if (ServiceType.MCC == mServiceType) {
            mMccManager?.getPlayPosition()?.let { mServiceCallback?.onMusicPitch(speakerPitch, it) }
        }
    }


    //////////////////////////// MccManager.MccCallback ////////////////////////////
    override fun onMusicLyricRequest(songCode: Long, lyricUrl: String?, pitchUrl: String?) {
        mServiceCallback?.onMusicLyricRequest(songCode, lyricUrl, pitchUrl)
    }

    override fun onTmeSongsLoaded(songs: MutableList<MccManager.TmeSong>?) {
        if (ServiceType.TME != mServiceType || songs.isNullOrEmpty()) return
        mTmeSongs = songs.toList()
        mCurrentSongCodeIndex = songs.indexOfFirst { it.songId == tmeController?.songId }.coerceAtLeast(0)
        tmeListLoading = false; tmeListError = null; publishTmeList()
        mServiceCallback?.onTmeSongChanged("${mTmeSongs[mCurrentSongCodeIndex].name} (${mCurrentSongCodeIndex + 1}/${mTmeSongs.size})")
    }

    override fun onTmeError(message: String?) {
        if (ServiceType.TME == mServiceType) {
            tmeListLoading = false; tmeListError = message ?: "TME song list failed"; publishTmeList()
            mServiceCallback?.onTmeError(message ?: "TME error")
        }
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

    interface ServiceCallback {
        fun onTmeSongChanged(songName: String) {
        }

        fun onTmeError(message: String) {
        }
        fun onMusicLyricRequest(
            songCode: Long,
            lyricUrl: String?,
            pitchUrl: String?
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

    }
}
