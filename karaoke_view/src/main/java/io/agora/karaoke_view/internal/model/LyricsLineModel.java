package io.agora.karaoke_view.internal.model;

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
            // ...
            if (this.begin == tone.begin && this.end == tone.end && this.word.equals(tone.word) && this.lang == tone.lang && this.pitch == tone.pitch) {
                return true;
            }
            return false;
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
    // 总时长 (ms), krc 有值，其他格式文件解析没有赋值
    public long duartion;

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
