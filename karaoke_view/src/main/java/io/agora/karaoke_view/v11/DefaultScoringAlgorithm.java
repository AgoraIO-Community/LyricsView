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

    public DefaultScoringAlgorithm() {
    }

    // Indicating the difficulty in scoring(can change by app)
    private int mScoringLevel = 10; // 0~100
    private int mScoringCompensationOffset = 0; // -100~100

    @Override
    public float getPitchScore(float currentPitch, float currentRefPitch) {
        final float scoreAfterNormalization = ScoringMachine.calculateScore2(0, mScoringLevel, mScoringCompensationOffset, currentPitch, currentRefPitch);
        return scoreAfterNormalization;
    }

    @Override
    public int getLineScore(final LinkedHashMap<Long, Float> pitchesForLine, final int indexOfLineJustFinished, final LyricsLineModel lineJustFinished) {
        float totalScoreForThisLine = 0;
        int scoreCount = 0;

        Float scoreForOnePitch;
        Iterator<Long> iterator = pitchesForLine.keySet().iterator();

        if (DEBUG) {
            debugScoringAlgo(pitchesForLine, indexOfLineJustFinished);
        }

        while (iterator.hasNext()) {
            Long myKeyTimestamp = iterator.next();
            if (myKeyTimestamp <= lineJustFinished.getEndTime()) {
                scoreForOnePitch = pitchesForLine.get(myKeyTimestamp);

                iterator.remove();
                pitchesForLine.remove(myKeyTimestamp);

                if (scoreForOnePitch != null) {
                    totalScoreForThisLine += scoreForOnePitch;
                }
                scoreCount++;
            }
        }

        scoreCount = Math.max(1, scoreCount);

        int scoreThisLine = (int) totalScoreForThisLine / scoreCount;

        if (DEBUG) {
            Log.d(TAG, "debugScoringAlgo/mPitchesForLine/CALC: totalScoreForThisLine=" + totalScoreForThisLine + ", scoreCount=" + scoreCount + ", scoreThisLine=" + scoreThisLine);
        }

        return scoreThisLine;
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
