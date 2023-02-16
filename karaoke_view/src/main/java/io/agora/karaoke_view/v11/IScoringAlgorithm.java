package io.agora.karaoke_view.v11;

public interface IScoringAlgorithm {
    public float pitchToScore(float minimumScore, float currentPitch, float currentRefPitch);

    public int getMaximumScoreForLine();

    public void setScoringLevel(int level);

    public void setScoringCompensationOffset(int offset);

    public int getScoringLevel();

    public int getScoringCompensationOffset();
}
