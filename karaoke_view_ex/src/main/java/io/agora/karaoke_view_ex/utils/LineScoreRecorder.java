package io.agora.karaoke_view_ex.utils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * A utility class for recording and managing line-by-line scoring in karaoke performances.
 * This class keeps track of scores for individual lyrics lines and provides methods for
 * score calculation and management.
 */
public class LineScoreRecorder {
    /**
     * List of score information for each line in the lyrics
     */
    private List<LineScoreInfo> mLineScoreList;

    /**
     * Sets the lyric data for score recording
     *
     * @param model The lyric model containing line information
     */
    public void setLyricData(LyricModel model) {
        if (model == null || model.lines == null || model.lines.isEmpty()) {
            return;
        }
        mLineScoreList = new ArrayList<>(model.lines.size());

        for (int i = 0; i < model.copyrightSentenceLineCount; i++) {
            LineScoreInfo info = new LineScoreInfo(0, 0, 0);
            mLineScoreList.add(info);
        }
        LineScoreInfo info;
        for (int line = 0; line < model.lines.size(); line++) {
            LyricsLineModel lineModel = model.lines.get(line);
            info = new LineScoreInfo(lineModel.getStartTime(), lineModel.getEndTime(), 0);
            mLineScoreList.add(info);
        }

        LogUtils.i("LineScoreRecorder setLyricData: " + mLineScoreList);
    }

    /**
     * Sets the score for a specific line
     *
     * @param index The index of the line (1-based)
     * @param score The score to set for the line
     * @return The cumulative score after setting this line's score, or -1 if index is invalid
     */
    public float setLineScore(int index, float score) {
        if (index <= 0 || index > mLineScoreList.size()) {
            return -1;
        }
        LineScoreInfo info = mLineScoreList.get(index - 1);
        if (info == null) {
            LogUtils.e("setLineScore error, index: " + index);
            return calculateCumulativeScore();
        }
        info.setScore(score);
        return calculateCumulativeScore();
    }

    /**
     * Updates scores based on a seek position
     *
     * @param position The position to seek to (in milliseconds)
     * @return The cumulative score after seeking
     */
    public float seek(long position) {
        for (int index = 0; index < mLineScoreList.size(); index++) {
            LineScoreInfo info = mLineScoreList.get(index);
            if (info.getBegin() >= position) {
                info.updateScore(0);
            }
        }
        LogUtils.d("LineScoreRecorder seek: " + position + ", " + mLineScoreList);
        return calculateCumulativeScore();
    }

    /**
     * Calculates the total cumulative score across all lines
     *
     * @return The total cumulative score
     */
    private float calculateCumulativeScore() {
        float score = 0;
        for (LineScoreInfo lineScore : mLineScoreList) {
            score += lineScore.getScore();
        }
        return score;
    }

    /**
     * Inner class representing score information for a single line of lyrics
     */
    private static class LineScoreInfo {
        private final long mBegin;
        private final long mEnd;
        private final long mDuration;
        private float mScore;

        /**
         * Constructor for LineScoreInfo
         *
         * @param begin    The start time of the line
         * @param duration The duration of the line
         * @param score    The initial score for the line
         */
        public LineScoreInfo(long begin, long duration, float score) {
            this.mBegin = begin;
            this.mEnd = begin + duration;
            this.mDuration = duration;
            this.mScore = score;
        }

        public long getBegin() {
            return mBegin;
        }

        public long getEnd() {
            return mEnd;
        }

        public long getDuration() {
            return mDuration;
        }

        public float getScore() {
            return mScore;
        }

        public void setScore(float score) {
            this.mScore = score;
        }

        public void updateScore(long score) {
            this.mScore = score;
        }

        @NonNull
        @Override
        public String toString() {
            return "LineScoreInfo{" +
                    "begin=" + mBegin +
                    ", end=" + mEnd +
                    ", duration=" + mDuration +
                    ", score=" + mScore +
                    '}';
        }
    }
}