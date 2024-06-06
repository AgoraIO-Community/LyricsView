package io.agora.karaoke_view_ex.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import io.agora.karaoke_view_ex.IScoringAlgorithm;
import io.agora.karaoke_view_ex.internal.ai.AIAlgorithmScoreNative;
import io.agora.karaoke_view_ex.internal.config.Config;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.model.LyricsPitchLineModel;
import io.agora.karaoke_view_ex.internal.model.PitchData;
import io.agora.karaoke_view_ex.internal.scoring.DefaultScoringAlgorithm;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * State/Information of playing/rendering/on-going lyrics
 * <p>
 * Non-ui related, shared by all components
 */
public class ScoringMachine {
    private LyricModel mLyricsModel;
    private final OnScoringListener mListener;
    private long mCurrentPitchProgress = 0;
    private long mCurrentLyricProgress = 0;
    private float mMaximumRefPitch = 0;
    private float mMinimumRefPitch = 100;

    private List<LyricsPitchLineModel> mPitchLines;

    private IScoringAlgorithm mScoringAlgorithm;
    private boolean mUsingInternalScoring = false;

    private int mDeltaOfUpdate = 40;


    // Pitches for every line, we will reset every new line
    public final LinkedHashMap<Long, Float> mPitchesForLine = new LinkedHashMap<>();

    public final LinkedHashMap<Integer, Integer> mScoreForEachLine = new LinkedHashMap<>();

    // Start time of first pitch or word or tone
    private long mTimestampOfFirstRefPitch = -1;

    // End time of this lyrics
    private long mEndTimeOfThisLyrics = 0;
    // Index of current line
    private int mIndexOfCurrentLine = -1;

    // Initial score for the lyrics(can change by app)
    private float mInitialScore;
    // Cumulative score
    private float mCumulativeScore;

    private int mContinuousZeroCount = 0;
    private static final int ZERO_PITCH_COUNT_THRESHOLD = 10;

    public ScoringMachine(OnScoringListener listener) {
        reset();
        this.mListener = listener;
        this.mScoringAlgorithm = new DefaultScoringAlgorithm();
    }

    private static final LyricModel EMPTY_LYRICS_MODEL = new LyricModel(LyricType.LRC);

    // Set data and prepare to rendering
    public void prepare(LyricModel model, boolean usingInternalScoring) {
        reset();

        if (model == null || model.lines == null || model.lines.isEmpty()) {
            LogUtils.e("Invalid lyrics model, use built-in EMPTY_LYRICS_MODEL");
            mLyricsModel = EMPTY_LYRICS_MODEL;
            return;
        }

        mLyricsModel = mayBeFixLyricModel(model);
        mUsingInternalScoring = usingInternalScoring;

        mEndTimeOfThisLyrics = model.lines.get(model.lines.size() - 1).getEndTime();
        // find the timestamp of first reference pitch
        mTimestampOfFirstRefPitch = model.preludeEndPosition;

        if (mLyricsModel.hasPitch) {
            if (model.pitchDataList != null) {
                for (PitchData data : model.pitchDataList) {
                    mMinimumRefPitch = (float) Math.min(mMinimumRefPitch, data.pitch);
                    mMaximumRefPitch = (float) Math.max(mMaximumRefPitch, data.pitch);
                }
                if (null != mLyricsModel.lines) {
                    mPitchLines = new ArrayList<>(mLyricsModel.lines.size());
                    for (LyricsLineModel line : mLyricsModel.lines) {
                        LyricsPitchLineModel lineModel = new LyricsPitchLineModel();
                        long startTime = line.getStartTime();
                        long endTime = line.getEndTime();
                        for (PitchData data : model.pitchDataList) {
                            if (data.startTime >= startTime && data.startTime < endTime) {
                                LyricsPitchLineModel.Pitch pitch = new LyricsPitchLineModel.Pitch();
                                pitch.begin = data.startTime;
                                pitch.end = data.startTime + data.duration;
                                pitch.pitch = (int) data.pitch;
                                lineModel.pitches.add(pitch);
                            }
                        }
                        mPitchLines.add(lineModel);
                    }
                }
            } else {
                if (null != mLyricsModel.lines) {
                    mPitchLines = new ArrayList<>(mLyricsModel.lines.size());
                    for (LyricsLineModel line : mLyricsModel.lines) {
                        LyricsPitchLineModel lineModel = new LyricsPitchLineModel();
                        if (null != line.tones) {
                            for (LyricsLineModel.Tone tone : line.tones) {
                                LyricsPitchLineModel.Pitch pitch = new LyricsPitchLineModel.Pitch();
                                pitch.begin = tone.begin;
                                pitch.end = tone.end;
                                pitch.pitch = (int) tone.pitch;

                                mMinimumRefPitch = (float) Math.min(mMinimumRefPitch, pitch.pitch);
                                mMaximumRefPitch = (float) Math.max(mMaximumRefPitch, pitch.pitch);

                                lineModel.pitches.add(pitch);
                            }
                        }
                        mPitchLines.add(lineModel);
                    }
                }
            }
        }

        LogUtils.d("prepare mMinimumRefPitch:" + mMinimumRefPitch + ",mMaximumRefPitch:" + mMaximumRefPitch);
    }

