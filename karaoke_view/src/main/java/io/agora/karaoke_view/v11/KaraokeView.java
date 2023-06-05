package io.agora.karaoke_view.v11;

import android.util.Log;

import java.io.File;

import io.agora.karaoke_view.v11.internal.ScoringMachine;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;
import io.agora.karaoke_view.v11.utils.LyricsParser;

public class KaraokeView {

    private static final String TAG = "KaraokeView";

    private static final boolean DEBUG = false;

    private IScoringAlgorithm mScoringAlgorithm;

    private KaraokeEvent mKaraokeEvent;

    private LyricsView mLyricsView;
    private ScoringView mScoringView;

    private ScoringMachine mScoringMachine;

    public KaraokeView(LyricsView lyricsView, ScoringView scoringView) {
        this.mLyricsView = lyricsView;
        this.mScoringView = scoringView;
        initialize();
    }

    public KaraokeView() {
        initialize();
    }

    private void initialize() {
        this.mScoringAlgorithm = new DefaultScoringAlgorithm();
        mScoringMachine = new ScoringMachine(new VoicePitchChanger(), mScoringAlgorithm, new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines) {
                if (DEBUG) {
                    Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + perfectScore + " " + index + " " + numberOfLines);
                }

                if (mScoringView != null) {
                    mScoringView.resetPitchIndicatorAndAnimationWhenFullLineFinished(score);
                }

                if (mKaraokeEvent != null) {
                    mKaraokeEvent.onLineFinished(KaraokeView.this, line, score, cumulativeScore, index, numberOfLines);
                }
            }

            @Override
            public void resetUi() {
                if (DEBUG) {
                    Log.d(TAG, "resetUi");
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
                if (DEBUG) {
                    Log.d(TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches + " " + progress);
                }

                if (mKaraokeEvent != null) {
                    mKaraokeEvent.onRefPitchUpdate(refPitch, numberOfRefPitches);
                }
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean betweenCurrentPitch, long progress) {
                if (DEBUG) {
                    Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + betweenCurrentPitch + " " + progress);
                }
                if (mScoringView != null) {
                    mScoringView.updatePitchAndScore(pitch, scoreAfterNormalization, betweenCurrentPitch);
                }
            }

            public void requestRefreshUi() {
                if (DEBUG) {
                    Log.d(TAG, "requestRefreshUi");
                }
                if (mLyricsView != null) {
                    mLyricsView.requestRefreshUi();
                }
                if (mScoringView != null) {
                    mScoringView.requestRefreshUi();
                }
            }
        });
    }

    public void reset() {
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

    public static LyricsModel parseLyricsData(File file, File pitch) {
        return LyricsParser.parse(file, pitch);
    }

    public static LyricsModel parseLyricsData(byte[] data) {
        return new LyricsModel(LyricsModel.Type.Migu);
    }

    public void setLyricsData(LyricsModel model) {
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
        this.mScoringAlgorithm = algorithm;
        this.mScoringMachine.setScoringAlgorithm(this.mScoringAlgorithm);
    }

    public void setScoringLevel(int level) {
        this.mScoringAlgorithm.setScoringLevel(level);
    }

    public int getScoringLevel() {
        return this.mScoringAlgorithm.getScoringLevel();
    }

    public void setScoringCompensationOffset(int offset) {
        this.mScoringAlgorithm.setScoringCompensationOffset(offset);
    }

    public int getScoringCompensationOffset() {
        return this.mScoringAlgorithm.getScoringCompensationOffset();
    }
}
