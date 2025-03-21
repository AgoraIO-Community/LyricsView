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
 * State and information manager for karaoke scoring functionality.
 * This class handles the non-UI related logic for pitch tracking, scoring calculation,
 * and performance evaluation. It is shared by all scoring-related components.
 */
public class ScoringMachine {
    /**
     * The current lyrics model being processed
     */
    private LyricModel mLyricsModel;

    /**
     * Listener for scoring-related events
     */
    private final OnScoringListener mListener;

    /**
     * Current pitch progress in milliseconds
     */
    private long mCurrentPitchProgress = 0;

    /**
     * Current lyrics progress in milliseconds
     */
    private long mCurrentLyricProgress = 0;

    /**
     * Maximum reference pitch value detected in the song
     */
    private float mMaximumRefPitch = 0;

    /**
     * Minimum reference pitch value detected in the song
     */
    private float mMinimumRefPitch = 100;

    /**
     * List of pitch line models for each lyrics line
     */
    private List<LyricsPitchLineModel> mPitchLines;

    /**
     * The scoring algorithm implementation
     */
    private IScoringAlgorithm mScoringAlgorithm;

    /**
     * Flag indicating whether internal scoring is being used
     */
    private boolean mUsingInternalScoring = false;

    /**
     * Default time delta between updates in milliseconds
     */
    private static final int DEFAULT_DELTA_OF_UPDATE = 20;

    /**
     * Current time delta between updates in milliseconds
     */
    private int mDeltaOfUpdate = DEFAULT_DELTA_OF_UPDATE;

    /**
     * Map storing pitch values for the current line
     */
    public final LinkedHashMap<Long, Float> mPitchesForLine = new LinkedHashMap<>();

    /**
     * Map storing scores for each line by index
     */
    public final LinkedHashMap<Integer, Integer> mScoreForEachLine = new LinkedHashMap<>();

    /**
     * Start time of the first reference pitch in milliseconds
     */
    private long mTimestampOfFirstRefPitch = -1;

    /**
     * End time of the current lyrics in milliseconds
     */
    private long mEndTimeOfThisLyrics = 0;

    /**
     * Index of the current line being processed
     */
    private int mIndexOfCurrentLine = 0;

    /**
     * Initial score for the lyrics (can be changed by application)
     */
    private float mInitialScore;

    /**
     * Cumulative score across all lines
     */
    private float mCumulativeScore;

    /**
     * Counter for continuous zero pitch detections
     */
    private int mContinuousZeroCount = 0;

    /**
     * Threshold for consecutive zero pitch readings before resetting
     */
    private static final int ZERO_PITCH_COUNT_THRESHOLD = 10;

    /**
     * Empty lyrics model used as a fallback
     */
    private static final LyricModel EMPTY_LYRICS_MODEL = new LyricModel(LyricType.LRC);

    /**
     * Constructs a new ScoringMachine instance
     *
     * @param listener The listener to handle scoring-related events
     */
    public ScoringMachine(OnScoringListener listener) {
        reset();
        this.mListener = listener;
        this.mScoringAlgorithm = new DefaultScoringAlgorithm();
    }

    /**
     * Sets the lyrics data and prepares for scoring
     *
     * @param model                The lyrics model to be processed
     * @param usingInternalScoring Whether to use internal scoring algorithm
     */
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

    /**
     * Fixes potential issues in the lyric model, such as overlapping lines
     *
     * @param model The original lyrics model
     * @return A fixed copy of the lyrics model
     */
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

    /**
     * Checks if the scoring machine is ready for operation
     *
     * @return true if lyrics model is set, false otherwise
     */
    public boolean isReady() {
        return mLyricsModel != null;
    }

    /**
     * Sets the current lyrics progress
     *
     * @param progress Current playback time in milliseconds
     */
    public void setLyricProgress(long progress) {
        mCurrentLyricProgress = progress;
    }