    private LyricModel mayBeFixLyricModel(LyricModel model) {
        if (model == null || model.lines == null || model.lines.isEmpty()) {
            LogUtils.e("Invalid lyrics model, use built-in EMPTY_LYRICS_MODEL");
            return EMPTY_LYRICS_MODEL;
        }
        LyricModel newModel = model.copy();

        LyricsLineModel preLineModel = newModel.lines.get(0);
        for (int i = 1; i < newModel.lines.size(); i++) {
            LyricsLineModel lineModel = newModel.lines.get(i);
            if (lineModel.getStartTime() < preLineModel.getEndTime()) {
                List<LyricsLineModel.Tone> tones = lineModel.tones;
                if (null != tones && !tones.isEmpty()) {
                    for (LyricsLineModel.Tone tone : tones) {
                        if (tone.begin < preLineModel.getEndTime()) {
                            tone.begin = preLineModel.getEndTime();
                            if (tone.end < tone.begin) {
                                tone.end = tone.begin;
                            }
                        } else {
                            break;
                        }
                    }
                }
            }
            preLineModel = lineModel;
        }
        return model;
    }

    public boolean isReady() {
        return mLyricsModel != null;
    }

    public void setLyricProgress(long progress) {
        mCurrentLyricProgress = progress;
    }

    public void setPitch(float speakerPitch, int progressInMs) {
        if (Config.DEBUG) {
            LogUtils.d("setPitch speakerPitch:" + speakerPitch + ",progressInMs:" + progressInMs);
        }
        if (this.mCurrentPitchProgress >= 0 && progressInMs > 0) {
            mDeltaOfUpdate = (int) (progressInMs - this.mCurrentPitchProgress);
            if (mDeltaOfUpdate > 100 || mDeltaOfUpdate < 0) {
                //LogUtils.d("setPitch this method called not smoothly: current mDeltaOfUpdate=" + mDeltaOfUpdate + " and reset to 20ms");
                mDeltaOfUpdate = 40;
            }
        }

        if (progressInMs <= 0L) {
            resetStats();
            if (mListener != null) {
                mListener.resetUi();
            }
        }

        mCurrentPitchProgress = progressInMs;

        if (mLyricsModel == null) {
            if (mListener != null) {
                mListener.resetUi();
            }
            return;
        }

        boolean[] newLine = new boolean[1];
        int[] indexOfMostRecentLine = new int[]{-1};
        float currentRefPitch = findRefPitchByTime(progressInMs, newLine, indexOfMostRecentLine);

        if (speakerPitch == 0) {
            if (++mContinuousZeroCount < ZERO_PITCH_COUNT_THRESHOLD) {
                updateScoreForMostRecentLine(progressInMs, newLine[0], indexOfMostRecentLine[0]);
                return;
            }
        } else {
            mContinuousZeroCount = 0;
        }

        // No ref pitch, just ignore this time
        if (currentRefPitch <= 0 || mContinuousZeroCount >= ZERO_PITCH_COUNT_THRESHOLD) {
            mContinuousZeroCount = 0;
            updateScoreForMostRecentLine(progressInMs, newLine[0], indexOfMostRecentLine[0]);
            if (mListener != null) {
                mListener.resetUi();
            }
            return;
        }

        updateScoreForMostRecentLine(progressInMs, newLine[0], indexOfMostRecentLine[0]);

        float pitchAfterProcess = (float) AIAlgorithmScoreNative.handlePitch(currentRefPitch, speakerPitch, this.mMaximumRefPitch);
        float scoreAfterNormalization = mScoringAlgorithm.getPitchScore(pitchAfterProcess, currentRefPitch);
        mPitchesForLine.put((long) progressInMs, scoreAfterNormalization);

        if (mListener != null) {
            mListener.onPitchAndScoreUpdate(pitchAfterProcess, scoreAfterNormalization, progressInMs);
        }

        if (mListener != null) {
            mListener.requestRefreshUi();
        }
    }

