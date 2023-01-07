package io.agora.lyrics_view.bean;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/7/6
 */
public class LrcEntryData {

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
    }

    public static class Monolog extends Tone {
        // Better not use extend
    }

    public List<Tone> tones;

    public LrcEntryData(Tone tone) {
        this.tones = new ArrayList<>();
        this.tones.add(tone);
    }

    public LrcEntryData(Monolog tone) {
        this.tones = new ArrayList<>();
        this.tones.add(tone);
    }

    public LrcEntryData(List<Tone> tones) {
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
        if (tones == null || tones.isEmpty()) return 0;
        else return tones.get(tones.size() - 1).end;
    }
}
