package io.agora.karaoke_view.v11;

import android.util.Log;

import java.util.Iterator;
import java.util.LinkedHashMap;

import io.agora.karaoke_view.v11.internal.ScoringMachine;
import io.agora.karaoke_view.v11.model.LyricsLineModel;

public class DefaultScoringAlgorithm implements IScoringAlgorithm {
    private static final String TAG = "DefaultScoringAlgorithm";

    private static final boolean DEBUG = false;

    // Maximum score for one line, 100 for maximum and 0 for minimum
    private final int mMaximumScoreForLine = 100;

    private ScoringMachine.OnScoringListener mListener;

    public DefaultScoringAlgorithm() {
    }

    // Indicating the difficulty in scoring(can change by app)
    private int mScoringLevel = 10; // 0~100
    private int mScoringCompensationOffset = 0; // -100~100

    @Override
    public float pitchToScore(float currentPitch, float currentRefPitch) {
        final float scoreAfterNormalization = ScoringMachine.calculateScore2(0, mScoringLevel, mScoringCompensationOffset, currentPitch, currentRefPitch);
        return scoreAfterNormalization;
    }

    @Override
    public int calcLineScore(final LinkedHashMap<Long, Float> pitchesForLine, final int indexOfLineJustFinished, final LyricsLineModel lineJustFinished) {
        // 计算歌词当前句的分数 = 所有打分/分数个数
        float totalScoreForThisLine = 0;
        int scoreCount = 0;

        Float scoreForOnePitch;
        // 两种情况 1. 到了空档期 2. 到了下一句
        Iterator<Long> iterator = pitchesForLine.keySet().iterator();
        int continuousZeroCount = 0;

        if (DEBUG) {
            debugScoringAlgo(pitchesForLine, indexOfLineJustFinished);
        }

        while (iterator.hasNext()) {
            Long myKeyTimestamp = iterator.next();
            if (myKeyTimestamp <= lineJustFinished.getEndTime()) {
                scoreForOnePitch = pitchesForLine.get(myKeyTimestamp);
                if (scoreForOnePitch == null || scoreForOnePitch == 0.f) {
                    continuousZeroCount++;
                    if (continuousZeroCount >= 8) {
                        continuousZeroCount = 0; // re-count it when reach 8 continuous zeros
                        if (mListener != null) { // Update UI when too many zeros comes
                            mListener.resetUi();
                        }
                    }
                } else {
                    continuousZeroCount = 0;
                }
                iterator.remove();
                pitchesForLine.remove(myKeyTimestamp);

                if (scoreForOnePitch != null) {
                    totalScoreForThisLine += scoreForOnePitch;
                }
                scoreCount++;
            }
        }

        scoreCount = Math.max(1, scoreCount);

        int scoreThisTime = (int) totalScoreForThisLine / scoreCount;

        if (DEBUG) {
            Log.d(TAG, "debugScoringAlgo/mPitchesForLine/CALC: totalScoreForThisLine=" + totalScoreForThisLine + ", scoreCount=" + scoreCount + ", scoreThisTime=" + scoreThisTime);
        }

        return scoreThisTime;
    }

    private void debugScoringAlgo(LinkedHashMap<Long, Float> pitches, int indexOfLineJustFinished) {
        Iterator<Long> iterator = pitches.keySet().iterator();
        double cumulativeScoreForLine = 0;
        while (iterator.hasNext()) {
            Long myKeyTimestamp = iterator.next();
            Float score = pitches.get(myKeyTimestamp);
            cumulativeScoreForLine += (score != null ? score : 0);
            Log.d(TAG, "debugScoringAlgo/mPitchesForLine: timestamp=" + myKeyTimestamp + ", scoreForPitch=" + score);
        }
        Log.d(TAG, "debugScoringAlgo/mPitchesForLine: numberOfPitches=" + pitches.size() + ", cumulativeScoreForLine=" + cumulativeScoreForLine + ", mIndexOfCurrentLine=" + indexOfLineJustFinished);
    }

    @Override
    public void setScoringListener(ScoringMachine.OnScoringListener listener) {
        this.mListener = listener;
    }

    @Override
    public int getMaximumScoreForLine() {
        return mMaximumScoreForLine;
    }

    @Override
    public void setScoringLevel(int level) {
        this.mScoringLevel = level;
    }

    @Override
    public void setScoringCompensationOffset(int offset) {
        this.mScoringCompensationOffset = offset;
    }

    @Override
    public int getScoringLevel() {
        return this.mScoringLevel;
    }

    @Override
    public int getScoringCompensationOffset() {
        return this.mScoringCompensationOffset;
    }

}