    /**
     * 根据当前播放时间获取 Pitch，并且更新当前 Pitch 相关数据
     * <p>
     *
     * @return 当前时间歌词的 Pitch 以及是否换行 returnNewLine 和 returnIndexOfMostRecentLine
     */
    private float findRefPitchByTime(long timestamp, final boolean[] returnNewLine, final int[] returnIndexOfMostRecentLine) {
        if (mLyricsModel == null || mLyricsModel.lines == null) {
            // Not ready
            return -1;
        }

        float referencePitch = -1f;
        int numberOfLines = mLyricsModel.lines.size();
        for (int i = 0; i < numberOfLines; i++) {
            LyricsLineModel line = mLyricsModel.lines.get(i);
            if (timestamp >= line.getStartTime() && timestamp <= line.getEndTime()) {
                int numberOfTones = line.tones.size();
                for (int j = 0; j < numberOfTones; j++) {
                    LyricsLineModel.Tone tone = line.tones.get(j);
                    if (timestamp >= tone.begin && timestamp <= tone.end) {
                        referencePitch = tone.pitch;

                        // Last tone in this line
                        if (j == numberOfTones - 1) {
                            mIndexOfCurrentLine = i;
                        }
                        break;
                    }
                }
                break;
            }
        }


        if (isNewLine(timestamp, numberOfLines)) {
            // Line switch
            // If timestamp is very close to start of next line, fire it at once or we will wait until later
            // A little bit of tricky here, check @Ref K329403
            // if we do not let timestamp very close to start of next line come here, it will miss one callback,
            // then timestamp is starting chasing a new mEndTimeOfCurrentRefPitch
            returnIndexOfMostRecentLine[0] = mIndexOfCurrentLine;
            returnNewLine[0] = true;
            mIndexOfCurrentLine = -1;
        }

        // -1, 0, valid pitches
        return referencePitch;
    }

