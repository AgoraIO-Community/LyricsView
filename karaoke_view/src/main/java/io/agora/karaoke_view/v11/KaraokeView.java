package io.agora.karaoke_view.v11;

import android.util.Log;

import java.io.File;

import io.agora.karaoke_view.v11.internal.ScoringMachine;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;
import io.agora.karaoke_view.v11.utils.LyricsParser;

public class KaraokeView {

    private static final String TAG = "KaraokeView";

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
        mScoringAlgorithm = new DefaultScoringAlgorithm();
        mScoringMachine = new ScoringMachine(new VoicePitchChanger(), new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines) {
                Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + perfectScore + " " + index + " " + numberOfLines);

                if (mScoringView != null) {
                    mScoringView.forceStopPivotAnimationWhenFullLineFinished(score);
                }

                if (mKaraokeEvent != null) {
                    mKaraokeEvent.onLineFinished(KaraokeView.this, line, (int) score, (int) cumulativeScore, index, numberOfLines);
                }
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
                if (mScoringView != null) {
                    mScoringView.forceStopPivotAnimationWhenReachingContinuousZeros();
                    mScoringView.requestRefreshUi();
                }
            }

            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches) {
                Log.d(TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches);

                if (mKaraokeEvent != null) {
                    mKaraokeEvent.onRefPitchUpdate(refPitch, numberOfRefPitches);
                }
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization) {
                Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization);
                if (mScoringView != null) {
                    mScoringView.updatePitchAndScore(pitch, scoreAfterNormalization);
                }
            }

            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
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
    }

    public void setScoreLevel(int level) {
        mScoringMachine.setScoreLevel(level);
    }

    public void setScoreCompensationOffset(int offset) {
        mScoringMachine.setScoreCompensationOffset(offset);
    }
}
