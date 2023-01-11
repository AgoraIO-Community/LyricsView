package io.agora.lyrics_view.v11;

import android.util.Log;

import androidx.annotation.MainThread;

import java.io.File;

import io.agora.lyrics_view.v11.internal.OngoingStats;
import io.agora.lyrics_view.v11.model.LyricsLineModel;
import io.agora.lyrics_view.v11.model.LyricsModel;
import io.agora.lyrics_view.v11.utils.LyricsParser;

public class KaraokeView {

    private static final String TAG = "KaraokeView";

    private IScoringAlgorithm mScoringAlgorithm;

    private int mScoreLevel = 10;
    private int mScoreCompensationOffset = 0;

    private KaraokeEvent mKaraokeEvent;

    private LyricsView mLyricsView;
    private ScoringView mScoringView;

    private OngoingStats mOngoingStats;

    private VoicePitchChanger mVoicePitchChanger;

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
        mVoicePitchChanger = new VoicePitchChanger();
        mOngoingStats = new OngoingStats(new OngoingStats.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, double score, double cumulativeScore, double perfectScore, int index, int total) {
                Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + perfectScore + " " + index + " " + total);

                if (mScoringView != null) {
                    mScoringView.forceStopPivotAnimationWhenFullLineFinished(score);
                }

                if (mKaraokeEvent != null) {
                    mKaraokeEvent.onLineFinished(KaraokeView.this, line, (int) score, index, total);
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

            public void onRefPitch(float refPitch, int numberOfRefPitches) {
                Log.d(TAG, "onRefPitch " + refPitch + " " + numberOfRefPitches);
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
        mOngoingStats.reset();
    }

    public static LyricsModel parseLyricsData(File file) {
        return LyricsParser.parse(file);
    }

    public static LyricsModel parseLyricsData(byte[] data) {
        return new LyricsModel(LyricsModel.Type.Migu);
    }

    public void setLyricsData(LyricsModel model) {
        mOngoingStats.prepare(model);

        if (mLyricsView != null) {
            mLyricsView.setLrcData(model);
        }

        if (mScoringView != null) {
            mScoringView.attachToOngoingStats(mOngoingStats, mVoicePitchChanger);
        }
    }

    @MainThread
    public void setPitch(float pitch) {
        if (mLyricsView != null) {
        }
        if (mScoringView != null) {
            mScoringView.updateLocalPitch(pitch);
        }
    }

    public void setProgress(long progress) {
        if (mOngoingStats != null) {
            mOngoingStats.setProgress(progress);
        }
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
        this.mScoreLevel = level;
    }

    public void setScoreCompensationOffset(int offset) {
        this.mScoreCompensationOffset = offset;
    }
}
