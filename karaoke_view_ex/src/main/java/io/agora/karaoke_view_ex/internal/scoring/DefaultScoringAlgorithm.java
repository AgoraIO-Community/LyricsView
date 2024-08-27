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

public class DefaultScoringAlgorithm implements IScoringAlgorithm {
    private static final String TAG = Constants.TAG + "-DefaultScoringAlgorithm";

    // Maximum score for one line, 100 for maximum and 0 for minimum
    private final int mMaximumScoreForLine = 100;

    // Indicating the difficulty in scoring(can change by app)
    private int mScoringLevel = 15; // 0~100
    private int mScoringCompensationOffset = 0; // -100~100

    public DefaultScoringAlgorithm() {
    }

    @Override
    public float getPitchScore(float currentPitch, float currentRefPitch) {
        float scoreAfterNormalization = 0f;
        scoreAfterNormalization = AIAlgorithmScoreNative.calculatedScore(currentPitch, currentRefPitch, mScoringLevel, mScoringCompensationOffset);
        return scoreAfterNormalization;
    }

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

            if (scoreForOnePitch != null) {
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
