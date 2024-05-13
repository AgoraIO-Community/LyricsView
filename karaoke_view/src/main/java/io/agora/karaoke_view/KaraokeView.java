package io.agora.karaoke_view;

import android.content.Context;

import java.io.File;

import io.agora.karaoke_view.internal.LyricMachine;
import io.agora.karaoke_view.internal.config.Config;
import io.agora.karaoke_view.internal.lyric.parse.LyricPitchParser;
import io.agora.karaoke_view.internal.utils.LogUtils;
import io.agora.karaoke_view.model.LyricModel;
import io.agora.logging.Logger;

public class KaraokeView {
    private KaraokeEvent mKaraokeEvent;
    private LyricsView mLyricsView;
    private ScoringView mScoringView;
    private LyricMachine mLyricMachine;
    private Context mContext;

    public KaraokeView(LyricsView lyricsView, ScoringView scoringView) {
        this.mLyricsView = lyricsView;
        this.mScoringView = scoringView;
        if (null != mLyricsView) {
            mContext = mLyricsView.getContext();
        } else if (null != mScoringView) {
            mContext = mScoringView.getContext();
        }
        initialize();
    }

    public KaraokeView() {
        initialize();
    }

    public KaraokeView(Context context) {
        mContext = context;
        initialize();
    }

    private void initialize() {
        LogUtils.d("initialize");
        mLyricMachine = new LyricMachine(new LyricMachine.OnLyricListener() {
            @Override
            public void resetUi() {
                if (Config.DEBUG) {
                    LogUtils.d("resetUi");
                }
                if (mLyricsView != null) {
                    mLyricsView.requestRefreshUi();
                }
                if (mScoringView != null) {
                    mScoringView.resetPitchIndicatorAndAnimation();
                    mScoringView.requestRefreshUi();
                }
            }

            public void requestRefreshUi() {
                if (Config.DEBUG) {
                    LogUtils.d("requestRefreshUi");
                }
                if (mLyricsView != null) {
                    mLyricsView.requestRefreshUi();
                }
                if (mScoringView != null) {
                    mScoringView.requestRefreshUi();
                }
            }
        });

        if (null != mContext) {
            LogUtils.enableLog(mContext, true, true, mContext.getExternalFilesDir(null).getPath());
        }
    }

    public void reset() {
        LogUtils.d("reset");
        if (mLyricsView != null) {
            mLyricsView.reset();
        }
        if (mScoringView != null) {
            mScoringView.reset();
        }
        mLyricMachine.reset();
    }


    public static LyricModel parseLyricData(File lyricFile, File pitchFile) {
        return LyricPitchParser.parseFile(lyricFile, pitchFile);
    }

    public static LyricModel parseLyricData(File lyricFile, File pitchFile, boolean includeCopyrightSentence) {
        return LyricPitchParser.parseFile(lyricFile, pitchFile, includeCopyrightSentence);
    }

    public static LyricModel parseLyricData(byte[] lyricData, byte[] pitchData) {
        return LyricPitchParser.parseLyricData(lyricData, pitchData);
    }

    public static LyricModel parseLyricData(byte[] lyricData, byte[] pitchData, boolean includeCopyrightSentence) {
        return LyricPitchParser.parseLyricData(lyricData, pitchData, includeCopyrightSentence);
    }


    public void attachUi(LyricsView lyrics, ScoringView scoring) {
        LogUtils.d("attachUi lyrics:" + lyrics + ",scoring:" + scoring);
        if (mLyricMachine == null) {
            throw new IllegalStateException("Call this after KaraokeView initialized, this is a convenient method for attach/detach on-the-fly");
        }

        if (scoring != null && mScoringView != scoring) {
            if (mScoringView != null) {
                mScoringView.reset();
            }
            mScoringView = scoring;
        } else if (scoring == null) {
            if (mScoringView != null) {
                mScoringView.reset();
            }
            mScoringView = null;
        }

        if (lyrics != null && mLyricsView != lyrics) {
            if (mLyricsView != null) {
                mLyricsView.reset();
            }
            mLyricsView = lyrics;
        } else if (lyrics == null) {
            if (mLyricsView != null) {
                mLyricsView.reset();
            }
            mLyricsView = null;
        }

        if (mLyricsView != null) {
            mLyricsView.attachToLyricMachine(mLyricMachine);
        }

        if (mScoringView != null) {
            mScoringView.attachToLyricMachine(mLyricMachine);
        }
    }

    public void setLyricData(LyricModel model) {
        LogUtils.d("setLyricData model:" + model);
        mLyricMachine.prepare(model);

        if (mLyricsView != null) {
            mLyricsView.attachToLyricMachine(mLyricMachine);
        }

        if (mScoringView != null) {
            mScoringView.attachToLyricMachine(mLyricMachine);
        }

        mLyricMachine.prepareUi();
    }

    public final LyricModel getLyricData() {
        return mLyricMachine.getLyricsModel();
    }

    /**
     * 设置实时采集(mic)的Pitch
     * - Note: 可以从AgoraRTC DRM回调方法 `onPitch`[该回调频率是50ms/次]  获取
     * - Parameter speakerPitch: 演唱者的实时音高值
     * - Parameter progressInMs: 当前音高、得分对应的实时进度（ms）
     *
     * @param speakerPitch 演唱者的实时音高值
     * @param progressInMs 当前音高、得分对应的实时进度（ms）
     */
    public void setPitch(float speakerPitch, int progressInMs) {
        mLyricMachine.setPitch(speakerPitch, progressInMs);

        if (mScoringView != null) {
            mScoringView.setPitch(speakerPitch, mLyricMachine.getRefPitch(progressInMs));
        }
    }

    /**
     * 设置当前歌曲的进度 [需要20ms/次的频率进行调用]
     * - Note: 可以获取播放器的当前进度进行设置
     * - Parameter progress: 歌曲进度 (ms)
     *
     * @param progress 歌曲进度 (ms)
     */
    public void setProgress(long progress) {
        mLyricMachine.setProgress(progress);
    }

    public void setKaraokeEvent(KaraokeEvent event) {
        LogUtils.d("setKaraokeEvent event:" + event);
        this.mKaraokeEvent = event;

        if (mLyricsView != null) {
            mLyricsView.setSeekListener(new LyricsView.OnLyricsSeekListener() {
                @Override
                public void onProgressChanged(long progress) {
                    if (mKaraokeEvent != null) {
                        mKaraokeEvent.onDragTo(KaraokeView.this, progress);
                    }
                    mLyricMachine.whenDraggingHappen(progress);
                }

                @Override
                public void onStartTrackingTouch() {

                }

                @Override
                public void onStopTrackingTouch() {

                }
            });
        }
    }

    public void addLogger(Logger logger) {
        LogUtils.addLogger(logger);
    }

    public void removeLogger(Logger logger) {
        LogUtils.removeLogger(logger);
    }

    public void removeAllLogger() {
        LogUtils.destroy();
    }
}
