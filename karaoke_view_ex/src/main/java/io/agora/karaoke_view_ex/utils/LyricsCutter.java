package io.agora.karaoke_view_ex.utils;

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * Utility class for cutting and trimming lyrics based on time ranges.
 * Provides functionality to extract portions of lyrics between specified start and end times.
 */
public class LyricsCutter {

    /**
     * Internal class representing a single line of lyrics with timing information
     */
    static class Line {
        private final int mBeginTime;
        private final int mDuration;

        /**
         * Constructs a new Line instance
         *
         * @param beginTime The start time of the line in milliseconds
         * @param duration  The duration of the line in milliseconds
         */
        public Line(long beginTime, long duration) {
            this.mBeginTime = (int) beginTime;
            this.mDuration = (int) duration;
        }

        public int getBeginTime() {
            return mBeginTime;
        }

        public int getDuration() {
            return mDuration;
        }

        public int getEndTime() {
            return mBeginTime + mDuration;
        }
    }

    /**
     * Adjusts the start and end times to match the nearest line boundaries
     *
     * @param startTime The requested start time in milliseconds
     * @param endTime   The requested end time in milliseconds
     * @param lines     List of lyrics lines to process
     * @return A Pair containing the adjusted start and end times, or null if invalid
     */
    private static Pair<Integer, Integer> handleFixTime(int startTime, int endTime, List<LyricsLineModel> lines) {
        if (startTime >= endTime || lines.isEmpty()) {
            return null;
        }

        long start = startTime;
        long end = endTime;

        LyricsLineModel firstLine = lines.get(0);
        LyricsLineModel lastLine = lines.get(lines.size() - 1);

        if ((start < firstLine.getStartTime() && end < firstLine.getStartTime()) ||
                (start > lastLine.getEndTime() && end > lastLine.getEndTime())) {
            return null;
        }

        // Adjust boundaries if they cross first or last line
        if (start < firstLine.getStartTime()) {
            start = firstLine.getStartTime();
        }
        if (end > lastLine.getEndTime()) {
            end = lastLine.getEndTime();
        }

        int startIndex = 0;
        long startGap = Long.MAX_VALUE;
        int endIndex = 0;
        long endGap = Long.MAX_VALUE;

        for (int i = 0; i < lines.size(); i++) {
            LyricsLineModel line = lines.get(i);
            long currentStartGap = Math.abs(line.getStartTime() - start);
            long currentEndGap = Math.abs(line.getEndTime() - end);

            if (currentStartGap < startGap) {
                startGap = currentStartGap;
                startIndex = i;
            }
            if (currentEndGap < endGap) {
                endGap = currentEndGap;
                endIndex = i;
            }
        }

        LyricsLineModel startLine = lines.get(startIndex);
        LyricsLineModel endLine = lines.get(endIndex);
        if (startLine.getStartTime() < endLine.getEndTime()) {
            return new Pair<>((int) startLine.getStartTime(), (int) endLine.getEndTime());
        }
        return null;
    }

    /**
     * Cuts the lyrics model to include only lines within the specified time range
     *
     * @param model     The lyrics model to cut
     * @param startTime The start time in milliseconds
     * @param endTime   The end time in milliseconds
     * @return The modified lyrics model containing only the lines within the specified range,
     * or the original model if cutting is not possible
     */
    public static LyricModel cut(LyricModel model, int startTime, int endTime) {
        LogUtils.d("cut LyricModel startTime: " + startTime + " endTime: " + endTime + " model: " + model);
        if (model == null || model.lines == null || model.lines.isEmpty()) {
            return model;
        }
        Pair<Integer, Integer> pair = handleFixTime(startTime, endTime, model.lines);
        if (pair == null) {
            return model;
        }
        int highStartTime = pair.first;
        int lowEndTime = pair.second;
        LogUtils.d("cut LyricModel highStartTime: " + highStartTime + " lowEndTime: " + lowEndTime);
        List<LyricsLineModel> lines = new ArrayList<>();
        boolean flag = false;

        for (LyricsLineModel line : model.lines) {
            if (line.getStartTime() == highStartTime) {
                flag = true;
            }
            if (line.getEndTime() == lowEndTime) {
                lines.add(line);
                break;
            }
            if (flag) {
                lines.add(line);
            }
        }

        model.lines = lines;
        model.preludeEndPosition = lines.isEmpty() ? 0 : lines.get(0).getStartTime();
        model.duration = lines.isEmpty() ? 0 : lines.get(lines.size() - 1).getEndTime() - lines.get(lines.size() - 1).getStartTime();

        return model;
    }
}
