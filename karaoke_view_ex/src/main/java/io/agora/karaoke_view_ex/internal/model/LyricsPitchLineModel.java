package io.agora.karaoke_view_ex.internal.model;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Model class representing a line of lyrics with pitch data.
 * Contains information about pitch segments with their timing and highlighting information.
 */
public class LyricsPitchLineModel {
    /**
     * Class representing a single pitch segment in a lyrics line
     */
    public static class Pitch {
        /**
         * Start time of the pitch segment in milliseconds
         */
        public long begin;

        /**
         * End time of the pitch segment in milliseconds
         */
        public long end;

        /**
         * Pitch value for this segment
         */
        public int pitch = 0;

        /**
         * Map of highlighted parts within this pitch segment
         * Key: Timestamp, Value: Pair of (start time, end time) for the highlight
         */
        public Map<Long, Pair<Long, Long>> highlightPartMap = new HashMap<>();

        /**
         * Clears all highlight information from this pitch segment
         */
        public void resetHighlight() {
            this.highlightPartMap.clear();
        }

        /**
         * Gets the duration of the pitch segment in milliseconds
         *
         * @return Duration in milliseconds
         */
        public long getDuration() {
            return end - begin;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Pitch pitch = (Pitch) obj;
            // Compare all properties for equality
            if (this.begin == pitch.begin && this.end == pitch.end && this.pitch == pitch.pitch) {
                return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(begin) + Objects.hashCode(end) + Objects.hashCode(pitch);
        }

        @NonNull
        @Override
        public String toString() {
            return "Pitch{" +
                    "begin=" + begin +
                    ", end=" + end +
                    ", pitch=" + pitch +
                    ", highlightPartMap=" + highlightPartMap +
                    '}';
        }
    }

    /**
     * List of pitch segments in this lyrics line
     */
    public List<Pitch> pitches;

    /**
     * Default constructor
     * Creates an empty pitch line with no pitch segments
     */
    public LyricsPitchLineModel() {
        this.pitches = new ArrayList<>();
    }

    /**
     * Constructor with a single pitch segment
     *
     * @param pitch The pitch segment to add to this line
     */
    public LyricsPitchLineModel(Pitch pitch) {
        this.pitches = new ArrayList<>();
        this.pitches.add(pitch);
    }

    /**
     * Constructor with a list of pitch segments
     *
     * @param pitches The list of pitch segments to add to this line
     */
    public LyricsPitchLineModel(List<Pitch> pitches) {
        this.pitches = pitches;
    }

    public long getStartTime() {
        if (pitches == null || pitches.size() <= 0) {
            return 0;
        }

        Pitch first = pitches.get(0);
        return first.begin;
    }

    public long getEndTime() {
        if (pitches == null || pitches.isEmpty()) {
            return 0;
        } else {
            return pitches.get(pitches.size() - 1).end;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "LyricsPitchLineModel{" +
                "pitches=" + pitches +
                '}';
    }
}
