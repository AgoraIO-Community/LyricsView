package io.agora.karaoke_view.v11;

import java.util.LinkedHashMap;

import io.agora.karaoke_view.v11.model.LyricsLineModel;

public interface IScoringAlgorithm {
    /**
     * normalization score for the pitch
     *
     * @param currentPitch
     * @param currentRefPitch
     * @return
     */
    float pitchToScore(float currentPitch, float currentRefPitch);

    /**
     * score for the line just finished
     *
     * @param pitchesForLine
     * @param indexOfLineJustFinished
     * @param lineJustFinished
     * @return
     */
    int calcLineScore(LinkedHashMap<Long, Float> pitchesForLine, final int indexOfLineJustFinished, final LyricsLineModel lineJustFinished);

    int getMaximumScoreForLine();

    void setScoringLevel(int level);

    void setScoringCompensationOffset(int offset);

    int getScoringLevel();

    int getScoringCompensationOffset();
}
