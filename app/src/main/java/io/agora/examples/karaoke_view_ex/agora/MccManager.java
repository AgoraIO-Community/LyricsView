package io.agora.examples.karaoke_view_ex.agora;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import io.agora.examples.karaoke_view_ex.BuildConfig;
import io.agora.examples.utils.KeyCenter;
import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.mccex.constants.MusicPlayMode;
import io.agora.mediaplayer.IMediaPlayerObserver;
import io.agora.mediaplayer.data.PlayerUpdatedInfo;
import io.agora.mediaplayer.data.SrcInfo;
import io.agora.musiccontentcenter.IAgoraMusicContentCenter;
import io.agora.musiccontentcenter.IAgoraMusicPlayer;
import io.agora.musiccontentcenter.IMusicContentCenterEventHandler;
import io.agora.musiccontentcenter.Music;
import io.agora.musiccontentcenter.MusicCacheInfo;
import io.agora.musiccontentcenter.MusicChartInfo;
import io.agora.musiccontentcenter.MusicContentCenterConfiguration;
import io.agora.rtc2.RtcEngine;
import io.agora.examples.karaoke_view_ex.agora.tme.TmePlaybackController;
import io.agora.karaoke_view_ex.tme.TMERequestManager;
import io.agora.karaoke_view_ex.tme.TMEParserAdapter;
import io.agora.karaoke_view_ex.tme.TMESongsResult;
import io.agora.karaoke_view_ex.tme.TMESongDetail;
import io.agora.karaoke_view_ex.tme.TMEParseError;

public class MccManager {
    private static final String TAG = Constants.TAG + "-MCCManager";
    private Context mContext;
    private IAgoraMusicContentCenter mMcc;
    private IAgoraMusicPlayer mAgoraMusicPlayer;
    private MusicContentCenterConfiguration mConfig;
    private final MccCallback mCallback;
    private final boolean mIsTme;
    private volatile TmeSong mCurrentTmeSong;
    private volatile long mCurrentTmeSongCode;
    private volatile boolean mTmeListPending;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private TMERequestManager mTmeListManager;
    private String mTmeListRequestId;
    private String mTmeSessionId;
    private String mTmePreloadRequestId;
    private IMediaPlayerObserver mTmePlayerObserver;
    private TmeSessionCallback mTmeSessionCallback;
    private boolean mDestroyed;
    private final Runnable mListTimeout;
    private final TMEParserAdapter mListDelegate = new TMEParserAdapter() {
        @Override public void onSongs(String id, TMESongsResult result) {
            if (mDestroyed || !id.equals(mTmeListRequestId)) return;
            finishListRequest();
            List<TmeSong> songs = new ArrayList<>();
            for (TMESongDetail song : result.getSongList()) {
                if (song.getSongId().isEmpty() || (song.getStatus() != null && song.getStatus() != 1)
                        || (song.getGrantStatus() != null && song.getGrantStatus() != 1)) continue;
                songs.add(new TmeSong(song.getSongId(), song.getSongName(), "", "", ""));
                if (songs.size() == 20) break;
            }
            if (songs.isEmpty()) mCallback.onTmeError("TME song list is empty");
            else mCallback.onTmeSongsLoaded(songs);
        }
        @Override public void onParseError(String id, String option, String body, TMEParseError error) {
            if (mDestroyed || !id.equals(mTmeListRequestId)) return;
            finishListRequest();
            mCallback.onTmeError("TME song list request failed");
        }
    };
    private ScheduledExecutorService mScheduledExecutorService;
    private long mCurrentMusicPosition;
    private final static int MUSIC_POSITION_UPDATE_INTERVAL = 20;

    private static volatile Status mStatus = Status.IDLE;
    private int mLyricType = 0;

    enum Status {
        IDLE(0), Opened(1), Started(2), Paused(3), Stopped(4);

        int value;

        Status(int value) {
            this.value = value;
        }

