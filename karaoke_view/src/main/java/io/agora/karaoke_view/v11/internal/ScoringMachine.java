package io.agora.karaoke_view.v11.internal;

import android.util.Log;

import java.util.LinkedHashMap;

import io.agora.karaoke_view.v11.DefaultScoringAlgorithm;
import io.agora.karaoke_view.v11.IScoringAlgorithm;
import io.agora.karaoke_view.v11.VoicePitchChanger;
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
    // End time of current reference line(same with last pitch/word)
    private long mEndTimeOfCurrentLine = -1;
    // End time of this lyrics
    private long mEndTimeOfThisLyrics = 0;
    // Index of current line
    private int mIndexOfCurrentLine = -1;

    // Current timestamp for this lyrics
    private long mCurrentTimestamp = 0;
    // Delta of time between updates
    private int mDeltaOfUpdate = 20;

    // Reference pitch for current timestamp
    private float mRefPitchForCurrentTimestamp = 0f;

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

    // Set data and prepare to rendering
    public void prepare(LyricsModel model) {
        if (model == null || model.lines == null || model.lines.isEmpty()) {
            throw new IllegalArgumentException("Invalid lyrics model");
        }

        reset();

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
        return mLyricsModel != null && mTimestampOfFirstRefPitch > 0 && mNumberOfRefPitches > 0;
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
     * {@link this#mEndTimeOfCurrentLine}
     *
     * @return 当前时间歌词的 Pitch 以及是否换行 returnNewLine 和 returnIndexOfLastLine
     */
    private float findRefPitchByTime(long timestamp, final boolean[] returnNewLine, final int[] returnIndexOfLastLine) {
        if (mLyricsModel == null) {
            return 0;
        }

        float referencePitch = 0;
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

                        mEndTimeOfCurrentLine = line.getEndTime();
                        if (mIndexOfCurrentLine != i && i >= 1) { // Line switch
                            returnIndexOfLastLine[0] = i - 1;
                            returnNewLine[0] = true;
                        }
                        mIndexOfCurrentLine = i;
                        break;
                    }
                }
                break;
            }
        }

        long latestEndTimeOfLastLine = mEndTimeOfCurrentLine;

        if (referencePitch == 0) { // Clear current line stats data if goes into another line
            mStartTimeOfCurrentRefPitch = -1;
            mEndTimeOfCurrentRefPitch = -1;

            if (timestamp > mEndTimeOfCurrentLine) {
                mEndTimeOfCurrentLine = -1;
            }
        } else {
            // 进入此行代码条件 ： 所唱歌词句开始时间 <= 当前时间 >= 所唱歌词句结束时间
            // 强行加上一个　0 分 ，标识此为可打分句
            // 相当于歌词当中预期有 pitch，所以需要做好占位
            mPitchesForLine.put(timestamp, 0f);
            if (DEBUG) {
                Log.d(TAG, "debugScoringAlgo/mPitchesForLine/STUB: timestamp=" + timestamp + ", referencePitch=" + referencePitch + ", scoreForPitch=" + 0d);
            }
        }
        mRefPitchForCurrentTimestamp = referencePitch;

        // Last line(No line switch any more), should do extra check
        if (timestamp > mEndTimeOfThisLyrics && latestEndTimeOfLastLine == mEndTimeOfThisLyrics) {
            returnIndexOfLastLine[0] = mIndexOfCurrentLine;
            returnNewLine[0] = true;
        }

        return referencePitch;
    }

    public static float calculateScore2(double minimumScore, int scoreLevel, int compensationOffset, double pitch, double refPitch) {
        float tone = (float) pitchToTone(pitch);
        float refTone = (float) pitchToTone(refPitch);

        float scoreAfterNormalization; // [0, 1]
        scoreAfterNormalization = (float) (1.f - (scoreLevel * Math.abs(tone - refTone)) / 100.f + compensationOffset * 1.0 / 100.f);

        // 得分线以下的分数归零
        scoreAfterNormalization = scoreAfterNormalization >= minimumScore ? scoreAfterNormalization : 0f;
        // 得分太大的置一
        scoreAfterNormalization = scoreAfterNormalization > 1 ? 1 : scoreAfterNormalization;
        return scoreAfterNormalization;
    }

    public static double pitchToTone(double pitch) {
        double eps = 1e-6;
        return (Math.max(0, Math.log(pitch / 55 + eps) / Math.log(2))) * 12;
    }

    private void updateScoreForCurrentLine(long timestamp, boolean newLine, int indexOfLineJustFinished) {
        if (timestamp < mTimestampOfFirstRefPitch) { // Not started
            return;
        }

        if (timestamp > mEndTimeOfThisLyrics + (2l * mDeltaOfUpdate)) { // After lyrics ended, do not need to update again
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "updateScoreForCurrentLine: mEndTimeOfCurrentLine=" + mEndTimeOfCurrentLine + ", timestamp=" + timestamp + ", mEndTimeOfThisLyrics=" + mEndTimeOfThisLyrics + ", numberOfPitchScores=" + mPitchesForLine.size()
                    + "\n" + newLine + "(" + indexOfLineJustFinished + ")"
                    + "\n indexOfLineJustFinished=" + indexOfLineJustFinished + ":mIndexOfCurrentLine=" + mIndexOfCurrentLine + " delta: " + mDeltaOfUpdate);
        }

        if (newLine && !mPitchesForLine.isEmpty()) {
            LyricsLineModel lineJustFinished = mLyricsModel.lines.get(indexOfLineJustFinished);
            int scoreThisTime = mScoringAlgo.calcLineScore(mPitchesForLine, indexOfLineJustFinished, lineJustFinished);

            // 统计到累计分数
            mCumulativeScore += scoreThisTime;

            if (mListener != null) {
                mListener.onLineFinished(lineJustFinished, scoreThisTime, (int) mCumulativeScore, (int) mPerfectScore, indexOfLineJustFinished, mLyricsModel.lines.size());
            }

            // Cache it for dragging
            mScoreForEachLine.put(indexOfLineJustFinished, scoreThisTime);
        }
    }

    /**
     * 更新歌词进度，单位毫秒
     * 根据当前时间，决定是否回调 {@link ScoringMachine.OnScoringListener#onRefPitchUpdate(float, int)}}
     * 并驱动打分
     *
     * @param progress 当前播放时间，毫秒
     *                 progress 一定要大于歌词结束时间才可以触发最后一句的回调
     *                 {@link ScoringMachine.OnScoringListener#onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines)}
     */
    public void setProgress(long progress) {
        if (this.mCurrentTimestamp >= 0 && progress > 0) {
            mDeltaOfUpdate = (int) (progress - this.mCurrentTimestamp);
            if (mDeltaOfUpdate > 100 || mDeltaOfUpdate < 0) {
                mDeltaOfUpdate = 20;
            }
        }
        this.mCurrentTimestamp = progress;
        if (progress == 0L) {
            resetStats();
            if (mListener != null) {
                mListener.resetUi();
            }
        }

        if (mLyricsModel == null) {
            if (mListener != null) {
                mListener.resetUi();
            }
            return;
        }

        boolean[] newLine = new boolean[1];
        int[] indexOfLastLine = new int[]{-1};
        if (progress < mStartTimeOfCurrentRefPitch || progress > mEndTimeOfCurrentRefPitch) {
            float currentRefPitch = findRefPitchByTime(progress, newLine, indexOfLastLine);
            if (currentRefPitch > 0 && mListener != null) {
                mListener.onRefPitchUpdate(currentRefPitch, mNumberOfRefPitches);
            }
        }

        updateScoreForCurrentLine(progress, newLine[0], indexOfLastLine[0]);

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

        float currentRefPitch = mRefPitchForCurrentTimestamp; // Not started or ended
        if (currentRefPitch == 0 || mContinuousZeroCount >= ZERO_PITCH_COUNT_THRESHOLD) { // No ref pitch, just ignore this time
            mContinuousZeroCount = 0;
            if (mListener != null) {
                mListener.resetUi();
            }
            return;
        }

        long timestamp = mCurrentTimestamp;

        boolean betweenCurrentPitch = checkBetweenCurrentRefPitch();

        float rawPitch = pitch;
        float pitchAfterProcess = 0;

        if (mVoicePitchChanger != null) {
            // Either no valid local pitch or ref pitch, we will treat the return value as 0
            pitch = (float) mVoicePitchChanger.handlePitch(currentRefPitch, pitch, this.mMaximumRefPitch);
            pitchAfterProcess = pitch;
        }

        float scoreAfterNormalization = mScoringAlgo.pitchToScore(pitch, currentRefPitch);
        float score = scoreAfterNormalization * mScoringAlgo.getMaximumScoreForLine();

        mPitchesForLine.put(timestamp, score);
        if (DEBUG) {
            Log.d(TAG, "debugScoringAlgo/mPitchesForLine/REAL: timestamp=" + timestamp +
                    ", scoreForPitch=" + score + ", rawPitch=" + rawPitch + ", pitchAfterProcess=" + pitchAfterProcess + ", currentRefPitch=" + currentRefPitch);
        }

        if (mListener != null) {
            mListener.onPitchAndScoreUpdate(pitch, scoreAfterNormalization, betweenCurrentPitch);
        }
    }

    private boolean checkBetweenCurrentRefPitch() {
        boolean betweenCurrentPitch = mStartTimeOfCurrentRefPitch > 0 && mEndTimeOfCurrentRefPitch > 0
                && mCurrentTimestamp >= mTimestampOfFirstRefPitch
                && mCurrentTimestamp <= mEndTimeOfThisLyrics
                && mCurrentTimestamp >= mStartTimeOfCurrentRefPitch
                && mCurrentTimestamp <= mEndTimeOfCurrentRefPitch;
        return betweenCurrentPitch;
    }

    public void whenDraggingHappen(long progress) {
        for (int lineIndex = 0; lineIndex < mLyricsModel.lines.size(); lineIndex++) {
            LyricsLineModel line = mLyricsModel.lines.get(lineIndex);
            for (int toneIndex = 0; toneIndex < line.tones.size(); toneIndex++) {
                LyricsLineModel.Tone tone = line.tones.get(toneIndex);
                tone.resetHighlight();
            }

            if (progress <= line.getEndTime()) {
                mScoreForEachLine.put(lineIndex, 0);
            }
        }
        mPitchesForLine.clear();
        mCumulativeScore = 0;

        for (Integer score : mScoreForEachLine.values()) {
            mCumulativeScore += score;
        }
    }

    public void setInitialScore(float initialScore) {
        mPerfectScore += mInitialScore;
        this.mInitialScore = initialScore;
    }

    public void reset() {
        mLyricsModel = null;

        mMinimumRefPitch = 100;
        mMaximumRefPitch = 0;
        mNumberOfRefPitches = 0;
        mTimestampOfFirstRefPitch = -1;

        mEndTimeOfThisLyrics = 0;

        mPerfectScore = 0;

        resetStats();
    }

    private void resetStats() {
        mCurrentTimestamp = 0;

        mIndexOfCurrentLine = -1;
        mStartTimeOfCurrentRefPitch = -1;
        mEndTimeOfCurrentRefPitch = -1;
        mEndTimeOfCurrentLine = -1;

        mRefPitchForCurrentTimestamp = 0f;

        mScoreForEachLine.clear();
        mPitchesForLine.clear();

        mInHighlightingStatus = false;

        mCumulativeScore = mInitialScore;

        mContinuousZeroCount = 0;
    }

    public void prepareUi() {
        if (mListener != null) {
            mListener.resetUi();
        }
    }

    public LyricsModel getLyricsModel() {
        return this.mLyricsModel;
    }

    public long getCurrentTimestamp() {
        return this.mCurrentTimestamp;
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
        public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines);

        public void resetUi();

        public void onRefPitchUpdate(float refPitch, int numberOfRefPitches);

        public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean betweenCurrentPitch);

        public void requestRefreshUi();
    }
}
