package io.agora.karaoke_view.v11;

import android.content.Context;

import java.io.File;

import io.agora.karaoke_view.v11.config.Config;
import io.agora.karaoke_view.v11.constants.Constants;
import io.agora.karaoke_view.v11.internal.ScoringMachine;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;
import io.agora.karaoke_view.v11.utils.LyricsParser;
import io.agora.logging.FileLogger;
import io.agora.logging.LogManager;
import io.agora.logging.Logger;

public class KaraokeView {
    private static final String TAG = Constants.TAG + "-KaraokeView";
    private IScoringAlgorithm mScoringAlgorithm;
    private KaraokeEvent mKaraokeEvent;
    private LyricsView mLyricsView;
    private ScoringView mScoringView;
    private ScoringMachine mScoringMachine;
    private Context mContext;
    private static FileLogger mFileLogger;

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
        LogManager.instance().debug(TAG, "initialize");
        this.mScoringAlgorithm = new DefaultScoringAlgorithm();
        mScoringMachine = new ScoringMachine(new VoicePitchChanger(), mScoringAlgorithm, new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines) {
                LogManager.instance().debug(Constants.TAG, "onLineFinished line startTime:" + line.getStartTime() + ",line endTime:" + line.getEndTime() +
                        ",score:" + score + ",cumulativeScore:" + cumulativeScore + ",perfectScore:" + perfectScore + ",index:" + index + ",numberOfLines:" + numberOfLines);

                if (mScoringView != null) {
                    mScoringView.resetPitchIndicatorAndAnimationWhenFullLineFinished(score);
                }

                if (mKaraokeEvent != null) {
                    mKaraokeEvent.onLineFinished(KaraokeView.this, line, score, cumulativeScore, index, numberOfLines);
                }
            }

            @Override
            public void resetUi() {
                if (Config.DEBUG) {
                    LogManager.instance().debug(Constants.TAG, "resetUi");
                }
                if (mLyricsView != null) {
                    mLyricsView.requestRefreshUi();
                }
                if (mScoringView != null) {
                    mScoringView.resetPitchIndicatorAndAnimation();
                    mScoringView.requestRefreshUi();
                }
            }

            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches, long progress) {
                if (Config.DEBUG) {
                    LogManager.instance().debug(Constants.TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches + " " + progress);
                }

                if (mKaraokeEvent != null) {
                    mKaraokeEvent.onRefPitchUpdate(refPitch, numberOfRefPitches);
                }
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean betweenCurrentPitch, long progress) {
                if (Config.DEBUG) {
                    LogManager.instance().debug(Constants.TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + betweenCurrentPitch + " " + progress);
                }
                if (mScoringView != null) {
                    mScoringView.updatePitchAndScore(pitch, scoreAfterNormalization, betweenCurrentPitch);
                }
            }

            public void requestRefreshUi() {
                if (Config.DEBUG) {
                    LogManager.instance().debug(Constants.TAG, "requestRefreshUi");
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
            if (null == mFileLogger) {
                mFileLogger = new FileLogger(mContext.getExternalFilesDir(null).getPath(), Constants.LOG_FILE_NAME, 1024 * 1024, 4);
            }
            LogManager.instance().addLogger(mFileLogger);
        }
    }

    public void reset() {
        LogManager.instance().debug(Constants.TAG, "reset");
        if (mLyricsView != null) {
            mLyricsView.reset();
        }
        if (mScoringView != null) {
            mScoringView.reset();
        }
        mScoringMachine.reset();
    }

    public static LyricsModel parseLyricsData(File file) {
        return LyricsParser.parse(file);
    }

    //This interface is unstable and is not recommended for use
    public static LyricsModel parseLyricsData(File file, File pitch) {
        return LyricsParser.parse(file, pitch);
    }

    public static LyricsModel parseLyricsData(byte[] data) {
        return LyricsParser.parse(data);
    }

    public void setLyricsData(LyricsModel model) {
        LogManager.instance().debug(Constants.TAG, "setLyricsData model:" + model);
        mScoringMachine.prepare(model);

        if (mLyricsView != null) {
            mLyricsView.attachToScoringMachine(mScoringMachine);
        }

        if (mScoringView != null) {
            mScoringView.attachToScoringMachine(mScoringMachine);
        }

        mScoringMachine.prepareUi();
    }

    public void attachUi(LyricsView lyrics, ScoringView scoring) {
        LogManager.instance().debug(Constants.TAG, "attachUi lyrics:" + lyrics + ",scoring:" + scoring);
        if (mScoringMachine == null) {
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
            mLyricsView.attachToScoringMachine(mScoringMachine);
        }

        if (mScoringView != null) {
            mScoringView.attachToScoringMachine(mScoringMachine);
        }
    }

    public final LyricsModel getLyricsData() {
        return mScoringMachine.getLyricsModel();
    }

    public void setPitch(float pitch) {
        mScoringMachine.setPitch(pitch);
    }

    public void setProgress(long progress) {
        mScoringMachine.setProgress(progress);
    }

    public void setKaraokeEvent(KaraokeEvent event) {
        LogManager.instance().debug(Constants.TAG, "setKaraokeEvent event:" + event);
        this.mKaraokeEvent = event;

        if (mLyricsView != null) {
            mLyricsView.setSeekListener(new LyricsView.OnLyricsSeekListener() {
                @Override
                public void onProgressChanged(long progress) {
                    if (mKaraokeEvent != null) {
                        mKaraokeEvent.onDragTo(KaraokeView.this, progress);
                    }
                    mScoringMachine.whenDraggingHappen(progress);
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

    public void setScoringAlgorithm(IScoringAlgorithm algorithm) {
        LogManager.instance().debug(Constants.TAG, "setScoringAlgorithm algorithm:" + algorithm);
        this.mScoringAlgorithm = algorithm;
        this.mScoringMachine.setScoringAlgorithm(this.mScoringAlgorithm);
    }

    public void setScoringLevel(int level) {
        LogManager.instance().debug(Constants.TAG, "setScoringLevel level:" + level);
        this.mScoringAlgorithm.setScoringLevel(level);
    }

    public int getScoringLevel() {
        return this.mScoringAlgorithm.getScoringLevel();
    }

    public void setScoringCompensationOffset(int offset) {
        LogManager.instance().debug(Constants.TAG, "setScoringCompensationOffset offset:" + offset);
        this.mScoringAlgorithm.setScoringCompensationOffset(offset);
    }

    public int getScoringCompensationOffset() {
        return this.mScoringAlgorithm.getScoringCompensationOffset();
    }

    public void addLogger(Logger logger) {
        LogManager.instance().addLogger(logger);
    }

    public void removeLogger(Logger logger) {
        LogManager.instance().removeLogger(logger);
    }

    public void removeAllLogger() {
        LogManager.instance().removeAllLogger();
    }
}