    private boolean isNewLine(long timestamp, int numberOfLines) {
        boolean newLine = false;
        if (mIndexOfCurrentLine >= 0) {
            if (timestamp > mLyricsModel.lines.get(mIndexOfCurrentLine).getEndTime()) {
                newLine = true;
            } else {
                if (mIndexOfCurrentLine + 1 < numberOfLines) {
                    if (Config.DEBUG) {
                        LogUtils.d("updateScoreForMostRecentLine isNewLine mIndexOfCurrentLine:" + mIndexOfCurrentLine + ",numberOfLines:" + numberOfLines + ",timestamp:" + timestamp + ",current end:" + mLyricsModel.lines.get(mIndexOfCurrentLine).getEndTime() + ",next start:" + mLyricsModel.lines.get(mIndexOfCurrentLine + 1).getStartTime() + ",mDeltaOfUpdate:" + mDeltaOfUpdate);
                    }
                    if (mLyricsModel.lines.get(mIndexOfCurrentLine).getEndTime() == mLyricsModel.lines.get(mIndexOfCurrentLine + 1).getStartTime()) {
                        if ((timestamp + mDeltaOfUpdate) > mLyricsModel.lines.get(mIndexOfCurrentLine + 1).getStartTime()) {
                            newLine = true;
                        }
                    } else if (mLyricsModel.lines.get(mIndexOfCurrentLine).getEndTime() < mLyricsModel.lines.get(mIndexOfCurrentLine + 1).getStartTime()) {
                        if ((timestamp + mDeltaOfUpdate) >= mLyricsModel.lines.get(mIndexOfCurrentLine + 1).getStartTime()) {
                            newLine = true;
                        }
                    } else {
                        //nothing to do
//                        if ((timestamp + mDeltaOfUpdate) > mLyricsModel.lines.get(mIndexOfCurrentLine).getEndTime()) {
//                            newLine = true;
//                        }
                    }
                }
            }
        }
        return newLine;
    }

    private void updateScoreForMostRecentLine(long timestamp, boolean newLine, int indexOfMostRecentLine) {
        // Not started
        if ((timestamp < mTimestampOfFirstRefPitch) || mTimestampOfFirstRefPitch == -1) {
            return;
        }

        // After lyrics ended, do not need to update again
        if (timestamp > mEndTimeOfThisLyrics + (2L * mDeltaOfUpdate)) {
            return;
        }


        if (newLine) {
            LyricsLineModel lineJustFinished = mLyricsModel.lines.get(indexOfMostRecentLine);
            int scoreThisTime = mScoringAlgorithm.getLineScore(mPitchesForLine, indexOfMostRecentLine, lineJustFinished);

            StringBuilder lyricsContentLine = new StringBuilder();
            for (LyricsLineModel.Tone tone : lineJustFinished.tones) {
                lyricsContentLine.append(tone.word);
            }

            // 统计到累计分数
            mCumulativeScore += scoreThisTime;

            LogUtils.d("updateScoreForMostRecentLine timestamp:" + timestamp + " index:" + indexOfMostRecentLine + " startTime:" + lineJustFinished.getStartTime() +
                    ",endTime:" + lineJustFinished.getEndTime() + ",lyricsContentLine:" + lyricsContentLine + ",scoreThisTime:" + scoreThisTime + ",mCumulativeScore:" + mCumulativeScore);


            if (mListener != null) {
                mListener.onLineFinished(lineJustFinished, scoreThisTime, (int) mCumulativeScore, indexOfMostRecentLine, mLyricsModel.lines.size());
            }

            // Cache it for dragging
            mScoreForEachLine.put(indexOfMostRecentLine, scoreThisTime);
        }
    }

    public double calculateScoreWithPitch(float speakerPitch, int progressInMs) {
        mCurrentPitchProgress = progressInMs;
        float refPitch = getRefPitch(progressInMs);
        double scoreAfterNormalization = 0;
        if (refPitch != 0) {
            scoreAfterNormalization = Math.abs(speakerPitch - refPitch) < 5 ? 100 : 0;
        }
        return scoreAfterNormalization;
    }

    public void whenDraggingHappen(long progress) {
        minorReset();

        for (int index = 0; index < mLyricsModel.lines.size(); index++) {
            LyricsLineModel line = mLyricsModel.lines.get(index);
            if (progress <= line.getEndTime()) {
                mScoreForEachLine.put(index, 0); // Erase the score item >= progress
            }
        }

        // Re-calculate when dragging happen
        mCumulativeScore = mInitialScore;
        for (Integer score : mScoreForEachLine.values()) {
            mCumulativeScore += score;
        }
    }

    public void reset() {
        resetProperties();

        resetStats();
    }

