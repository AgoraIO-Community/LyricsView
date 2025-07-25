package io.agora.karaoke_view_ex;

import java.util.LinkedHashMap;

import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;

/**
 * Interface defining the scoring algorithm for karaoke performance evaluation.
 * Provides methods to calculate pitch scores and line scores based on user's singing performance.
 */
public interface IScoringAlgorithm {
    /**
     * Calculate a normalized score (between 0 and 1) for the current pitch compared to reference pitch.
     * Used to evaluate how well the user's singing pitch matches the expected pitch.
     *
     * @param currentPitch    The actual pitch value from user's singing
     * @param currentRefPitch The reference pitch value from the original song
     * @return A normalized score between 0 (completely off) and 1 (perfect match)
     */
    float getPitchScore(float currentPitch, float currentRefPitch);

    /**
     * Calculate the overall score for a completed lyrics line.
     * Evaluates the entire line's performance based on collected pitch data.
     *
     * @param pitchesForLine          Map of timestamps to pitch values for the completed line
     * @param indexOfLineJustFinished Index of the completed line in the lyrics sequence
     * @param lineJustFinished        The lyrics line model containing timing and text information
     * @return Integer score for the completed line
     */
    int getLineScore(LinkedHashMap<Long, Float> pitchesForLine, final int indexOfLineJustFinished, final LyricsLineModel lineJustFinished);

    /**
     * Get the maximum possible score that can be achieved for a line.
     *
     * @return Maximum achievable score value
     */
    int getMaximumScoreForLine();

    /**
     * Set the difficulty level for scoring calculation.
     * Higher levels may require more precise pitch matching.
     *
     * @param level Scoring difficulty level
     */
    void setScoringLevel(int level);

    /**
     * Get the current scoring difficulty level.
     *
     * @return Current scoring level
     */
    int getScoringLevel();

    /**
     * Set the compensation offset for score calculation.
     * Used to adjust scoring sensitivity or tolerance.
     *
     * @param offset Compensation offset value
     */
    void setScoringCompensationOffset(int offset);

    /**
     * Get the current compensation offset value.
     *
     * @return Current compensation offset
     */
    int getScoringCompensationOffset();
}
