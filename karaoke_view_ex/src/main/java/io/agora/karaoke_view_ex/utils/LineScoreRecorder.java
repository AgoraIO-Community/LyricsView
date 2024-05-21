package io.agora.karaoke_view_ex.utils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

public class LineScoreRecorder {
    private List<LineScoreInfo> lineScoreList;

    public void setLyricData(LyricModel model) {
        if (model == null || model.lines == null || model.lines.isEmpty()) {
            return;
        }
        lineScoreList = new ArrayList<>(model.lines.size());

        for (int i = 0; i < model.copyrightSentenceLineCount; i++) {
            LineScoreInfo info = new LineScoreInfo(0, 0, 0);
            lineScoreList.add(info);
        }
        LineScoreInfo info;
        for (int line = 0; line < model.lines.size(); line++) {
            LyricsLineModel lineModel = model.lines.get(line);
            info = new LineScoreInfo(lineModel.getStartTime(), lineModel.getEndTime(), 0);
            lineScoreList.add(info);
        }

        LogUtils.i("LineScoreRecorder setLyricData: " + lineScoreList);
    }

    public float setLineScore(int index, float score) {
        if (index <= 0 || index > lineScoreList.size()) {
            return -1;
        }
        LineScoreInfo info = lineScoreList.get(index - 1);
        if (info == null) {
            LogUtils.e("setLineScore error, index: " + index);
            return calculateCumulativeScore();
        }
        info.setScore(score);
        return calculateCumulativeScore();
    }

    public float seek(long position) {
        for (int index = 0; index < lineScoreList.size(); index++) {
            LineScoreInfo info = lineScoreList.get(index);
            if (info.getBegin() >= position) {
                info.updateScore(0);
            }
        }
        LogUtils.d("LineScoreRecorder seek: " + position + ", " + lineScoreList);
        return calculateCumulativeScore();
    }

    private float calculateCumulativeScore() {
        float score = 0;
        for (LineScoreInfo lineScore : lineScoreList) {
            score += lineScore.getScore();
        }
        return score;
    }

    private static class LineScoreInfo {
        private final long begin;
        private final long end;
        private final long duration;
        private float score;

        public LineScoreInfo(long begin, long duration, float score) {
            this.begin = begin;
            this.end = begin + duration;
            this.duration = duration;
            this.score = score;
        }

        public long getBegin() {
            return begin;
        }

        public long getEnd() {
            return end;
        }

        public long getDuration() {
            return duration;
        }

        public float getScore() {
            return score;
        }

        public void setScore(float score) {
            this.score = score;
        }

        public void updateScore(long score) {
            this.score = score;
        }

        @NonNull
        @Override
        public String toString() {
            return "LineScoreInfo{" +
                    "begin=" + begin +
                    ", end=" + end +
                    ", duration=" + duration +
                    ", score=" + score +
                    '}';
        }
    }
}