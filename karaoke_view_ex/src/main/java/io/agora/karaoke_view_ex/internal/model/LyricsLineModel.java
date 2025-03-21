package io.agora.karaoke_view_ex.internal.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Model class representing a single line of lyrics.
 * Contains information about individual tones (words/syllables) with their timing and pitch data.
 */
public class LyricsLineModel {

    /**
     * Enumeration for language types supported in lyrics
     */
    public enum Lang {
        Chinese, English
    }

    /**
     * Class representing a single tone (word/syllable) in a lyrics line
     */
    public static class Tone {
        /**
         * Start time of the tone in milliseconds
         */
        public long begin;

        /**
         * End time of the tone in milliseconds
         */
        public long end;

        /**
         * Text content of the tone
         */
        public String word;

        /**
         * Language of the tone
         */
        public Lang lang = Lang.Chinese;

        /**
         * Pitch value for the tone
         */
        public int pitch = 0;

        /**
         * Whether this tone represents a full line of lyrics
         */
        public boolean isFullLine = false;

        /**
         * Gets the duration of the tone in milliseconds
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
            Tone tone = (Tone) obj;
            // Compare all properties for equality
            return this.begin == tone.begin && this.end == tone.end && this.word.equals(tone.word) && this.lang == tone.lang && this.pitch == tone.pitch;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(begin) + Objects.hashCode(end) + Objects.hashCode(word) + Objects.hashCode(lang) + Objects.hashCode(pitch);
        }

        @NonNull
        @Override
        public String toString() {
            return "Tone{" +
                    "begin=" + begin +
                    ", end=" + end +
                    ", word='" + word + '\'' +
                    ", lang=" + lang +
                    ", pitch=" + pitch +
                    ", isFullLine=" + isFullLine +
                    '}';
        }
    }

    /**
     * Class representing a monologue section in lyrics
     * Note: Inheritance is not recommended for this use case
     */
    public static class Monolog extends Tone {
        // Better not use extend
    }

    /**
     * List of tones in this lyrics line
     */
    public List<Tone> tones;

    /**
     * Duration of the entire line in milliseconds
     */
    public long duration;

    /**
     * Default constructor
     * Creates an empty lyrics line with no tones
     */
    public LyricsLineModel() {
        this.tones = new ArrayList<>();
    }

    /**
     * Constructor with a single tone
     *
     * @param tone The tone to add to this line
     */
    public LyricsLineModel(Tone tone) {
        this.tones = new ArrayList<>();
        this.tones.add(tone);
    }

    /**
     * Constructor with a monologue tone
     *
     * @param tone The monologue tone to add to this line
     */
    public LyricsLineModel(Monolog tone) {
        this.tones = new ArrayList<>();
        this.tones.add(tone);
    }

    /**
     * Constructor with a list of tones
     *
     * @param tones The list of tones to add to this line
     */
    public LyricsLineModel(List<Tone> tones) {
        this.tones = tones;
    }

    /**
     * Gets the start time of the lyrics line
     *
     * @return Start time in milliseconds, or 0 if no tones exist
     */
    public long getStartTime() {
        if (tones == null || tones.size() <= 0) {
            return 0;
        }

        Tone first = tones.get(0);
        return first.begin;
    }

    /**
     * Gets the end time of the lyrics line
     *
     * @return End time in milliseconds, or 0 if no tones exist
     */
    public long getEndTime() {
        if (tones == null || tones.isEmpty()) {
            return 0;
        } else {
            return tones.get(tones.size() - 1).end;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "LyricsLineModel{" +
                "tones=" + tones +
                '}';
    }
}