        public boolean isAtLeast(@NonNull Status state) {
            return compareTo(state) >= 0;
        }
    }

    private final IMusicContentCenterEventHandler mMccEventHandler = new IMusicContentCenterEventHandler() {
        @Override
        public void onPreLoadEvent(String requestId, long songCode, int percent, String lyricUrl, int status, int errorCode) {
            if (mIsTme) {
                // Always enqueue, even on main: preload may synchronously invoke its callback.
                mMain.post(() -> {
                    if (mDestroyed || mTmeSessionCallback == null || mTmeSessionId == null
                            || !requestId.equals(mTmePreloadRequestId) || songCode != mCurrentTmeSongCode) return;
                    mTmeSessionCallback.onPreload(mTmeSessionId, requestId, songCode, percent, status, errorCode);
                });
                return;
            }
            Log.d(TAG, "onPreLoadEvent percent:" + percent + " status:" + status + " errorCode:" + errorCode);
            mCallback.onMusicPreloadResult(songCode, percent);
            if (status == 0 && percent == 100) {
                mMcc.getLyric(songCode, mLyricType);
            }
        }

        @Override
        public void onMusicCollectionResult(String requestId, int page, int pageSize, int total, Music[] list, int errorCode) {

        }

        @Override
        public void onMusicChartsResult(String requestId, MusicChartInfo[] list, int errorCode) {

        }

        @Override
        public void onLyricResult(String requestId, long songCode, String lyricUrl, int errorCode) {
            if (mIsTme) return;
            Log.d(TAG, "onLyricResult errorCode:" + errorCode);
            mCallback.onMusicLyricRequest(songCode, lyricUrl, null);
        }

        @Override
        public void onSongSimpleInfoResult(String requestId, long songCode, String simpleInfo, int errorCode) {

        }

        @Override
        public void onExtResponse(String requestId, String jsonOption, int httpCode, String response) {
            if (!mIsTme) {
                return;
            }
            mMain.post(() -> {
                if (mDestroyed) return;
                if (mTmeSessionCallback != null && mTmeSessionCallback.onExtResponse(requestId, jsonOption, httpCode, response)) return;
                if (requestId.equals(mTmeListRequestId)) mTmeListManager.onExtResponse(requestId, jsonOption, httpCode, response);
            });
        }
    };

    private void finishListRequest() {
        mTmeListPending = false;
        mTmeListRequestId = null;
        mMain.removeCallbacks(mListTimeout);
    }

    private final IMediaPlayerObserver mMediaPlayerObserver = createMediaPlayerObserver(null);

