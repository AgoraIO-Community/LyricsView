package io.agora.karaoke_view.v11.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

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
        public boolean highlight;

        public float highlightOffset = -1;
        public float highlightWidth = -1;

        public void resetHighlight() {
            this.highlight = false;
            this.highlightOffset = -1;
            this.highlightWidth = -1;
        }

        public long getDuration() {
            return end - begin;
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
                    ", highlight=" + highlight +
                    ", highlightOffset=" + highlightOffset +
                    ", highlightWidth=" + highlightWidth +
                    '}';
        }
    }

    public static class Monolog extends Tone {
        // Better not use extend
    }

    public List<Tone> tones;

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
