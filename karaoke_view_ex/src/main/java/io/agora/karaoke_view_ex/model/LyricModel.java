package io.agora.karaoke_view_ex.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.model.PitchData;

/**
 * Model class representing lyrics data for a song.
 * Contains song information, lyrics lines, and pitch data for karaoke functionality.
 */
public class LyricModel {
    /**
     * Type of lyrics (e.g., LRC, XML, KRC)
     */
    public LyricType type;

    /**
     * Name of the song
     */
    public String name;

    /**
     * Name of the singer/artist
     */
    public String singer;

    /**
     * List of lyrics lines containing timing and text information
     */
    public List<LyricsLineModel> lines;

    /**
     * End time of the song's prelude in milliseconds
     */
    public long preludeEndPosition;

    /**
     * Total duration of the lyrics in milliseconds
     */
    public long duration;

    /**
     * Whether the lyrics contain pitch information
     */
    public boolean hasPitch;

    /**
     * Number of lines containing copyright information
     */
    public int copyrightSentenceLineCount;

    /**
     * List of pitch data points for karaoke scoring
     */
    public List<PitchData> pitchDataList;

    /**
     * Constructor for LyricModel
     *
     * @param type Type of lyrics to be used
     */
    public LyricModel(LyricType type) {
        this.type = type;
        copyrightSentenceLineCount = 0;
    }

    /**
     * Create a deep copy of the LyricModel
     *
     * @return New instance of LyricModel with copied data
     */
    public LyricModel copy() {
        LyricModel lyricModel = new LyricModel(type);
        lyricModel.name = name;
        lyricModel.singer = singer;
        if (null != lines) {
            lyricModel.lines = new ArrayList<>(lines);
        }
        lyricModel.preludeEndPosition = preludeEndPosition;
        lyricModel.duration = duration;
        lyricModel.hasPitch = hasPitch;
        if (null != pitchDataList) {
            lyricModel.pitchDataList = new ArrayList<>(pitchDataList);
        }
        return lyricModel;
    }

    /**
     * Convert the LyricModel to a string representation
     *
     * @return String containing all model properties
     */
    @NonNull
    @Override
    public String toString() {
        return "LyricModel{" +
                "name='" + name + '\'' +
                ", singer='" + singer + '\'' +
                ", type=" + type +
                ", lines=" + lines +
                ", preludeEndPosition=" + preludeEndPosition +
                ", duration=" + duration +
                ", hasPitch=" + hasPitch +
                ", pitchDataList=" + pitchDataList +
                '}';
    }
}
