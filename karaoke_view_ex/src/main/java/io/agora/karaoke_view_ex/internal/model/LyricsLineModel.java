package io.agora.karaoke_view_ex.internal.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LyricsLineModel {

    public enum Lang {
        Chinese, English
    }

    public static class Tone {
        public long begin;
        public long end;
        public String word;
        public Lang lang = Lang.Chinese;
        public int pitch = 0;
        public boolean isFullLine = false;

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
            // 比较各个属性是否相等
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

    public static class Monolog extends Tone {
        // Better not use extend
    }

    public List<Tone> tones;
    public long duration;

    public LyricsLineModel() {
        this.tones = new ArrayList<>();
    }

    public LyricsLineModel(Tone tone) {
        this.tones = new ArrayList<>();
        this.tones.add(tone);
    }

    public LyricsLineModel(Monolog tone) {
        this.tones = new ArrayList<>();
        this.tones.add(tone);
    }

    public LyricsLineModel(List<Tone> tones) {
        this.tones = tones;
    }

    public long getStartTime() {
        if (tones == null || tones.size() <= 0) {
            return 0;
        }

        Tone first = tones.get(0);
        return first.begin;
    }

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
