package io.agora.lyrics_view.v11;

import androidx.annotation.MainThread;

import java.io.File;

import io.agora.lyrics_view.v11.model.LyricsModel;
import io.agora.lyrics_view.v11.utils.LyricsParser;

public class KaraokeView {

    private IScoringAlgorithm mScoringAlgorithm;

    private int mScoreLevel = 10;
    private int mScoreCompensationOffset = 0;

    private KaraokeEvent mKaraokeEvent;

    private LyricsView mLyricsView;
    private ScoringView mScoringView;

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
    }

    public void reset() {
        if (mLyricsView != null) {
            mLyricsView.reset();
        }
        if (mScoringView != null) {
        }
    }

    public static LyricsModel parseLyricsData(File file) {
        return LyricsParser.parse(file);
    }

    public static LyricsModel parseLyricsData(byte[] data) {
        return new LyricsModel(LyricsModel.Type.Migu);
    }

    public void setLyricsData(LyricsModel model) {
        if (mLyricsView != null) {
            mLyricsView.setLrcData(model);
        }
        if (mScoringView != null) {
            mScoringView.setLrcData(model);
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
        if (mLyricsView != null) {
            mLyricsView.updateTime(progress);
        }
        if (mScoringView != null) {
            mScoringView.updateTime(progress);
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

        if (mScoringView != null) {
            mScoringView.setSingScoreListener(new ScoringView.OnSingScoreListener() {
                @Override
                public void onOriginalPitch(float pitch, int totalCount) {

                }

                @Override
                public void onScore(double score, double cumulativeScore, double totalScore) {
                    if (mKaraokeEvent != null) {
                        mKaraokeEvent.onLineFinished(KaraokeView.this, /** Fake **/null, (int) score, /** Fake **/0, (int) totalScore);
                    }
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