    private IMediaPlayerObserver createMediaPlayerObserver(final String sessionId) {
        return new IMediaPlayerObserver() {
        @Override
        public void onPlayerStateChanged(io.agora.mediaplayer.Constants.MediaPlayerState state, io.agora.mediaplayer.Constants.MediaPlayerReason error) {
            if (sessionId != null) {
                mMain.post(() -> {
                    if (mDestroyed || !sessionId.equals(mTmeSessionId) || mTmeSessionCallback == null) return;
                    TmePlaybackController.PlayerEvent event = null;
                    switch (state) {
                        case PLAYER_STATE_OPEN_COMPLETED: event = TmePlaybackController.PlayerEvent.OPENED; break;
                        case PLAYER_STATE_PLAYING: event = TmePlaybackController.PlayerEvent.PLAYING; break;
                        case PLAYER_STATE_PAUSED: event = TmePlaybackController.PlayerEvent.PAUSED; break;
                        case PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED: event = TmePlaybackController.PlayerEvent.COMPLETED; break;
                        case PLAYER_STATE_FAILED: event = TmePlaybackController.PlayerEvent.FAILED; break;
                        default: break;
                    }
                    if (event != null) mTmeSessionCallback.onPlayer(sessionId, event);
                });
                return;
            }
            Log.d(TAG, "onPlayerStateChanged: " + state + " " + error);
            if (io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED == state) {
                if (mStatus == Status.IDLE) {
                    onMusicOpenCompleted();
                }
            }
            if (io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYING == state) {
                onMusicPlaying();
            } else if (io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PAUSED == state) {
                onMusicPause();
            } else if (io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_STOPPED == state) {
                onMusicStop();
            } else if (io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED == state) {
                onMusicCompleted();
            } else if (io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_FAILED == state) {
                onMusicOpenError(error.ordinal());
            }
        }

        @Override
        public void onPositionChanged(long positionMs, long timestampMs) {

        }

        @Override
        public void onPlayerEvent(io.agora.mediaplayer.Constants.MediaPlayerEvent eventCode, long elapsedTime, String message) {

        }

        @Override
        public void onMetaData(io.agora.mediaplayer.Constants.MediaPlayerMetadataType type, byte[] data) {

        }

        @Override
        public void onPlayBufferUpdated(long playCachedBuffer) {

        }

        @Override
        public void onPreloadEvent(String src, io.agora.mediaplayer.Constants.MediaPlayerPreloadEvent event) {

        }

        @Override
        public void onAgoraCDNTokenWillExpire() {

        }

        @Override
        public void onPlayerSrcInfoChanged(SrcInfo from, SrcInfo to) {

        }

        @Override
        public void onPlayerInfoUpdated(PlayerUpdatedInfo info) {

        }

        @Override
        public void onPlayerCacheStats(io.agora.mediaplayer.data.CacheStatistics stats) {
        }

        @Override
        public void onPlayerPlaybackStats(io.agora.mediaplayer.data.PlayerPlaybackStats stats) {
        }

        @Override
        public void onAudioVolumeIndication(int volume) {

        }
        };
    }

    public MccManager(Context context, MccCallback callback, boolean isTme) {
        mContext = context;
        mCallback = callback;
        mIsTme = isTme;
        mListTimeout = () -> {
            if (!mDestroyed && mTmeListPending) {
                mTmeListPending = false;
                mTmeListRequestId = null;
                mTmeListManager.stop();
                mCallback.onTmeError("TME song list timed out");
            }
        };
        initData();
    }

    private void initData() {
        mScheduledExecutorService = Executors.newScheduledThreadPool(5);
    }

    public void init(RtcEngine rtcEngine) {
        try {
            mMcc = IAgoraMusicContentCenter.create(rtcEngine);

            mConfig = new MusicContentCenterConfiguration();
            mConfig.appId = BuildConfig.APP_ID;
            mConfig.mccUid = KeyCenter.getUserUid();
            mConfig.token = KeyCenter.getRtmToken(KeyCenter.getUserUid());
            if (mIsTme && !BuildConfig.TME_MCC_DOMAIN.isEmpty()) {
                mConfig.mccDomain = BuildConfig.TME_MCC_DOMAIN;
            }
            mConfig.eventHandler = mMccEventHandler;
            int result = mMcc.initialize(mConfig);
            if (result != 0) {
                mCallback.onTmeError("Music content center initialization failed: " + result);
                return;
            }

            if (mIsTme) {
                mTmeListManager = new TMERequestManager(option -> mMcc.sendExtRequest(option));
                mTmeListManager.setDelegate(mListDelegate);
                requestTmeSongs();
            } else {
                mAgoraMusicPlayer = mMcc.createMusicPlayer();
                mAgoraMusicPlayer.registerPlayerObserver(mMediaPlayerObserver);
            }
        } catch (Exception e) {
            Log.e(TAG, "Music content center initialization failed");
            mCallback.onTmeError("Music content center initialization failed");
        }

    }

