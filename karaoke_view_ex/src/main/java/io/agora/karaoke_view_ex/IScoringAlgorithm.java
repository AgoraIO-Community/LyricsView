package io.agora.karaoke_view_ex;

import java.util.LinkedHashMap;

import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;

/**
 * Scoring algorithm interface
 */
public interface IScoringAlgorithm {
    /**
     * normalized score(0, 1) for the pitch
     *
     * @param currentPitch    current pitch
     * @param currentRefPitch current reference pitch
     * @return score
     */
    float getPitchScore(float currentPitch, float currentRefPitch);

    /**
     * score for the line just finished
     *
     * @param pitchesForLine          pitches for the line
     * @param indexOfLineJustFinished index of the line just finished
     * @param lineJustFinished        line just finished
     * @return score
     */
    int getLineScore(LinkedHashMap<Long, Float> pitchesForLine, final int indexOfLineJustFinished, final LyricsLineModel lineJustFinished);

    int getMaximumScoreForLine();

    void setScoringLevel(int level);

    int getScoringLevel();

    void setScoringCompensationOffset(int offset);

    int getScoringCompensationOffset();
}
