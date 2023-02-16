package io.agora.karaoke_view.v11;

import io.agora.karaoke_view.v11.internal.ScoringMachine;

public class DefaultScoringAlgorithm implements IScoringAlgorithm {

    // Maximum score for one line, 100 for maximum and 0 for minimum
    private final int mMaximumScoreForLine = 100;

    public DefaultScoringAlgorithm() {
    }

    // Indicating the difficulty in scoring(can change by app)
    private int mScoringLevel = 10; // 0~100
    private int mScoringCompensationOffset = 0; // -100~100

    @Override
    public float pitchToScore(float minimumScore, float currentPitch, float currentRefPitch) {
        final float scoreAfterNormalization = ScoringMachine.calculateScore2(minimumScore, mScoringLevel, mScoringCompensationOffset, currentPitch, currentRefPitch);
        return scoreAfterNormalization;
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
