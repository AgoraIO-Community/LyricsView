package io.agora.lyrics_view.v11.internal;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import io.agora.lyrics_view.v11.VoicePitchChanger;
import io.agora.lyrics_view.v11.model.LyricsLineModel;
import io.agora.lyrics_view.v11.model.LyricsModel;

/**
 * State/Information of playing/rendering/on-going lyrics
 * <p>
 * Non-ui related, shared by all components
 */
public class ScoringMachine {
    private static final String TAG = "ScoringMachine";

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
    private int mScoringFiredIndexOfLine = -1; // Indicating that scoring just fired

    // Current timestamp for this lyrics
    private long mCurrentTimestamp = 0;
    // Delta of time between updates
    private int mDeltaOfUpdate = 20;

    // Reference pitch for current timestamp
    private float mRefPitchForCurrentTimestamp = 0f;

    // Pitches for every line, we will reset every new line
    public final LinkedHashMap<Long, Double> mPitchesForLine = new LinkedHashMap<>();

    // In highlighting status, always with some shiny animations
    private boolean mInHighlightingStatus;

    // Initial score for the lyrics(can change by app)
    private float mInitialScore;

    // Minimum score for each tone/pitch
    private float mMinimumScorePerTone;

    // Cumulative score
    private double mCumulativeScore;

    // Maximum score for one line, 100 for maximum and 0 for minimum
    private int mMaximumScoreForLine = 100;

    // Full marks/perfect for the lyrics
    private double mPerfectScore;

    // Indicating the difficulty in scoring(can change by app)
    private float mScoreLevel = 10; // 0~100
    private float mCompensationOffset = 0; // -100~100

    private VoicePitchChanger mVoicePitchChanger;

    private OnScoringListener mListener;

    public ScoringMachine(VoicePitchChanger changer, OnScoringListener listener) {
        reset();
        this.mVoicePitchChanger = changer;
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
        mPerfectScore = mMaximumScoreForLine * model.lines.size() + mInitialScore;

        for (LyricsLineModel line : model.lines) {
            for (LyricsLineModel.Tone tone : line.tones) {
                mMinimumRefPitch = Math.min(mMinimumRefPitch, tone.pitch);
                mMaximumRefPitch = Math.max(mMaximumRefPitch, tone.pitch);
                mNumberOfRefPitches++;
            }
        }

        List<LyricsLineModel.Tone> tone = model.lines.get(0).tones;
        if (tone != null && !tone.isEmpty()) {
            mTimestampOfFirstRefPitch = tone.get(0).begin; // find the timestamp of first reference pitch
        }
    }

    public boolean isReady() {
        return mLyricsModel != null && mTimestampOfFirstRefPitch > 0 && mNumberOfRefPitches > 0;
    }