    public void destroy() {
        mDestroyed = true;
        mMain.removeCallbacksAndMessages(null);
        if (mTmeListManager != null) mTmeListManager.close();
        if (mIsTme) releaseTmePlayer();
        mCurrentTmeSong = null;
        mCurrentTmeSongCode = 0;
        mTmeListPending = false;
        if (mAgoraMusicPlayer != null) {
            mAgoraMusicPlayer.unRegisterPlayerObserver(mMediaPlayerObserver);
            mAgoraMusicPlayer.destroy();
        }
        if (mMcc != null) {
            mMcc.unregisterEventHandler();
            IAgoraMusicContentCenter.destroy();
        }
        mAgoraMusicPlayer = null;
        mMcc = null;
        if (mConfig != null) {
            mConfig.eventHandler = null;
        }
        mConfig = null;
        if (mScheduledExecutorService != null) mScheduledExecutorService.shutdownNow();
    }

    public boolean isTmeReady() { return !mDestroyed && mMcc != null && mTmeListManager != null; }

    public void requestTmeSongs() {
        if (mDestroyed || mTmeListPending || mMcc == null || mTmeListManager == null) {
            return;
        }
        mTmeListPending = true;
        try {
            JSONObject option = new JSONObject();
            option.put("vendorId", IAgoraMusicContentCenter.MUSIC_CONTENT_CENTER_VENDOR_ID_TME);
            option.put("actionType", "songs");
            JSONObject parameters = new JSONObject();
            parameters.put("queryInfo", "");
            parameters.put("limit", 20);
            option.put("actionParameter", parameters);
            String requestId = mTmeListManager.sendRequest(option.toString());
            mTmeListRequestId = requestId;
            if (requestId == null || requestId.isEmpty()) {
                mTmeListPending = false;
                mCallback.onTmeError("TME song request was not submitted");
            } else {
                mMain.postDelayed(mListTimeout, 30000);
            }
        } catch (Exception e) {
            mTmeListPending = false;
            mCallback.onTmeError("TME song request failed");
        }
    }

    public boolean preloadTmeMusic(TmeSong song) {
        return preloadTmeSession("legacy", song.songId) != null;
    }

    public TmePlaybackController.PreloadRequest preloadTmeSession(String sessionId, String originalSongId) {
        releaseTmePlayer();
        mTmeSessionId = sessionId;
        try {
            JSONObject parameters = new JSONObject();
            parameters.put("songCode", originalSongId);
            parameters.put("fileType", "mkv");
            JSONObject option = new JSONObject();
            option.put("vendorId", IAgoraMusicContentCenter.MUSIC_CONTENT_CENTER_VENDOR_ID_TME);
            option.put("actionParameter", parameters);
            long songCode = mMcc.getInternalSongCode(0, option.toString());
            if (songCode <= 0) {
                return null;
            }
            mCurrentTmeSongCode = songCode;
            mLyricType = 1;
            String requestId = mMcc.preload(songCode);
            mTmePreloadRequestId = requestId;
            if (requestId == null || requestId.isEmpty()) {
                return null;
            }
            return new TmePlaybackController.PreloadRequest(songCode, requestId);
        } catch (Exception e) {
            return null;
        }
    }

    public void setTmeSessionCallback(TmeSessionCallback callback) { mTmeSessionCallback = callback; }
    public String sendTmeRequest(String option) { return mMcc == null ? null : mMcc.sendExtRequest(option); }
    public int openTmeSession(String sessionId, long songCode) {
        if (!sessionId.equals(mTmeSessionId) || mMcc == null || songCode != mCurrentTmeSongCode) return -1;
        try {
            mAgoraMusicPlayer = mMcc.createMusicPlayer();
            if (mAgoraMusicPlayer == null) return -1;
            mTmePlayerObserver = createMediaPlayerObserver(sessionId);
            mAgoraMusicPlayer.registerPlayerObserver(mTmePlayerObserver);
            RtcManager.INSTANCE.enableMediaPlayerOptions(mAgoraMusicPlayer.getMediaPlayerId(), true);
            return mAgoraMusicPlayer.open(songCode, 0);
        } catch (Exception e) { return -1; }
    }
    public int playTme() { return mAgoraMusicPlayer == null ? -1 : mAgoraMusicPlayer.play(); }
    public int pauseTme() { return mAgoraMusicPlayer == null ? -1 : mAgoraMusicPlayer.pause(); }
    public int resumeTme() { return mAgoraMusicPlayer == null ? -1 : mAgoraMusicPlayer.resume(); }
    public long getTmePlayPosition() { return mAgoraMusicPlayer == null ? 0 : mAgoraMusicPlayer.getPlayPosition(); }
    public void releaseTmePlayer() {
        mTmeSessionId = null;
        mTmePreloadRequestId = null;
        mCurrentTmeSongCode = 0;
        IAgoraMusicPlayer player = mAgoraMusicPlayer;
        mAgoraMusicPlayer = null;
        if (player == null) return;
        RtcManager.INSTANCE.enableMediaPlayerOptions(player.getMediaPlayerId(), false);
        if (mTmePlayerObserver != null) player.unRegisterPlayerObserver(mTmePlayerObserver);
        mTmePlayerObserver = null;
        player.stop();
        player.destroy();
    }

