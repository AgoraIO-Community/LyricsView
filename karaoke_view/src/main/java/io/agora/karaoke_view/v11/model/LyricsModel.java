package io.agora.karaoke_view.v11.model;

import java.util.List;

public class LyricsModel {
    public static enum Type {
        General, Migu;
    }

    public Type type;
    public List<LyricsLineModel> lines;

    public LyricsModel(Type type) {
        this.type = type;
    }
}
