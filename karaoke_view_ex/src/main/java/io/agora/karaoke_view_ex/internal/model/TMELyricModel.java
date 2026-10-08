package io.agora.karaoke_view_ex.internal.model;

import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.model.LyricModel;

/** Internal marker: only TME file parsing opts into filtered, half-open scoring. */
public final class TMELyricModel extends LyricModel {
    public final List<ScoringLine> scoringLines = new ArrayList<>();

    public TMELyricModel() {
        super(LyricType.LRC);
        lines = new ArrayList<>();
    }

    /** Frequencies remain floating point without changing legacy Tone.pitch's ABI. */
    public static final class Note extends LyricsLineModel.Tone {
        public final float frequency;
        public final String pronounce = "";

        public Note(long begin, long end, float frequency) {
            this.begin = begin;
            this.end = end;
            this.frequency = frequency;
            pitch = (int) frequency;
            word = "";
            lang = LyricsLineModel.Lang.Unknown;
        }
    }

    public static final class Pitch extends LyricsPitchLineModel.Pitch {
        private final float frequency;

        public Pitch(Note note) {
            begin = note.begin;
            end = note.end;
            pitch = note.pitch;
            frequency = note.frequency;
        }

        @Override public float getFrequency() { return frequency; }
    }

    /** Algorithm input contains real notes; callbacks retain the original display line. */
    public static final class ScoringLine extends LyricsLineModel {
        public final LyricsLineModel displayLine;
        public final List<Note> notes;

        public ScoringLine(LyricsLineModel displayLine, List<Note> notes) {
            super(new ArrayList<Tone>(notes));
            this.displayLine = displayLine;
            this.notes = notes;
            duration = displayLine.duration;
        }

        @Override public long getStartTime() { return displayLine.getStartTime(); }
        @Override public long getEndTime() { return displayLine.getEndTime(); }
    }

    @Override public TMELyricModel copy() {
        TMELyricModel copy = new TMELyricModel();
        copy.name = name;
        copy.singer = singer;
        copy.lines = new ArrayList<>(lines);
        copy.scoringLines.addAll(scoringLines);
        copy.preludeEndPosition = preludeEndPosition;
        copy.duration = duration;
        copy.hasPitch = hasPitch;
        copy.copyrightSentenceLineCount = copyrightSentenceLineCount;
        return copy;
    }
}