    /**
     * Updates the pitch data and calculates scoring
     *
     * @param speakerPitch The detected pitch from the user's voice
     * @param progressInMs The current progress in milliseconds
     */
    public void setPitch(float speakerPitch, int progressInMs) {
        if (Config.DEBUG) {
            LogUtils.d("setPitch speakerPitch:" + speakerPitch + ",progressInMs:" + progressInMs);
        }
        if (mUsingInternalScoring) {
            //ignore set pitch progress
            progressInMs = (int) mCurrentLyricProgress;
        }
        if (this.mCurrentPitchProgress >= 0 && progressInMs > 0) {
            mDeltaOfUpdate = (int) (progressInMs - this.mCurrentPitchProgress);
            if (mDeltaOfUpdate > 100 || mDeltaOfUpdate < 0) {
                //LogUtils.d("setPitch this method called not smoothly: current mDeltaOfUpdate=" + mDeltaOfUpdate + " and reset to 20ms");
                mDeltaOfUpdate = DEFAULT_DELTA_OF_UPDATE;
            }
        }

        if (progressInMs <= 0L) {
            resetStats();
            if (mListener != null) {
                mListener.resetUi();
            }
        }

        if (progressInMs < mCurrentPitchProgress) {
            whenDraggingHappen(progressInMs);
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
        float currentRefPitch = -1f;
        if (mUsingInternalScoring) {
            currentRefPitch = findRefPitchByTime(progressInMs, newLine, indexOfMostRecentLine);
            LogUtils.d("setPitch currentRefPitch:" + currentRefPitch + ",speakerPitch:" + speakerPitch + ",progressInMs:" + progressInMs + ",newLine:" + newLine[0] + ",indexOfMostRecentLine:" + indexOfMostRecentLine[0] + ",mContinuousZeroCount:" + mContinuousZeroCount);
        }

        if (speakerPitch == 0) {
            if (++mContinuousZeroCount < ZERO_PITCH_COUNT_THRESHOLD) {
                if (mUsingInternalScoring) {
                    updateScoreForMostRecentLine(progressInMs, newLine[0], indexOfMostRecentLine[0]);
                }
                return;
            }
        } else {
            mContinuousZeroCount = 0;
        }

        if (mUsingInternalScoring) {
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
        } else {
            double calculateScore = calculateScoreWithPitch(speakerPitch, progressInMs);
            if (null != mListener) {
                mListener.onPitchAndScoreUpdate(speakerPitch, (float) calculateScore, progressInMs);
            }
        }

        if (mListener != null) {
            mListener.requestRefreshUi();
        }
    }

    /**
     * Finds the reference pitch for the current time and updates line-related data
     *
     * @param timestamp                   The current timestamp in milliseconds
     * @param returnNewLine               Output parameter indicating if a new line has started
     * @param returnIndexOfMostRecentLine Output parameter with the index of the most recent line
     * @return The reference pitch at the current time, or -1 if not found
     */
    private float findRefPitchByTime(long timestamp, final boolean[] returnNewLine, final int[] returnIndexOfMostRecentLine) {
        if (mLyricsModel == null || mLyricsModel.lines == null) {
            // Not ready
            return -1;
        }

        float referencePitch = -1f;
        int numberOfLines = mLyricsModel.lines.size();
        int timestampLineIndex = -1;
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
                            timestampLineIndex = i;
                        }
                        break;
                    }
                }
                break;
            }
        }

        if (referencePitch != -1) {
            mPitchesForLine.put(timestamp, -1f);
        }

        if (isNewLine(timestamp, numberOfLines, timestampLineIndex)) {
            // Line switch
            // If timestamp is very close to start of next line, fire it at once or we will wait until later
            // A little bit of tricky here, check @Ref K329403
            // if we do not let timestamp very close to start of next line come here, it will miss one callback,
            // then timestamp is starting chasing a new mEndTimeOfCurrentRefPitch
            returnIndexOfMostRecentLine[0] = mIndexOfCurrentLine;
            returnNewLine[0] = true;
        }

        if (-1 != timestampLineIndex && timestampLineIndex != mIndexOfCurrentLine) {
            mIndexOfCurrentLine = timestampLineIndex;
        }

        // -1, 0, valid pitches
        return referencePitch;
    }

    /**
     * Determines if the current timestamp indicates a new line has started
     *
     * @param timestamp          The current timestamp in milliseconds
     * @param numberOfLines      Total number of lines in the lyrics
     * @param timestampLineIndex Current line index for the timestamp
     * @return true if a new line has started, false otherwise
     */
    private boolean isNewLine(long timestamp, int numberOfLines, int timestampLineIndex) {
        LogUtils.d("isNewLine timestamp:" + timestamp + ",numberOfLines:" + numberOfLines + ",timestampLineIndex:" + timestampLineIndex);
        boolean newLine = false;
        if (mIndexOfCurrentLine >= 0 && mIndexOfCurrentLine + 1 <= numberOfLines) {
            if (timestamp > mLyricsModel.lines.get(mIndexOfCurrentLine).getEndTime()) {
                // Current line lyrics ended
                if (-1 == timestampLineIndex) {
                    // End of current line, but not yet at the start of next line
                    if (!mScoreForEachLine.containsKey(mIndexOfCurrentLine)) {
                        newLine = true;
                    }
                } else if (mIndexOfCurrentLine != timestampLineIndex) {
                    // Changed to a new line
                    if (!mScoreForEachLine.containsKey(mIndexOfCurrentLine)) {
                        newLine = true;
                    }
                }
            }
        } else if (mIndexOfCurrentLine + 2 == numberOfLines) {
            // Last line
            if (timestamp >= mLyricsModel.lines.get(mIndexOfCurrentLine).getEndTime()) {
                if (!mScoreForEachLine.containsKey(mIndexOfCurrentLine)) {
                    newLine = true;
                }
            }
        } else {
            LogUtils.i("isNewLine mIndexOfCurrentLine:" + mIndexOfCurrentLine + ",numberOfLines:" + numberOfLines);
        }
        return newLine;
    }

    /**
     * Updates the score for the most recently completed line
     *
     * @param timestamp             Current timestamp in milliseconds
     * @param newLine               Whether a new line has started
     * @param indexOfMostRecentLine Index of the most recently completed line
     */
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

            // Add to cumulative score
            mCumulativeScore += scoreThisTime;

            LogUtils.d("updateScoreForMostRecentLine timestamp:" + timestamp + " index:" + indexOfMostRecentLine + " startTime:" + lineJustFinished.getStartTime() +
                    ",endTime:" + lineJustFinished.getEndTime() + ",scoreThisTime:" + scoreThisTime + ",mCumulativeScore:" + mCumulativeScore + ",lyricsContentLine:" + lyricsContentLine);


            if (mListener != null) {
                mListener.onLineFinished(lineJustFinished, scoreThisTime, (int) mCumulativeScore, indexOfMostRecentLine, mLyricsModel.lines.size());
            }

            // Cache it for dragging
            mScoreForEachLine.put(indexOfMostRecentLine, scoreThisTime);
        }
    }

    /**
     * Calculates a score based on the difference between speaker pitch and reference pitch
     *
     * @param speakerPitch The detected pitch from the user's voice
     * @param progressInMs The current progress in milliseconds
     * @return The calculated score (0-100)
     */
    public double calculateScoreWithPitch(float speakerPitch, int progressInMs) {
        mCurrentPitchProgress = progressInMs;
        float refPitch = getRefPitch(progressInMs);
        double scoreAfterNormalization = 0;
        if (refPitch != 0) {
            scoreAfterNormalization = Math.abs(speakerPitch - refPitch) < 5 ? 100 : 0;
        }
        return scoreAfterNormalization;
    }

    /**
     * Handles progress updates when dragging occurs
     *
     * @param progress The new progress position in milliseconds
     */
    public void whenDraggingHappen(long progress) {
        minorReset();

        for (int index = 0; index < mLyricsModel.lines.size(); index++) {
            LyricsLineModel line = mLyricsModel.lines.get(index);
            if (progress <= line.getStartTime()) {
                mScoreForEachLine.remove(index); // Erase the score item >= progress
            }
        }

        // Re-calculate when dragging happen
        mCumulativeScore = mInitialScore;
        for (Integer score : mScoreForEachLine.values()) {
            mCumulativeScore += score;
        }
    }

    /**
     * Resets all states and properties
     */
    public void reset() {
        resetProperties();
        resetStats();
    }

    /**
     * Resets properties when song changes
     */
    private void resetProperties() {
        mLyricsModel = null;
        mMinimumRefPitch = 100;
        mMaximumRefPitch = 0;

        mTimestampOfFirstRefPitch = -1;

        mEndTimeOfThisLyrics = 0;
    }

    /**
     * Resets statistics and scores
     */
    private void resetStats() {
        minorReset();

        // Partially reset according to the corresponded action
        mCumulativeScore = mInitialScore;
        mScoreForEachLine.clear();
    }

    /**
     * Performs a minor reset that will recover immediately
     */
    private void minorReset() {
        mCurrentPitchProgress = 0;
        mCurrentLyricProgress = 0;
        mIndexOfCurrentLine = 0;

        mPitchesForLine.clear();
    }

    /**
     * Prepares the UI for scoring display
     */
    public void prepareUi() {
        if (mListener != null) {
            mListener.resetUi();
        }
    }

    /**
     * Gets the current lyrics model
     *
     * @return The current lyrics model
     */
    public LyricModel getLyricsModel() {
        return this.mLyricsModel;
    }

    /**
     * Gets the current pitch progress
     *
     * @return Current progress in milliseconds
     */
    public long getCurrentPitchProgress() {
        return mCurrentPitchProgress != 0 ? mCurrentPitchProgress : mCurrentLyricProgress;
    }

    /**
     * Gets the minimum reference pitch in the song
     *
     * @return The minimum reference pitch value
     */
    public float getMinimumRefPitch() {
        return this.mMinimumRefPitch;
    }

    /**
     * Gets the maximum reference pitch in the song
     *
     * @return The maximum reference pitch value
     */
    public float getMaximumRefPitch() {
        return this.mMaximumRefPitch;
    }

    /**
     * Gets the list of pitch line models
     *
     * @return List of pitch line models
     */
    public List<LyricsPitchLineModel> getPitchLines() {
        return mPitchLines;
    }

    /**
     * Checks if the lyrics model has pitch data
     *
     * @return true if pitch data is available, false otherwise
     */
    public boolean hasPitchData() {
        if (null == mLyricsModel) {
            return false;
        }
        return mLyricsModel.hasPitch;
    }

    /**
     * Gets the start time of a specific line
     *
     * @param lineIndex The index of the line
     * @return The start time in milliseconds, or 0 if index is invalid
     */
    public long getLineStartTime(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= mLyricsModel.lines.size()) {
            return 0;
        }

        return mLyricsModel.lines.get(lineIndex).getStartTime();
    }

    /**
     * Gets the reference pitch at a specific time
     *
     * @param progressInMs The time in milliseconds
     * @return The reference pitch at the specified time, or 0 if not found
     */
    public float getRefPitch(int progressInMs) {
        if (mLyricsModel == null) {
            return 0;
        }
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

    /**
     * Sets the initial score for the performance
     *
     * @param initialScore The initial score value
     */
    public void setInitialScore(float initialScore) {
        this.mCumulativeScore += initialScore;
        this.mInitialScore = initialScore;
    }

    /**
     * Sets the scoring algorithm to be used
     *
     * @param algorithm The scoring algorithm implementation
     * @throws IllegalArgumentException if algorithm is null
     */
    public void setScoringAlgorithm(IScoringAlgorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("IScoringAlgorithm should not be an empty object");
        }
        this.mScoringAlgorithm = algorithm;
    }

    /**
     * Sets the scoring difficulty level
     *
     * @param level The difficulty level
     */
    public void setScoringLevel(int level) {
        if (null != mScoringAlgorithm) {
            mScoringAlgorithm.setScoringLevel(level);
        }
    }

    /**
     * Gets the current scoring difficulty level
     *
     * @return The current difficulty level
     */
    public int getScoringLevel() {
        if (null != mScoringAlgorithm) {
            return mScoringAlgorithm.getScoringLevel();
        }
        return 0;
    }

    /**
     * Sets the scoring compensation offset
     *
     * @param offset The compensation offset value
     */
    public void setScoringCompensationOffset(int offset) {
        if (null != mScoringAlgorithm) {
            mScoringAlgorithm.setScoringCompensationOffset(offset);
        }
    }

    /**
     * Gets the current scoring compensation offset
     *
     * @return The current compensation offset
     */
    public int getScoringCompensationOffset() {
        if (null != mScoringAlgorithm) {
            return mScoringAlgorithm.getScoringCompensationOffset();
        }
        return 0;
    }

    /**
     * Checks if internal scoring is being used
     *
     * @return true if internal scoring is enabled, false otherwise
     */
    public boolean isUsingInternalScoring() {
        return mUsingInternalScoring;
    }

    /**
     * Interface for handling scoring-related events
     */
    public interface OnScoringListener {
        /**
         * Called when UI needs to be reset
         */
        void resetUi();

        /**
         * Called when UI refresh is requested
         */
        void requestRefreshUi();

        /**
         * Called when pitch and score are updated
         *
         * @param speakerPitch            The processed pitch from the user's voice
         * @param scoreAfterNormalization The normalized score (0-100)
         * @param progress                The current progress in milliseconds
         */
        void onPitchAndScoreUpdate(float speakerPitch, double scoreAfterNormalization, long progress);

        /**
         * Called automatically when a line is finished
         *
         * @param line            The lyrics line model that was just finished
         * @param score           The score for this line
         * @param cumulativeScore The total cumulative score so far
         * @param index           The index of the finished line
         * @param lineCount       The total number of lines
         */
        void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount);
    }
}