    public interface TmeSessionCallback {
        boolean onExtResponse(String requestId, String jsonOption, int httpCode, String response);
        void onPreload(String sessionId, String requestId, long songCode, int percent, int status, int errorCode);
        void onPlayer(String sessionId, TmePlaybackController.PlayerEvent event);
    }

    public long getCurrentTmeSongCode() {
        return mCurrentTmeSongCode;
    }

    private void onMusicOpenCompleted() {
        Log.i(TAG, "onMusicOpenCompleted() called");
        mAgoraMusicPlayer.play();
        mStatus = Status.Opened;
        startDisplayLrc();
    }

    private void onMusicPlaying() {
        Log.i(TAG, "onMusicPlaying() called");
        mStatus = Status.Started;
        mCallback.onMusicPlaying();
    }

    private void onMusicPause() {
        Log.i(TAG, "onMusicPause() called");
        mStatus = Status.Paused;
    }

    private void onMusicStop() {
        Log.i(TAG, "onMusicStop() called");
        if (mStatus != Status.IDLE) {
            mStatus = Status.Stopped;
        }
        mCallback.onMusicStop();
        stopDisplayLrc();
        reset();
    }

    private void onMusicOpenError(int error) {
        Log.i(TAG, "onMusicOpenError() called with: error = " + error);
    }

    private void onMusicCompleted() {
        Log.i(TAG, "onMusicCompleted() called");
        mCallback.onMusicStop();
        stopDisplayLrc();
        reset();
    }

    public void openMusic(long songCode) {
        int ret = mAgoraMusicPlayer.open(songCode, 0);
        //int ret = mAgoraMusicPlayer.open("http://agora.fronted.love/yyl.mov",0);
        Log.i(TAG, "open() called ret= " + ret);
    }

    public void stop() {
        Log.i(TAG, "stop()  called");
        if (mIsTme) {
            mCurrentTmeSong = null;
            mCurrentTmeSongCode = 0;
        }
        if (mStatus == Status.IDLE) {
            return;
        }
        mStatus = Status.IDLE;
        mAgoraMusicPlayer.stop();
    }

    public void pause() {
        Log.i(TAG, "pause() called");
        if (!mStatus.isAtLeast(Status.Started)) {
            return;
        }
        mStatus = Status.Paused;
        mAgoraMusicPlayer.pause();
    }

    public void resume() {
        Log.i(TAG, "resume() called");
        if (!mStatus.isAtLeast(Status.Started)) {
            return;
        }
        mStatus = Status.Started;
        mAgoraMusicPlayer.resume();
    }

    public void seek(long time) {
        mAgoraMusicPlayer.seek(time);
    }

