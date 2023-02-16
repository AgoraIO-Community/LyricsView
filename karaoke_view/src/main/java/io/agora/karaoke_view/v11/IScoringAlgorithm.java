package io.agora.karaoke_view.v11;

public interface IScoringAlgorithm {
    float pitchToScore(float minimumScore, float currentPitch, float currentRefPitch);

    int getMaximumScoreForLine();

    void setScoringLevel(int level);

    void setScoringCompensationOffset(int offset);

    int getScoringLevel();

    int getScoringCompensationOffset();
}
