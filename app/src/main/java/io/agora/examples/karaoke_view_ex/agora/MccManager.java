package io.agora.examples.karaoke_view_ex.agora;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.agora.examples.karaoke_view_ex.BuildConfig;
import io.agora.examples.utils.KeyCenter;
import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.mccex.utils.Utils;
import io.agora.mediaplayer.IMediaPlayerObserver;
import io.agora.mediaplayer.data.CacheStatistics;
import io.agora.mediaplayer.data.PlayerPlaybackStats;
import io.agora.mediaplayer.data.PlayerUpdatedInfo;
import io.agora.mediaplayer.data.SrcInfo;
import io.agora.musiccontentcenter.IAgoraMusicContentCenter;
import io.agora.musiccontentcenter.IAgoraMusicPlayer;
import io.agora.musiccontentcenter.IMusicContentCenterEventHandler;
import io.agora.musiccontentcenter.IScoreEventHandler;
import io.agora.musiccontentcenter.LineScoreData;
import io.agora.musiccontentcenter.MccConstants;
import io.agora.musiccontentcenter.Music;
import io.agora.musiccontentcenter.MusicCacheInfo;
import io.agora.musiccontentcenter.MusicChartInfo;
import io.agora.musiccontentcenter.MusicContentCenterConfiguration;
import io.agora.musiccontentcenter.RawScoreData;
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

    private String mToken = "";
    private String mUserId = "";

    private static volatile Status mStatus = Status.IDLE;
    private MccConstants.LyricSourceType mLyricType = MccConstants.LyricSourceType.LYRIC_SOURCE_XML;
    private MccConstants.MusicPlayMode mMusicPlayMode = MccConstants.MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL;

    private int mLyricOffset = 0;
    private int mSongOffsetBegin = 0;

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
        public void onPreLoadEvent(String requestId, long internalSongCode, int percent, String payload, int state, int reason) {
            Log.d(TAG, "mcc onPreLoadEvent requestId:" + requestId + " songCode:" + internalSongCode + " percent:" + percent + " payload:" + payload + " state:" + state + " reason:" + reason);
            mCallback.onMusicPreloadResult(internalSongCode, percent);
            if (state == 0 && percent == 100) {
                handleLyricResult(internalSongCode, payload);
            }
        }

        @Override
        public void onMusicCollectionResult(String requestId, int page, int pageSize, int total, Music[] list, int errorCode) {

        }

        @Override
        public void onMusicChartsResult(String requestId, MusicChartInfo[] list, int errorCode) {

        }

        @Override
        public void onLyricResult(String requestId, long internalSongCode, String payload, int reason) {
            Log.d(TAG, "mcc onLyricResult()  requestId :" + requestId + " songCode:" + internalSongCode + " payload:" + payload + " reason:" + reason);
            handleLyricResult(internalSongCode, payload);

        }

        @Override
        public void onSongSimpleInfoResult(String requestId, long songCode, String simpleInfo, int errorCode) {
            Log.d(TAG, "onSongSimpleInfoResult() called with: requestId = [" + requestId + "], songCode = [" + songCode + "], simpleInfo = [" + simpleInfo + "], errorCode = [" + errorCode + "]");
        }

        @Override
        public void onStartScoreResult(long internalSongCode, int state, int reason) {
            Log.d(TAG, "mcc onStartScoreResult() called with: internalSongCode = [" + internalSongCode + "], state = [" + state + "], reason = [" + reason + "]");

            if (state == MccConstants.MusicContentCenterState.MUSIC_CONTENT_CENTER_STATE_START_SCORE_COMPLETED.value()) {
                mMcc.setScoreLevel(MccConstants.ScoreLevel.SCORE_LEVEL_EASY);
            }
            openMusic(internalSongCode);
        }
    };

    private void handleLyricResult(long internalSongCode, String payload) {
        /*
         * {"lyricPath":"/data/user/0/io.agora.examples.karaoke_view_ex/cache/mccex/111805482632310595.krc","pitchPath":"/data/user/0/io.agora.examples.karaoke_view_ex/cache/mccex/111805482632310595.pitch","songOffsetBegin":0,"songOffsetEnd":191000,"lyricOffset":2}
         * */
        try {
            JSONObject jsonObject = new JSONObject(payload);
            String lyricPath = "";
            String pitchPath = "";
            if (jsonObject.has("lyricPath")) {
                lyricPath = jsonObject.getString("lyricPath");
            }
            if (jsonObject.has("pitchPath")) {
                pitchPath = jsonObject.getString("pitchPath");
            }
            if (jsonObject.has("songOffsetBegin")) {
                mSongOffsetBegin = jsonObject.getInt("songOffsetBegin");
            }
            if (jsonObject.has("lyricOffset")) {
                mLyricOffset = jsonObject.getInt("lyricOffset");
            }

            mCallback.onMusicLyricRequest(internalSongCode, lyricPath, pitchPath, mLyricOffset);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final IScoreEventHandler mMccScoreEventHandler = new IScoreEventHandler() {
        @Override
        public void onPitch(long internalSongCode, RawScoreData rawScoreData) {
            Log.d(TAG, "mcc onPitch() called with: internalSongCode = [" + internalSongCode + "], rawScoreData = [" + rawScoreData + "]");
            if (null != rawScoreData) {
                mCallback.onMusicPitch(internalSongCode, rawScoreData.speakerPitch, rawScoreData.pitchScore, rawScoreData.progressInMs);
            }
        }

        @Override
        public void onLineScore(long internalSongCode, LineScoreData lineScoreData) {
            Log.d(TAG, "mcc onLineScore() called with: internalSongCode = [" + internalSongCode + "], lineScoreData = [" + lineScoreData + "]");
            if (null != lineScoreData) {
                mCallback.onMusicLineScore(internalSongCode, lineScoreData.pitchScore, lineScoreData.cumulativePitchScore, lineScoreData.currentLineIndex, lineScoreData.totalLines);
            }
        }
    };

    private final IMediaPlayerObserver mMediaPlayerObserver = new IMediaPlayerObserver() {
        @Override
        public void onPlayerStateChanged(io.agora.mediaplayer.Constants.MediaPlayerState state, io.agora.mediaplayer.Constants.MediaPlayerReason reason) {
            Log.d(TAG, "onPlayerStateChanged: " + state + " " + reason);
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
                onMusicOpenError(io.agora.mediaplayer.Constants.MediaPlayerReason.getValue(reason));
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
        public void onPlayerCacheStats(CacheStatistics stats) {

        }

        @Override
        public void onPlayerPlaybackStats(PlayerPlaybackStats stats) {

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

    public void setTokenAndUserId(String token, String userId) {
        mToken = token;
        mUserId = userId;
    }

    private void initData() {
        mScheduledExecutorService = Executors.newScheduledThreadPool(5);
    }

    public void init(RtcEngine rtcEngine) {
        try {
            mMcc = IAgoraMusicContentCenter.create(rtcEngine);

            mConfig = new MusicContentCenterConfiguration();
            mConfig.eventHandler = mMccEventHandler;
            mConfig.scoreEventHandler = mMccScoreEventHandler;
            mConfig.audioFrameObserver = null;
            mMcc.initialize(mConfig);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("appId", BuildConfig.APP_ID);
            jsonObject.put("token", KeyCenter.getRtmToken2(KeyCenter.getUserUid()));
            jsonObject.put("userId", KeyCenter.getUserUid());
            //jsonObject.put("domain", "api-test.agora.io");
            Log.d(TAG, "mcc init: " + jsonObject.toString());
            mMcc.addVendor(MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_DEFAULT, jsonObject.toString());

            jsonObject = new JSONObject();
            jsonObject.put("appId", BuildConfig.VENDOR_2_APP_ID);
            jsonObject.put("appKey", BuildConfig.VENDOR_2_APP_KEY);
            jsonObject.put("token", mToken);
            jsonObject.put("userId", mUserId);
            jsonObject.put("deviceId", Utils.getUuid());
            jsonObject.put("urlTokenExpireTime", 15 * 60);
            jsonObject.put("chargeMode", 2);
            mMcc.addVendor(MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2, jsonObject.toString());

            mAgoraMusicPlayer = mMcc.createMusicPlayer();

            mAgoraMusicPlayer.registerPlayerObserver(mMediaPlayerObserver);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void destroy() {
        mAgoraMusicPlayer.unRegisterPlayerObserver(mMediaPlayerObserver);
        mMcc.unregisterEventHandler(mMccEventHandler);
        mMcc.destroyMusicPlayer(mAgoraMusicPlayer);
        IAgoraMusicContentCenter.destroy();
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

    public void openMusic(long internalSongCode) {
        mAgoraMusicPlayer.setPlayMode(mMusicPlayMode);
        RtcManager.INSTANCE.setPlayingSongCode(internalSongCode);
        int ret = mAgoraMusicPlayer.open(internalSongCode, 0);
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
        mMcc.stopScore();
        RtcManager.INSTANCE.setPlayingSongCode(0L);
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

    public void preloadMusic(final String songCode, final MccConstants.LyricSourceType lyricType, MccConstants.MusicContentCenterVendorId vendorId, String songOptionJson) {
        Log.d(TAG, "mcc preloadMusic call with music songCode:" + songCode + " lyricType:" + lyricType + " vendorId:" + vendorId + " songOptionJson:" + songOptionJson);
        mLyricType = lyricType;
        try {
            long internalSongCode = mMcc.getInternalSongCode(vendorId, songCode, songOptionJson);
            Log.d(TAG, "mcc preloadMusic internalSongCode=" + internalSongCode);
            if (0 == mMcc.isPreloaded(internalSongCode)) {
                Log.i(TAG, "mcc is preloaded songCode=" + internalSongCode);
                mMcc.getLyric(internalSongCode, mLyricType);
            } else {
                String requestId = mMcc.preload(internalSongCode);
                Log.d(TAG, "mcc preload song code requestId=" + requestId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startScore(MccConstants.MusicContentCenterVendorId vendorId, String songCode, String jsonOption) {
        Log.d(TAG, "mcc startScore() called with: vendorId = [" + vendorId + "], songCode = [" + songCode + "], jsonOption = [" + jsonOption + "]");
        try {
            long internalSongCode = mMcc.getInternalSongCode(vendorId, songCode, jsonOption);
            if (internalSongCode == 0) {
                Log.e(TAG, "getInternalSongCode failed songCode=" + songCode);
                return;
            }
            int ret = mMcc.startScore(internalSongCode);
            Log.d(TAG, "mcc startScore() called with " + internalSongCode + " ret = " + ret);
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
                        mCurrentMusicPosition += mSongOffsetBegin;
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

    public void setPlayMode(MccConstants.MusicPlayMode playMode, int musicType) {
        int ret = mAgoraMusicPlayer.setPlayMode(playMode);
        if (ret == 0) {
            mMusicPlayMode = playMode;
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
        default void onMusicLyricRequest(long songCode, String lyricUrl, String pitchUrl, int lyricOffset) {

        }

        default void onMusicPreloadResult(long songCode, int percent) {

        }

        default void onMusicPositionChange(long position) {

        }

        default void onMusicPitch(long internalSongCode, double voicePitch, double pitchScore, long progressInMs) {

        }

        default void onMusicLineScore(long internalSongCode, float linePitchScore, float cumulativeTotalLinePitchScores, int performedLineIndex, int performedTotalLines) {
        }

        default void onMusicPlaying() {

        }

        default void onMusicStop() {

        }
    }
}
