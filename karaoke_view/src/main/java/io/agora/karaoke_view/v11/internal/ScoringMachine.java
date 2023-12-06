package io.agora.karaoke_view.v11.internal;

import android.util.Log;

import java.util.LinkedHashMap;

import io.agora.karaoke_view.v11.IScoringAlgorithm;
import io.agora.karaoke_view.v11.VoicePitchChanger;
import io.agora.karaoke_view.v11.ai.AINative;
import io.agora.karaoke_view.v11.config.Config;
import io.agora.karaoke_view.v11.logging.LogManager;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;

/**
 * State/Information of playing/rendering/on-going lyrics
 * <p>
 * Non-ui related, shared by all components
 */
public class ScoringMachine {
    private static final String TAG = "ScoringMachine";

    private static final boolean DEBUG = false;

    private LyricsModel mLyricsModel;

    private int mMaximumRefPitch = 0;
    private int mMinimumRefPitch = 100; // FIXME(Hai_Guo Should not be zero, song 夏天)
    private int mNumberOfRefPitches = 0;
    // Start time of first pitch or word or tone
    private long mTimestampOfFirstRefPitch = -1;

    // Start time of current reference pitch or word or tone
    private long mStartTimeOfCurrentRefPitch = -1;
    // End time of current reference pitch or word
    private long mEndTimeOfCurrentRefPitch = -1;

    // End time of this lyrics
    private long mEndTimeOfThisLyrics = 0;
    // Index of current line
    private int mIndexOfCurrentLine = -1;
    private long mMarkOfLineEndEventFire = -1;

    // Current progress for this lyrics
    private long mCurrentProgress = 0;
    // Delta of time between updates
    private int mDeltaOfUpdate = 20;

    // Reference pitch for current timestamp
    private float mRefPitchForCurrentProgress = -1f;

    // Pitches for every line, we will reset every new line
    public final LinkedHashMap<Long, Float> mPitchesForLine = new LinkedHashMap<>();

    public final LinkedHashMap<Integer, Integer> mScoreForEachLine = new LinkedHashMap<>();

    // In highlighting status, always with some shiny animations
    private boolean mInHighlightingStatus;

    // Initial score for the lyrics(can change by app)
    private float mInitialScore;

    // Cumulative score
    private float mCumulativeScore;

    // Full marks/perfect for the lyrics
    private float mPerfectScore;

    private VoicePitchChanger mVoicePitchChanger;

    private IScoringAlgorithm mScoringAlgo;

    private OnScoringListener mListener;

    public ScoringMachine(VoicePitchChanger changer, IScoringAlgorithm algo, OnScoringListener listener) {
        reset();

        this.mVoicePitchChanger = changer;
        setScoringAlgorithm(algo);
        this.mListener = listener;
    }

    private static final LyricsModel EMPTY_LYRICS_MODEL = new LyricsModel(LyricsModel.Type.General);

    // Set data and prepare to rendering
    public void prepare(LyricsModel model) {
        reset();

        if (model == null || model.lines == null || model.lines.isEmpty()) {
            LogManager.instance().warn(TAG, "Invalid lyrics model, use built-in EMPTY_LYRICS_MODEL");
            mLyricsModel = EMPTY_LYRICS_MODEL;
            return;
        }

        mLyricsModel = model;

        mEndTimeOfThisLyrics = model.lines.get(model.lines.size() - 1).getEndTime();
        mPerfectScore = mScoringAlgo.getMaximumScoreForLine() * model.lines.size();

        for (LyricsLineModel line : model.lines) {
            for (LyricsLineModel.Tone tone : line.tones) {
                mMinimumRefPitch = Math.min(mMinimumRefPitch, tone.pitch);
                mMaximumRefPitch = Math.max(mMaximumRefPitch, tone.pitch);
                mNumberOfRefPitches++;
            }
        }

        mTimestampOfFirstRefPitch = model.startOfVerse; // find the timestamp of first reference pitch
    }

    public boolean isReady() {
        return mLyricsModel != null;
    }

