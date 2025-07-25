package io.agora.karaoke_view_ex;

import android.content.Context;

import java.io.File;
import java.util.Objects;

import io.agora.karaoke_view_ex.internal.LyricMachine;
import io.agora.karaoke_view_ex.internal.ScoringMachine;
import io.agora.karaoke_view_ex.internal.lyric.parse.LyricPitchParser;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;
import io.agora.logging.Logger;

/**
 * Main controller class for karaoke functionality.
 * Manages lyrics display, scoring, and synchronization between different components.
 */
public class KaraokeView {
    /**
     * Event listener for karaoke-related callbacks
     */
    private KaraokeEvent mKaraokeEvent;

    /**
     * View component for displaying lyrics
     */
    private LyricsView mLyricsView;

    /**
     * Controller for lyrics timing and progression
     */
    private LyricMachine mLyricMachine;

    /**
     * View component for displaying scoring information
     */
    private ScoringView mScoringView;

    /**
     * Controller for scoring calculation and management
     */
    private ScoringMachine mScoringMachine;

    /**
     * Application context
     */
    private Context mContext;

    /**
     * Constructor with both lyrics and scoring views
     *
     * @param lyricsView  View for displaying lyrics
     * @param scoringView View for displaying score
     */
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

    /**
     * Default constructor
     */
    public KaraokeView() {
        initialize();
    }

    /**
     * Constructor with context
     *
     * @param context Application context
     */
    public KaraokeView(Context context) {
        mContext = context;
        initialize();
    }