    private void resetProperties() { // Reset when song changed
        mLyricsModel = null;
        mMinimumRefPitch = 100;
        mMaximumRefPitch = 0;

        mTimestampOfFirstRefPitch = -1;

        mEndTimeOfThisLyrics = 0;
    }

    private void resetStats() {
        minorReset();

        // Partially reset according to the corresponded action
        mCumulativeScore = mInitialScore;
        mScoreForEachLine.clear();
    }

    // Will recover immediately
    private void minorReset() {
        mCurrentPitchProgress = 0;
        mCurrentLyricProgress = 0;
        mIndexOfCurrentLine = -1;

        mPitchesForLine.clear();
    }

    public void prepareUi() {
        if (mListener != null) {
            mListener.resetUi();
        }
    }

    public LyricModel getLyricsModel() {
        return this.mLyricsModel;
    }


    public long getCurrentPitchProgress() {
        return mCurrentPitchProgress != 0 ? mCurrentPitchProgress : mCurrentLyricProgress;
    }

    public float getMinimumRefPitch() {
        return this.mMinimumRefPitch;
    }

    public float getMaximumRefPitch() {
        return this.mMaximumRefPitch;
    }

    public List<LyricsPitchLineModel> getPitchLines() {
        return mPitchLines;
    }

    public boolean hasPitchData() {
        if (null == mLyricsModel) {
            return false;
        }
        return mLyricsModel.hasPitch;
    }

    public long getLineStartTime(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= mLyricsModel.lines.size()) {
            return 0;
        }

        return mLyricsModel.lines.get(lineIndex).getStartTime();
    }

    public float getRefPitch(int progressInMs) {
        if (null != mLyricsModel && mLyricsModel.hasPitch) {
            if (null != mLyricsModel.pitchDataList) {
                for (PitchData data : mLyricsModel.pitchDataList) {
                    if (data.startTime <= progressInMs && data.startTime + data.duration >= progressInMs) {
                        return data.pitch;
                    }
                }
            } else {
                if (null != mLyricsModel.lines) {
                    for (LyricsLineModel line : mLyricsModel.lines) {
                        for (LyricsLineModel.Tone tone : line.tones) {
                            if (tone.begin <= progressInMs && tone.end >= progressInMs) {
                                return tone.pitch;
                            }
                        }
                    }
                }
            }
        }
        return 0;
    }

    public void setInitialScore(float initialScore) {
        this.mCumulativeScore += initialScore;
        this.mInitialScore = initialScore;
    }

    public void setScoringAlgorithm(IScoringAlgorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("IScoringAlgorithm should not be an empty object");
        }
        this.mScoringAlgorithm = algorithm;
    }

    public void setScoringLevel(int level) {
        if (null != mScoringAlgorithm) {
            mScoringAlgorithm.setScoringLevel(level);
        }
    }

    public int getScoringLevel() {
        if (null != mScoringAlgorithm) {
            return mScoringAlgorithm.getScoringLevel();
        }
        return 0;
    }

    public void setScoringCompensationOffset(int offset) {
        if (null != mScoringAlgorithm) {
            mScoringAlgorithm.setScoringCompensationOffset(offset);
        }
    }

    public int getScoringCompensationOffset() {
        if (null != mScoringAlgorithm) {
            return mScoringAlgorithm.getScoringCompensationOffset();
        }
        return 0;
    }

    public boolean isUsingInternalScoring() {
        return mUsingInternalScoring;
    }


    public interface OnScoringListener {
        void resetUi();

        void requestRefreshUi();

        void onPitchAndScoreUpdate(float speakerPitch, double scoreAfterNormalization, long progress);

        /**
         * Called automatically when the line is finished
         * <p>
         * Do not block this callback
         *
         * @param line            LyricsLineModel
         * @param score           score
         * @param cumulativeScore cumulativeScore
         * @param index           index
         * @param lineCount       lineCount
         */
        void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount);
    }
}