    public void setScoringAlgorithm(IScoringAlgorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("IScoringAlgorithm should not be an empty object");
        }
        this.mScoringAlgo = algorithm;
    }

    /**
     * 根据当前播放时间获取 Pitch，并且更新当前 Pitch 相关数据
     * <p>
     * {@link this#mStartTimeOfCurrentRefPitch}
     * {@link this#mEndTimeOfCurrentRefPitch}
     *
     * @return 当前时间歌词的 Pitch 以及是否换行 returnNewLine 和 returnIndexOfMostRecentLine
     */
    private float findRefPitchByTime(long timestamp, final boolean[] returnNewLine, final int[] returnIndexOfMostRecentLine) {
        if (mLyricsModel == null || mLyricsModel.lines == null) {
            return -1; // Not ready
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
                        mStartTimeOfCurrentRefPitch = tone.begin;
                        mEndTimeOfCurrentRefPitch = tone.end;

                        if (j == numberOfTones - 1) { // Last tone in this line
                            mIndexOfCurrentLine = i;
                            mMarkOfLineEndEventFire = line.getEndTime();
                        }
                        break;
                    }
                }
                break;
            }
        }

        if (referencePitch == -1f) { // No ref pitch hit
            mStartTimeOfCurrentRefPitch = -1;
            mEndTimeOfCurrentRefPitch = -1;
        } else { // If hit the ref pitch(whenever 0 or > 0)
            mPitchesForLine.put(timestamp, 0f);
        }

        if (DEBUG) {
            Log.d(TAG, "debugScoringAlgo/mPitchesForLine/STUB: timestamp=" + timestamp + ", referencePitch=" + referencePitch + ", mIndexOfCurrentLine=" + mIndexOfCurrentLine + ", mMarkOfLineEndEventFire=" + mMarkOfLineEndEventFire);
        }

        if (mIndexOfCurrentLine >= 0 && ( /** VeryCloseToNextLine @Ref K329404 **/((mIndexOfCurrentLine + 1 < numberOfLines) && ((timestamp + mDeltaOfUpdate) >= mLyricsModel.lines.get(mIndexOfCurrentLine + 1).getStartTime()))
                || /** MostRecentLineJustPassed **/timestamp > mMarkOfLineEndEventFire)) { // Line switch
            // If timestamp is very close to start of next line, fire it at once or we will wait until later
            // A little bit of tricky here, check @Ref K329403
            // if we do not let timestamp very close to start of next line come here, it will miss one callback,
            // 'cause mStartTimeOfCurrentRefPitch/mEndTimeOfCurrentRefPitch will change next time
            // then timestamp is starting chasing a new mEndTimeOfCurrentRefPitch
            returnIndexOfMostRecentLine[0] = mIndexOfCurrentLine;
            returnNewLine[0] = true;
            mIndexOfCurrentLine = -1;
            mMarkOfLineEndEventFire = -1;
        }

        mRefPitchForCurrentProgress = referencePitch;

        return referencePitch; // -1, 0, valid pitches
    }

    public static float calculateScore2(double minimumScore, int scoringLevel, int scoringCompensationOffset, double pitch, double refPitch) {
        float tone = (float) pitchToTone(pitch);
        float refTone = (float) pitchToTone(refPitch);

        float scoreAfterNormalization; // [0, 1]
        scoreAfterNormalization = (float) (1.f - (scoringLevel * Math.abs(tone - refTone)) / 100.f + scoringCompensationOffset * 1.0 / 100.f);

        // 得分线以下的分数归零
        scoreAfterNormalization = scoreAfterNormalization >= minimumScore ? scoreAfterNormalization : 0f;
        // 得分太大的置一
        scoreAfterNormalization = scoreAfterNormalization > 1 ? 1 : scoreAfterNormalization;

        if (DEBUG) {
            Log.d(TAG, "debugScoringAlgo/calculateScore2/REAL: minimumScore=" + minimumScore + ", pitch=" + pitch + ", refPitch=" + refPitch +
                    ", level=" + scoringLevel + ", compensationOffset=" + scoringCompensationOffset);
        }

        return scoreAfterNormalization;
    }

    public static double pitchToTone(double pitch) {
        double eps = 1e-6;
        return (Math.max(0, Math.log(pitch / 55 + eps) / Math.log(2))) * 12;
    }

    private void updateScoreForMostRecentLine(long timestamp, boolean newLine, int indexOfMostRecentLine) {
        if ((timestamp < mTimestampOfFirstRefPitch) || mTimestampOfFirstRefPitch == -1) { // Not started
            return;
        }

        if (timestamp > mEndTimeOfThisLyrics + (2L * mDeltaOfUpdate)) { // After lyrics ended, do not need to update again
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "updateScoreForMostRecentLine: timestamp=" + timestamp + ", mEndTimeOfThisLyrics=" + mEndTimeOfThisLyrics + ", numberOfPitchScores=" + mPitchesForLine.size()
                    + "\n" + newLine + "(" + indexOfMostRecentLine + ")"
                    + "\n indexOfMostRecentLine: " + indexOfMostRecentLine + ", mIndexOfCurrentLine=" + mIndexOfCurrentLine + ", delta= " + mDeltaOfUpdate);
        }

        if (newLine && !mPitchesForLine.isEmpty()) {
            LyricsLineModel lineJustFinished = mLyricsModel.lines.get(indexOfMostRecentLine);
            int scoreThisTime = mScoringAlgo.getLineScore(mPitchesForLine, indexOfMostRecentLine, lineJustFinished);

            // 统计到累计分数
            mCumulativeScore += scoreThisTime;

            if (mListener != null) {
                mListener.onLineFinished(lineJustFinished, scoreThisTime, (int) mCumulativeScore, (int) mPerfectScore, indexOfMostRecentLine, mLyricsModel.lines.size());
            }

            // Cache it for dragging
            mScoreForEachLine.put(indexOfMostRecentLine, scoreThisTime);
        }
    }

    /**
     * 更新歌词进度，单位毫秒
     * 根据当前时间，决定是否回调 {@link ScoringMachine.OnScoringListener#onRefPitchUpdate(float, int, long progress)}}
     * 并驱动打分
     *
     * @param progress 当前播放时间，毫秒
     *                 progress 一定要大于歌词结束时间才可以触发最后一句的回调
     *                 {@link ScoringMachine.OnScoringListener#onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines)}
     */
    public void setProgress(long progress) {
        if (this.mCurrentProgress >= 0 && progress > 0) {
            mDeltaOfUpdate = (int) (progress - this.mCurrentProgress);
            if (mDeltaOfUpdate > 100 || mDeltaOfUpdate < 0) {
                // TODO(Hai_Guo) Need to show warning information when this method called not smoothly
            }
        }

        if (progress <= 0L) {
            resetStats();
            if (mListener != null) {
                mListener.resetUi();
            }
        }

        this.mCurrentProgress = progress;

        if (mLyricsModel == null) {
            if (mListener != null) {
                mListener.resetUi();
            }
            return;
        }

        boolean[] newLine = new boolean[1];
        int[] indexOfMostRecentLine = new int[]{-1};
        // Fire just after line finished
        // mStartTimeOfCurrentRefPitch(55147) mEndTimeOfCurrentRefPitch(57949) timestamp(58000)
        // mStartTimeOfCurrentRefPitch(-1) mEndTimeOfCurrentRefPitch(-1) timestamp(58000), indexOfMostRecentLine=1:true, currentRefPitch=-1

        // Fire a little bit earlier
        // mStartTimeOfCurrentRefPitch(143602) mEndTimeOfCurrentRefPitch(145303) timestamp(145300)
        // mStartTimeOfCurrentRefPitch(143602) mEndTimeOfCurrentRefPitch(145303) timestamp(145300), indexOfMostRecentLine=9:true, currentRefPitch=60.0
        if (progress < mStartTimeOfCurrentRefPitch
                || /** VeryCloseToEndOfCurrentLine @Ref K329403 **/(progress + (mDeltaOfUpdate)) > mEndTimeOfCurrentRefPitch) {
            // check @Ref K329404 for more information
            float currentRefPitch = findRefPitchByTime(progress, newLine, indexOfMostRecentLine);
            if (currentRefPitch > -1f && mListener != null) {
                mListener.onRefPitchUpdate(currentRefPitch, mNumberOfRefPitches, progress);
            }
        }

        updateScoreForMostRecentLine(progress, newLine[0], indexOfMostRecentLine[0]);

        if (mListener != null) {
            mListener.requestRefreshUi();
        }
    }

    private int mContinuousZeroCount = 0;

    public void setPitch(float pitch) {
        if (mLyricsModel == null) {
            if (mListener != null) {
                mListener.resetUi();
            }
            return;
        }

        final int ZERO_PITCH_COUNT_THRESHOLD = 10;

        if (pitch == 0) {
            if (++mContinuousZeroCount < ZERO_PITCH_COUNT_THRESHOLD) {
                return;
            }
        } else {
            mContinuousZeroCount = 0;
        }

        float currentRefPitch = mRefPitchForCurrentProgress; // Not started or ended
        if (currentRefPitch <= 0 || mContinuousZeroCount >= ZERO_PITCH_COUNT_THRESHOLD) { // No ref pitch, just ignore this time
            mContinuousZeroCount = 0;
            if (mListener != null) {
                mListener.resetUi();
            }
            return;
        }

        long progress = mCurrentProgress;

        boolean betweenCurrentPitch = checkBetweenCurrentRefPitch();

        float rawPitch = pitch;
        float pitchAfterProcess = 0;

        if (mVoicePitchChanger != null) {
            // Either no valid local pitch or ref pitch, we will treat the return value as 0
            if (Config.USE_AI_ALGORITHM) {
                pitch = (float) AINative.handlePitch(currentRefPitch, pitch, this.mMaximumRefPitch);
            } else {
                pitch = (float) mVoicePitchChanger.handlePitch(currentRefPitch, pitch, this.mMaximumRefPitch);
            }
            pitchAfterProcess = pitch;
        }

        float scoreAfterNormalization = mScoringAlgo.getPitchScore(pitch, currentRefPitch);
        float score = scoreAfterNormalization * mScoringAlgo.getMaximumScoreForLine();

        mPitchesForLine.put(progress, score);
        if (DEBUG) {
            Log.d(TAG, "debugScoringAlgo/mPitchesForLine/REAL: progress=" + progress +
                    ", scoreForPitch=" + score + ", rawPitch=" + rawPitch + ", pitchAfterProcess=" + pitchAfterProcess + ", currentRefPitch=" + currentRefPitch);
        }

        if (mListener != null) {
            mListener.onPitchAndScoreUpdate(pitch, scoreAfterNormalization, betweenCurrentPitch, progress);
        }
    }

    private boolean checkBetweenCurrentRefPitch() {
        boolean betweenCurrentPitch = mStartTimeOfCurrentRefPitch > 0 && mEndTimeOfCurrentRefPitch > 0
                && mCurrentProgress >= mTimestampOfFirstRefPitch
                && mCurrentProgress <= mEndTimeOfThisLyrics
                && mCurrentProgress >= mStartTimeOfCurrentRefPitch
                && mCurrentProgress <= mEndTimeOfCurrentRefPitch;
        return betweenCurrentPitch;
    }

    public void whenDraggingHappen(long progress) {
        minorReset();

        for (int index = 0; index < mLyricsModel.lines.size(); index++) {
            LyricsLineModel line = mLyricsModel.lines.get(index);
            for (int toneIndex = 0; toneIndex < line.tones.size(); toneIndex++) {
                LyricsLineModel.Tone tone = line.tones.get(toneIndex);
                tone.resetHighlight();
            }

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

    public void setInitialScore(float initialScore) {
        this.mCumulativeScore += initialScore;
        this.mInitialScore = initialScore;
    }

    public void reset() {
        resetProperties();

        resetStats();

        if (Config.USE_AI_ALGORITHM) {
            AINative.reset();
        }
    }

    private void resetProperties() { // Reset when song changed
        mLyricsModel = null;

        mMinimumRefPitch = 100;
        mMaximumRefPitch = 0;
        mNumberOfRefPitches = 0;
        mTimestampOfFirstRefPitch = -1;

        mEndTimeOfThisLyrics = 0;

        mPerfectScore = 0;
    }

    private void resetStats() {
        minorReset();

        // Partially reset according to the corresponded action
        mCumulativeScore = mInitialScore;
        mScoreForEachLine.clear();
    }

    private void minorReset() { // Will recover immediately
        mCurrentProgress = 0;
        mIndexOfCurrentLine = -1;

        mContinuousZeroCount = 0;

        mStartTimeOfCurrentRefPitch = -1;
        mEndTimeOfCurrentRefPitch = -1;
        mRefPitchForCurrentProgress = -1f;

        mPitchesForLine.clear();

        mInHighlightingStatus = false;
    }

    public void prepareUi() {
        if (mListener != null) {
            mListener.resetUi();
        }
    }

    public LyricsModel getLyricsModel() {
        return this.mLyricsModel;
    }

    public long getCurrentProgress() {
        return this.mCurrentProgress;
    }

    public int getMinimumRefPitch() {
        return this.mMinimumRefPitch;
    }

    public int getMaximumRefPitch() {
        return this.mMaximumRefPitch;
    }

    public long getTimestampOfFirstRefPitch() {
        return this.mTimestampOfFirstRefPitch;
    }

    public double getPerfectScore() {
        return this.mPerfectScore;
    }

    public interface OnScoringListener {
        /**
         * Called automatically when the line is finished
         *
         * @param line
         * @param score
         * @param cumulativeScore
         * @param perfectScore
         * @param index
         * @param numberOfLines
         */
        public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines);

        public void resetUi();

        public void onRefPitchUpdate(float refPitch, int numberOfRefPitches, long progress);

        public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean betweenCurrentPitch, long progress);

        public void requestRefreshUi();
    }
}
