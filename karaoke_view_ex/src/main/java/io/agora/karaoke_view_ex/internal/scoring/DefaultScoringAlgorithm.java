package io.agora.karaoke_view_ex.internal.scoring;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.agora.karaoke_view_ex.IScoringAlgorithm;
import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.internal.ai.AIAlgorithmScoreNative;
import io.agora.karaoke_view_ex.internal.config.Config;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.logging.LogManager;

/**
 * Default implementation of the IScoringAlgorithm interface.
 * This class provides scoring functionality for karaoke performances by evaluating
 * the match between user's pitch and reference pitch data.
 */
public class DefaultScoringAlgorithm implements IScoringAlgorithm {
    /**
     * Tag for logging
     */
    private static final String TAG = Constants.TAG + "-DefaultScoringAlgorithm";

    /**
     * Maximum score for one line, 100 for maximum and 0 for minimum
     */
    private final int mMaximumScoreForLine = 100;

    /**
     * Difficulty level for scoring (0-100, higher values are more lenient)
     * Can be changed by the application
     */
    private int mScoringLevel = 15;

    /**
     * Compensation offset for scoring (-100 to 100)
     * Used to adjust scores based on external factors
     */
    private int mScoringCompensationOffset = 0;

    /**
     * Constructs a new DefaultScoringAlgorithm with default settings
     */
    public DefaultScoringAlgorithm() {
    }

    /**
     * Calculates a normalized score for the current pitch compared to the reference pitch
     *
     * @param currentPitch    The current pitch detected from the user's voice
     * @param currentRefPitch The reference pitch from the song
     * @return A normalized score value (0-100)
     */
    @Override
    public float getPitchScore(float currentPitch, float currentRefPitch) {
        float scoreAfterNormalization = 0f;
        scoreAfterNormalization = AIAlgorithmScoreNative.calculatedScore(currentPitch, currentRefPitch, mScoringLevel, mScoringCompensationOffset);
        return scoreAfterNormalization;
    }

    /**
     * Calculates the overall score for a completed lyrics line
     *
     * @param pitchesForLine          Map of timestamps to pitch scores for the line
     * @param indexOfLineJustFinished Index of the line that was just finished
     * @param lineJustFinished        The lyrics line model that was just finished
     * @return The calculated score for the line (0-100)
     */
    @Override
    public int getLineScore(final LinkedHashMap<Long, Float> pitchesForLine, final int indexOfLineJustFinished, final LyricsLineModel lineJustFinished) {
        if (Config.DEBUG) {
            debugScoringAlgo(pitchesForLine, indexOfLineJustFinished);
        }

        List<LyricsLineModel.Tone> tones = lineJustFinished.tones;
        Map<LyricsLineModel.Tone, Float> toneScoresMap = new HashMap<>(tones.size());

        int toneIndex = 0;
        float totalScoreForTone = 0f;
        int scoreCountForTone = 0;
        Float scoreForOnePitch = null;
        Iterator<Long> iterator = pitchesForLine.keySet().iterator();
        while (iterator.hasNext()) {
            Long myKeyTimestamp = iterator.next();
            do {
                if (toneIndex >= tones.size()) {
                    break;
                }
                if (myKeyTimestamp >= tones.get(toneIndex).begin && myKeyTimestamp <= tones.get(toneIndex).end) {
                    scoreForOnePitch = pitchesForLine.get(myKeyTimestamp);
                    break;
                } else {
                    if (scoreCountForTone > 0) {
                        toneScoresMap.put(tones.get(toneIndex), totalScoreForTone / scoreCountForTone);
                    } else {
                        toneScoresMap.put(tones.get(toneIndex), 0f);
                    }
                    toneIndex++;
                    totalScoreForTone = 0f;
                    scoreCountForTone = 0;
                    scoreForOnePitch = null;
                }
            } while (toneIndex < tones.size());

            iterator.remove();

            if (scoreForOnePitch != null && scoreForOnePitch != -1f) {
                totalScoreForTone += scoreForOnePitch;
                scoreCountForTone++;
            }
        }

        if (toneIndex < tones.size()) {
            if (scoreCountForTone > 0) {
                toneScoresMap.put(tones.get(toneIndex), totalScoreForTone / scoreCountForTone);
            } else {
                toneScoresMap.put(tones.get(toneIndex), 0f);
            }
        }

        if (!toneScoresMap.isEmpty()) {
            if (Config.DEBUG) {
                LogManager.instance().debug(TAG, "getLineScore: toneScoresMap=" + toneScoresMap);
            }
            float totalScoreForThisLine = 0;
            for (Float score : toneScoresMap.values()) {
                totalScoreForThisLine += score;
            }
            return (int) (totalScoreForThisLine / toneScoresMap.size());
        }

        return 0;
    }

    /**
     * Outputs debug information about the scoring algorithm
     *
     * @param pitches                 Map of timestamps to pitch scores
     * @param indexOfLineJustFinished Index of the line that was just finished
     */
    private void debugScoringAlgo(LinkedHashMap<Long, Float> pitches, int indexOfLineJustFinished) {
        Iterator<Long> iterator = pitches.keySet().iterator();
        double cumulativeScoreForLine = 0;
        while (iterator.hasNext()) {
            Long myKeyTimestamp = iterator.next();
            Float score = pitches.get(myKeyTimestamp);
            cumulativeScoreForLine += (score != null ? score : 0);
            if (Config.DEBUG) {
                LogManager.instance().debug(TAG, "debugScoringAlgo/mPitchesForLine: timestamp=" + myKeyTimestamp + ", scoreForPitch=" + score);
            }
        }
        LogManager.instance().debug(TAG, "debugScoringAlgo/mPitchesForLine: numberOfPitches=" + pitches.size() + ", cumulativeScoreForLine=" + cumulativeScoreForLine + ", mIndexOfCurrentLine=" + indexOfLineJustFinished);
    }

    /**
     * Gets the maximum possible score for a line
     *
     * @return The maximum score value (100)
     */
    @Override
    public int getMaximumScoreForLine() {
        return mMaximumScoreForLine;
    }

    /**
     * Sets the scoring difficulty level
     *
     * @param level The difficulty level (0-100, higher values are more lenient)
     */
    @Override
    public void setScoringLevel(int level) {
        this.mScoringLevel = level;
    }

    /**
     * Sets the scoring compensation offset
     *
     * @param offset The compensation offset (-100 to 100)
     */
    @Override
    public void setScoringCompensationOffset(int offset) {
        this.mScoringCompensationOffset = offset;
    }

    /**
     * Gets the current scoring difficulty level
     *
     * @return The current difficulty level
     */
    @Override
    public int getScoringLevel() {
        return this.mScoringLevel;
    }

    /**
     * Gets the current scoring compensation offset
     *
     * @return The current compensation offset
     */
    @Override
    public int getScoringCompensationOffset() {
        return this.mScoringCompensationOffset;
    }
}