    /**
     * 根据当前播放时间获取 Pitch，并且更新当前 Pitch 相关数据
     * <p>
     * {@link this#mStartTimeOfCurrentRefPitch}
     * {@link this#mEndTimeOfCurrentRefPitch}
     * {@link this#mEndTimeOfCurrentLine}
     *
     * @return 当前时间歌词的 Pitch
     */
    private float findRefPitchByTime(long timestamp) {
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
                        mIndexOfCurrentLine = i;
                        break;
                    }
                }
                break;
            }
        }

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
            mPitchesForLine.put(timestamp, 0d);
        }
        mRefPitchForCurrentTimestamp = referencePitch;
        return referencePitch;
    }

    private double calculateScore2(double minimumScore, double pitch, double refPitch) {
        double tone = pitchToTone(pitch);
        double refTone = pitchToTone(refPitch);

        double scoreAfterNormalization; // [0, 1]

        double score = 1 - (mScoreLevel * Math.abs(tone - refTone)) / 100 + mCompensationOffset / 100;

        // 得分线以下的分数归零
        score = score >= minimumScore ? score : 0f;
        // 得分太大的置一
        score = score > 1 ? 1 : score;

        scoreAfterNormalization = score;
        // 百分制分数 * 每句固定分数
        score *= mMaximumScoreForLine;
        mPitchesForLine.put(mCurrentTimestamp, score);

        return scoreAfterNormalization;
    }

    public static double pitchToTone(double pitch) {
        double eps = 1e-6;
        return (Math.max(0, Math.log(pitch / 55 + eps) / Math.log(2))) * 12;
    }

    private void updateScoreForCurrentLine(long timestamp) {
        if (timestamp < mTimestampOfFirstRefPitch) { // Not started
            return;
        }

        //  没有开始 || 在空档期
        boolean notStarted = mEndTimeOfCurrentLine == -1;
        // Let it more looser, and we do not fire the duplicated callback later
        boolean isThisLineJustFinished = ((timestamp >= mEndTimeOfCurrentLine) || (mEndTimeOfCurrentLine - timestamp) > 0 && (mEndTimeOfCurrentLine - timestamp) < mDeltaOfUpdate) && mEndTimeOfCurrentLine > 0;
        // 当前时间 >= 歌词结束时间
        boolean isThisSongFinished = timestamp >= mEndTimeOfThisLyrics;
        boolean scoringNotFiredBefore = (mScoringFiredIndexOfLine != mIndexOfCurrentLine);

        if (scoringNotFiredBefore && (notStarted || isThisLineJustFinished || isThisSongFinished)) {
            if (!mPitchesForLine.isEmpty()) {
                mScoringFiredIndexOfLine = mIndexOfCurrentLine;

                // 计算歌词当前句的分数 = 所有打分/分数个数
                double tempTotalScore = 0;
                int scoreCount = 0;

                Double tempScore;
                // 两种情况 1. 到了空档期 2. 到了下一句
                Iterator<Long> iterator = mPitchesForLine.keySet().iterator();
                int continuousZeroCount = 0;

                while (iterator.hasNext()) {
                    Long myKeyTimestamp = iterator.next();
                    if (notStarted || myKeyTimestamp <= mEndTimeOfCurrentLine) {
                        tempScore = mPitchesForLine.get(myKeyTimestamp);
                        if (tempScore == null || tempScore.floatValue() == 0.f) {
                            continuousZeroCount++;
                            if (continuousZeroCount < 8) {
                                tempScore = null; // Ignore it when not enough continuous zeros
                            } else {
                                continuousZeroCount = 0; // re-count it when reach 8 continuous zeros
                                if (mListener != null) {
                                    mListener.resetUi();
                                }
                            }
                        } else {
                            continuousZeroCount = 0;
                        }
                        iterator.remove();
                        mPitchesForLine.remove(myKeyTimestamp);
                        if (tempScore != null && tempScore.floatValue() > 0) {
                            tempTotalScore += tempScore.floatValue();
                            scoreCount++;
                        }
                    }
                }

                scoreCount = Math.max(1, scoreCount);

                double scoreThisTime = tempTotalScore / scoreCount;

                // 统计到累计分数
                mCumulativeScore += scoreThisTime;

                if (mListener != null) {
                    mListener.onLineFinished(mLyricsModel.lines.get(mIndexOfCurrentLine), scoreThisTime, mCumulativeScore, mPerfectScore, mIndexOfCurrentLine, mLyricsModel.lines.size());
                }
            }
        }
    }

    /**
     * 更新进度，单位毫秒
     * 根据当前时间，决定是否回调 {@link ScoringMachine.OnScoringListener#onRefPitch(float, int)}}
     * 与打分逻辑无关
     *
     * @param progress 当前播放时间，毫秒
     */
    public void setProgress(long progress) {
        if (mLyricsModel == null) {
            return;
        }

        if (this.mCurrentTimestamp != 0 && Math.abs(progress - this.mCurrentTimestamp) >= 500) { // Workaround(We assume this as dragging happened)
            for (int lineIndex = 0; lineIndex < mLyricsModel.lines.size(); lineIndex++) {
                LyricsLineModel line = mLyricsModel.lines.get(lineIndex);
                for (int toneIndex = 0; toneIndex < line.tones.size(); toneIndex++) {
                    LyricsLineModel.Tone tone = line.tones.get(toneIndex);
                    tone.resetHighlight();
                }
            }
        }

        if (this.mCurrentTimestamp >= 0 && progress > 0) {
            mDeltaOfUpdate = (int) (progress - this.mCurrentTimestamp);
            if (mDeltaOfUpdate > 100 || mDeltaOfUpdate < 0) {
                mDeltaOfUpdate = 20;
            }
        }
        this.mCurrentTimestamp = progress;
        if (progress == 0L) {
            resetStats();
        }

        if (progress < mStartTimeOfCurrentRefPitch || progress > mEndTimeOfCurrentRefPitch) {
            float currentRefPitch = findRefPitchByTime(progress);
            if (currentRefPitch > 0 && mListener != null) {
                mListener.onRefPitchUpdate(currentRefPitch, mNumberOfRefPitches);
            }
        }

        updateScoreForCurrentLine(progress);

        if (mListener != null) {
            mListener.requestRefreshUi();
        }
    }

    public void setPitch(float pitch) {
        if (mLyricsModel == null) {
            return;
        }

        float currentRefPitch = mRefPitchForCurrentTimestamp;

        if (mVoicePitchChanger != null) {
            // Either no valid local pitch or ref pitch, we will treat the return value as 0
            pitch = (float) mVoicePitchChanger.handlePitch(currentRefPitch, pitch, this.mMaximumRefPitch);
        }

        final double scoreAfterNormalization = this.calculateScore2(mMinimumScorePerTone, pitch, currentRefPitch);

        if (mListener != null) {
            mListener.onPitchAndScoreUpdate(pitch, scoreAfterNormalization);
        }
    }

    public void setInitialScore(float initialScore) {
        this.mInitialScore = initialScore;
    }

    public void setMinimumScorePerTone(float minimumScore) {
        this.mMinimumScorePerTone = minimumScore;
    }

    public void reset() {
        mLyricsModel = null;

        mMinimumRefPitch = 100;
        mMaximumRefPitch = 0;
        mNumberOfRefPitches = 0;
        mTimestampOfFirstRefPitch = -1;

        mEndTimeOfThisLyrics = 0;

        mMaximumScoreForLine = 100;

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

        mPitchesForLine.clear();

        mInHighlightingStatus = false;

        mCumulativeScore = mInitialScore;
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

    public float getRefPitchForCurrentTimestamp() {
        return this.mRefPitchForCurrentTimestamp;
    }

    public interface OnScoringListener {
        public void onLineFinished(LyricsLineModel line, double score, double cumulativeScore, double perfectScore, int index, int numberOfLines);

        public void resetUi();

        public void onRefPitchUpdate(float refPitch, int numberOfRefPitches);

        public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization);

        public void requestRefreshUi();
    }
}