    /**
     * Initialize the karaoke components including lyrics and scoring machines
     */
    private void initialize() {
        LogUtils.d("initialize");
        mLyricMachine = new LyricMachine(new LyricMachine.OnLyricListener() {
            @Override
            public void resetUi() {
                if (mLyricsView != null) {
                    mLyricsView.requestRefreshUi();
                }
                if (mScoringView != null) {
                    mScoringView.resetPitchIndicatorAndAnimation();
                    mScoringView.requestRefreshUi();
                }
            }

            public void requestRefreshUi() {
                if (mLyricsView != null) {
                    mLyricsView.requestRefreshUi();
                }
                if (mScoringView != null) {
                    mScoringView.requestRefreshUi();
                }
            }
        });

        mScoringMachine = new ScoringMachine(new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount) {
                LogUtils.d("onLineFinished line startTime:" + line.getStartTime() + ",line endTime:" + line.getEndTime() +
                        ",score:" + score + ",cumulativeScore:" + cumulativeScore + ",index:" + index + ",lineCount:" + lineCount);

                if (mScoringView != null) {
                    mScoringView.resetPitchIndicatorAndAnimationWhenFullLineFinished(score);
                }

                if (mKaraokeEvent != null) {
                    mKaraokeEvent.onLineFinished(KaraokeView.this, line, score, cumulativeScore, index, lineCount);
                }
            }

            @Override
            public void onPitchAndScoreUpdate(float speakerPitch, double scoreAfterNormalization, long progress) {
                LogUtils.d("onPitchAndScoreUpdate speakerPitch:" + speakerPitch + ",scoreAfterNormalization:" + scoreAfterNormalization + ",progress:" + progress);
                if (mScoringView != null) {
                    mScoringView.updatePitchAndScore(speakerPitch, (float) scoreAfterNormalization);
                }
            }

            @Override
            public void resetUi() {
                if (mLyricsView != null) {
                    mLyricsView.requestRefreshUi();
                }
                if (mScoringView != null) {
                    mScoringView.resetPitchIndicatorAndAnimation();
                    mScoringView.requestRefreshUi();
                }
            }

            public void requestRefreshUi() {
                if (mLyricsView != null) {
                    mLyricsView.requestRefreshUi();
                }
                if (mScoringView != null) {
                    mScoringView.requestRefreshUi();
                }
            }
        });

        if (null != mContext) {
            LogUtils.enableLog(mContext, true, true, Objects.requireNonNull(mContext.getExternalFilesDir(null)).getPath());
        }
    }

    /**
     * Reset all components to their initial state
     */
    public void reset() {
        LogUtils.d("reset");
        if (mLyricsView != null) {
            mLyricsView.reset();
        }
        if (mScoringView != null) {
            mScoringView.reset();
        }
        mLyricMachine.reset();
        mScoringMachine.reset();
    }

    /**
     * Parse lyrics data from files
     *
     * @param lyricFile Lyrics file
     * @param pitchFile Pitch data file
     * @return Parsed lyrics model
     */
    public static LyricModel parseLyricData(File lyricFile, File pitchFile) {
        return LyricPitchParser.parseFile(lyricFile, pitchFile, true, 0);
    }

    /**
     * Parse lyrics data from files with copyright control
     *
     * @param lyricFile                Lyrics file
     * @param pitchFile                Pitch data file
     * @param includeCopyrightSentence Whether to include copyright information
     * @return Parsed lyrics model
     */
    public static LyricModel parseLyricData(File lyricFile, File pitchFile, boolean includeCopyrightSentence) {
        return LyricPitchParser.parseFile(lyricFile, pitchFile, includeCopyrightSentence, 0);
    }

    /**
     * Parse lyrics data from files with copyright control and offset
     *
     * @param lyricFile                Lyrics file
     * @param pitchFile                Pitch data file
     * @param includeCopyrightSentence Whether to include copyright information
     * @param lyricOffset              Timing offset for lyrics
     * @return Parsed lyrics model
     */
    public static LyricModel parseLyricData(File lyricFile, File pitchFile, boolean includeCopyrightSentence, int lyricOffset) {
        return LyricPitchParser.parseFile(lyricFile, pitchFile, includeCopyrightSentence, lyricOffset);
    }

    /**
     * Parse lyrics data from byte arrays
     *
     * @param lyricData Lyrics data bytes
     * @param pitchData Pitch data bytes
     * @return Parsed lyrics model
     */
    public static LyricModel parseLyricData(byte[] lyricData, byte[] pitchData) {
        return LyricPitchParser.parseLyricData(lyricData, pitchData, true, 0);
    }

    /**
     * Parse lyrics data from byte arrays with copyright control
     *
     * @param lyricData                Lyrics data bytes
     * @param pitchData                Pitch data bytes
     * @param includeCopyrightSentence Whether to include copyright information
     * @return Parsed lyrics model
     */
    public static LyricModel parseLyricData(byte[] lyricData, byte[] pitchData, boolean includeCopyrightSentence) {
        return LyricPitchParser.parseLyricData(lyricData, pitchData, includeCopyrightSentence, 0);
    }

    /**
     * Parse lyrics data from byte arrays with copyright control and offset
     *
     * @param lyricData                Lyrics data bytes
     * @param pitchData                Pitch data bytes
     * @param includeCopyrightSentence Whether to include copyright information
     * @param lyricOffset              Timing offset for lyrics
     * @return Parsed lyrics model
     */
    public static LyricModel parseLyricData(byte[] lyricData, byte[] pitchData, boolean includeCopyrightSentence, int lyricOffset) {
        return LyricPitchParser.parseLyricData(lyricData, pitchData, includeCopyrightSentence, lyricOffset);
    }

    /**
     * Attach UI components to the karaoke view
     *
     * @param lyrics  Lyrics view component
     * @param scoring Scoring view component
     * @throws IllegalStateException if called before initialization
     */
    public void attachUi(LyricsView lyrics, ScoringView scoring) {
        LogUtils.d("attachUi lyrics:" + lyrics + ",scoring:" + scoring);
        if (mLyricMachine == null) {
            throw new IllegalStateException("Call this after KaraokeView initialized, this is a convenient method for attach/detach on-the-fly");
        }

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
            mLyricsView.attachToLyricMachine(mLyricMachine);
        }

        if (mScoringView != null) {
            mScoringView.attachToScoreMachine(mScoringMachine);
        }
    }

    /**
     * Set lyrics data and initialize scoring system
     *
     * @param model                Lyrics model containing timing and text data
     * @param usingInternalScoring Whether to use internal scoring algorithm
     */
    public void setLyricData(LyricModel model, boolean usingInternalScoring) {
        LogUtils.d("setLyricData model:" + model);
        mLyricMachine.prepare(model);
        mScoringMachine.prepare(model, usingInternalScoring);

        if (mLyricsView != null) {
            mLyricsView.attachToLyricMachine(mLyricMachine);
        }

        if (mScoringView != null) {
            mScoringView.attachToScoreMachine(mScoringMachine);
        }

        mLyricMachine.prepareUi();
        mScoringMachine.prepareUi();
    }

    /**
     * Get current lyrics data model
     *
     * @return Current lyrics model
     */
    public final LyricModel getLyricData() {
        return mLyricMachine.getLyricsModel();
    }

    /**
     * Set real-time pitch data from microphone input
     * Note: Can be obtained from AgoraRTC DRM callback method 'onPitch' (50ms interval)
     *
     * @param speakerPitch Current pitch value from singer
     * @param pitchScore   Current pitch score (0.0 to 100.0)
     * @param progressInMs Current progress timestamp in milliseconds
     */
    public void setPitch(float speakerPitch, float pitchScore, int progressInMs) {
        if (null != mScoringMachine) {
            mScoringMachine.setPitch(speakerPitch, pitchScore, progressInMs);
        }
    }

    /**
     * Set current song progress (should be called every 20ms)
     * Note: Can be obtained from the player's current progress
     *
     * @param progress Current song progress in milliseconds
     */
    public void setProgress(long progress) {
        LogUtils.d("setProgress progress:" + progress);
        mLyricMachine.setProgress(progress);
        mScoringMachine.setLyricProgress(progress);
    }

    /**
     * Set event listener for karaoke callbacks
     *
     * @param event Karaoke event listener
     */
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

    /**
     * Set custom scoring algorithm
     *
     * @param algorithm Custom scoring algorithm implementation
     */
    public void setScoringAlgorithm(IScoringAlgorithm algorithm) {
        LogUtils.d("setScoringAlgorithm algorithm:" + algorithm);
        if (mScoringMachine != null) {
            mScoringMachine.setScoringAlgorithm(algorithm);
        }
    }

    /**
     * Set scoring difficulty level
     *
     * @param level Scoring difficulty level
     */
    public void setScoringLevel(int level) {
        LogUtils.d("setScoringLevel level:" + level);
        if (null != mScoringMachine) {
            mScoringMachine.setScoringLevel(level);
        }
    }

    /**
     * Get current scoring difficulty level
     *
     * @return Current scoring level, defaults to 15
     */
    public int getScoringLevel() {
        if (null != mScoringMachine) {
            return mScoringMachine.getScoringLevel();
        }
        return 15;
    }

    /**
     * Set scoring compensation offset
     * Not recommended, use {@link #setScoringLevel(int)} instead.
     *
     * @param offset Compensation offset value
     * @deprecated Use {@link #setScoringLevel(int)} instead
     */
    @Deprecated
    public void setScoringCompensationOffset(int offset) {
        LogUtils.d("setScoringCompensationOffset offset:" + offset);
        if (null != mScoringMachine) {
            mScoringMachine.setScoringCompensationOffset(offset);
        }
    }

    /**
     * Get current scoring compensation offset
     *
     * @return Current compensation offset value
     * @deprecated Use {@link #getScoringLevel()} instead
     */
    @Deprecated
    public int getScoringCompensationOffset() {
        if (null != mScoringMachine) {
            return mScoringMachine.getScoringCompensationOffset();
        }
        return 0;
    }

    /**
     * Add custom logger
     *
     * @param logger Logger implementation to add
     */
    public void addLogger(Logger logger) {
        LogUtils.addLogger(logger);
    }

    /**
     * Remove custom logger
     *
     * @param logger Logger implementation to remove
     */
    public void removeLogger(Logger logger) {
        LogUtils.removeLogger(logger);
    }

    /**
     * Remove all custom loggers
     */
    public void removeAllLogger() {
        LogUtils.destroy();
    }
}
