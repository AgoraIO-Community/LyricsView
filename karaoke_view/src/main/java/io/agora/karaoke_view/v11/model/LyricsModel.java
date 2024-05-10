package io.agora.karaoke_view.v11.model;

import java.util.List;

public class LyricsModel {
    public static enum Type {
        General, Xml, KRC;
    }

    public Type type;
    public List<LyricsLineModel> lines;

    public long duration; // milliseconds

    /**
     * Also known as end of intro
     */
    public long startOfVerse; // milliseconds

    /**
     * <a href="https://en.wikipedia.org/wiki/ID3#ID3v2">ID3 title</a>
     */
    public String title;

    /**
     * <a href="https://en.wikipedia.org/wiki/ID3#ID3v2">ID3 artist</a>
     */
    public String artist;

    public List<KrcPitchData> pitchDatas;

    public boolean hasPitch;

    public LyricsModel(Type type) {
        this.type = type;
    }
}
