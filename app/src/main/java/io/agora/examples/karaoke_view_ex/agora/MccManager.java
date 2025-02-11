package io.agora.examples.karaoke_view_ex.agora;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

public class MccManager {
    private static final String TAG = Constants.TAG + "-MCCManager";
    private Context mContext;
    private IAgoraMusicContentCenter mMcc;
    private IAgoraMusicPlayer mAgoraMusicPlayer;
    private MusicContentCenterConfiguration mConfig;
    private final MccCallback mCallback;
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
            Log.d(TAG, "onPreLoadEvent requestId:" + requestId + " songCode:" + songCode + " percent:" + percent + " lyricUrl:" + lyricUrl + " status:" + status + " errorCode:" + errorCode);
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
            Log.d(TAG, "onLyricResult()  requestId :" + requestId + " songCode:" + songCode + " lyricUrl:" + lyricUrl + " errorCode:" + errorCode);
            mCallback.onMusicLyricRequest(songCode, lyricUrl);
        }

        @Override
        public void onSongSimpleInfoResult(String requestId, long songCode, String simpleInfo, int errorCode) {

        }
    };

    private final IMediaPlayerObserver mMediaPlayerObserver = new IMediaPlayerObserver() {
        @Override
        public void onPlayerStateChanged(io.agora.mediaplayer.Constants.MediaPlayerState state, io.agora.mediaplayer.Constants.MediaPlayerError error) {
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
        public void onAudioVolumeIndication(int volume) {

        }
    };

    public MccManager(Context context, MccCallback callback) {
        mContext = context;
        mCallback = callback;
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
            //mConfig.mccDomain = "api-test.agora.io";
            mConfig.eventHandler = mMccEventHandler;
            mMcc.initialize(mConfig);

            mAgoraMusicPlayer = mMcc.createMusicPlayer();

            mAgoraMusicPlayer.registerPlayerObserver(mMediaPlayerObserver);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void destroy() {
        mAgoraMusicPlayer.unRegisterPlayerObserver(mMediaPlayerObserver);
        mMcc.unregisterEventHandler();
        IAgoraMusicContentCenter.destroy();
        mAgoraMusicPlayer.destroy();
        mAgoraMusicPlayer = null;
        mMcc = null;
        mConfig.eventHandler = null;
        mConfig = null;
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
        default void onMusicLyricRequest(long songCode, String lyricUrl) {

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
}
