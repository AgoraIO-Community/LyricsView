package io.agora.karaoke_view.internal.model;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LyricsPitchLineModel {
    public static class Pitch {
        public long begin;
        public long end;
        public int pitch = 0;
        public Map<Long, Pair<Long, Long>> highlightPartMap = new HashMap<>();

        public void resetHighlight() {
            this.highlightPartMap.clear();
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
            Pitch pitch = (Pitch) obj;
            // 比较各个属性是否相等
            // ...
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


    public List<Pitch> pitches;
    // 总时长 (ms), krc 有值，其他格式文件解析没有赋值
    public long duartion;

    public LyricsPitchLineModel() {
        this.pitches = new ArrayList<>();
    }

    public LyricsPitchLineModel(Pitch pitch) {
        this.pitches = new ArrayList<>();
        this.pitches.add(pitch);
    }


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