    public void preloadMusic(final long songCode, final int lyricType) {
        Log.i(TAG, "preloadMusic call with music songCode:" + songCode + " lyricType:" + lyricType);
        mLyricType = lyricType;
        try {
            if (0 == mMcc.isPreloaded(songCode)) {
                Log.i(TAG, "mcc is preloaded songCode=" + songCode);
                //0 xml  1 lrc
                mMcc.getLyric(songCode, mLyricType);
            } else {
                String requestId = mMcc.preload(songCode);
                Log.i(TAG, "preload song code requestId=" + requestId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("DiscouragedApi")
    private void startDisplayLrc() {
        maybeCreateNewScheduledService();
        mCurrentMusicPosition = -1;
        mScheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (mStatus == Status.Started) {
                    if (-1 == mCurrentMusicPosition || mCurrentMusicPosition % 1000 < MUSIC_POSITION_UPDATE_INTERVAL) {
                        mCurrentMusicPosition = mAgoraMusicPlayer.getPlayPosition();
                    } else {
                        mCurrentMusicPosition += MUSIC_POSITION_UPDATE_INTERVAL;
                    }
                    mCallback.onMusicPositionChange(mCurrentMusicPosition);
                }
            }
        }, 0, MUSIC_POSITION_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void stopDisplayLrc() {
        mScheduledExecutorService.shutdown();
    }

    private void maybeCreateNewScheduledService() {
        if (null == mScheduledExecutorService || mScheduledExecutorService.isShutdown()) {
            mScheduledExecutorService = Executors.newScheduledThreadPool(5);
        }
    }

    private void reset() {
        mStatus = Status.IDLE;
        mScheduledExecutorService.shutdown();
        mScheduledExecutorService = null;
    }

    public void updateMusicPosition(long position) {
        if (mStatus == Status.Started) {
            mCurrentMusicPosition = position;
        }
    }

    public void setPlayMode(MusicPlayMode playMode, int musicType) {
        if (mAgoraMusicPlayer == null) return;
//        mAgoraMusicPlayer.setPlayMode(playMode);
        int ret = -1;
        if (MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL == playMode) {
            if (musicType == 1) {
                ret = mAgoraMusicPlayer.setAudioDualMonoMode(io.agora.mediaplayer.Constants.AudioDualMonoMode.AUDIO_DUAL_MONO_R.ordinal());
            } else if (musicType == 4 || musicType == 6) {
                ret = mAgoraMusicPlayer.selectAudioTrack(0);
            }
        } else if (MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY == playMode) {
            if (musicType == 1) {
                //左伴右唱
                ret = mAgoraMusicPlayer.setAudioDualMonoMode(io.agora.mediaplayer.Constants.AudioDualMonoMode.AUDIO_DUAL_MONO_L.ordinal());
            } else if (musicType == 4 || musicType == 6) {
                //多音轨
                ret = mAgoraMusicPlayer.selectAudioTrack(1);
            }
        }
        Log.i(TAG, "setPlayMode() called with: playMode = " + playMode + " ret=" + ret);
    }

    public void clearCache() {
        MusicCacheInfo[] caches = mMcc.getCaches();
        for (MusicCacheInfo cache : caches) {
            mMcc.removeCache(cache.songCode);
        }
    }

    public long getPlayPosition() {
        return mCurrentMusicPosition;
    }

    public interface MccCallback {
        default void onMusicLyricRequest(long songCode, String lyricUrl, String pitchUrl) {

        }

        default void onTmeSongsLoaded(List<TmeSong> songs) {
        }

        default void onTmeError(String message) {
        }

        default void onMusicPreloadResult(long songCode, int percent) {

        }

        default void onMusicPositionChange(long position) {

        }

        default void onMusicPitch(double voicePitch, long progressInMs) {

        }

        default void onMusicPlaying() {

        }

        default void onMusicStop() {

        }
    }

    public static class TmeSong {
        public final String songId;
        public final String name;
        public final String lyricUrl;
        public final String lyricType;
        public final String pitchUrl;

        TmeSong(String songId, String name, String lyricUrl, String lyricType, String pitchUrl) {
            this.songId = songId;
            this.name = name;
            this.lyricUrl = lyricUrl;
            this.lyricType = lyricType;
            this.pitchUrl = pitchUrl;
        }
    }
}
