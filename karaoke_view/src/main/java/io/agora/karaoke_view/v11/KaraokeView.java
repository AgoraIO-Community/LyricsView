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
                    mScoringView.forceStopPivotAnimationWhenFullLineFinished(score);
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
                if (mScoringView != null) {
                    mScoringView.forceStopPivotAnimationWhenReachingContinuousZeros();
                    mScoringView.requestRefreshUi();
                }
            }

            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches) {
                if (DEBUG) {
                    Log.d(TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches);
                }

                if (mKaraokeEvent != null) {
                    mKaraokeEvent.onRefPitchUpdate(refPitch, numberOfRefPitches);
                }
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean hit) {
                if (DEBUG) {
                    Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + hit);
                }
                if (mScoringView != null) {
                    mScoringView.updatePitchAndScore(pitch, scoreAfterNormalization, hit);
                }
            }

            public void requestRefreshUi() {
                if (DEBUG) {
                    Log.d(TAG, "requestRefreshUi");
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
        }
        mScoringMachine.reset();
    }

    public static LyricsModel parseLyricsData(File file) {
        return LyricsParser.parse(file);
    }

    public static LyricsModel parseLyricsData(byte[] data) {
        return new LyricsModel(LyricsModel.Type.Migu);
    }

    public void setLyricsData(LyricsModel model) {
        mScoringMachine.prepare(model);

        if (mLyricsView != null) {
            mLyricsView.setLrcData(model);
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

        if (mLyricsView != null) {
            mLyricsView.updateTime(progress);
        }
    }

    public void setKaraokeEvent(KaraokeEvent event) {
        this.mKaraokeEvent = event;

        if (mLyricsView != null) {
            mLyricsView.setSeekListener(new LyricsView.OnLyricsSeekListener() {
                @Override
                public void onProgressChanged(long time) {
                    if (mKaraokeEvent != null) {
                        mKaraokeEvent.onDragTo(KaraokeView.this, time);
                    }
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

    public void setScoreLevel(int level) {
        this.mScoringAlgorithm.setScoringLevel(level);
    }

    public int getScoreLevel() {
        return this.mScoringAlgorithm.getScoringLevel();
    }

    public void setScoreCompensationOffset(int offset) {
        this.mScoringAlgorithm.setScoringCompensationOffset(offset);
    }

    public int getScoreCompensationOffset() {
        return this.mScoringAlgorithm.getScoringCompensationOffset();
    }
}
