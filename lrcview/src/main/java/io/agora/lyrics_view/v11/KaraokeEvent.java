package io.agora.lyrics_view.v11;

import io.agora.lyrics_view.v11.model.LyricsLineModel;

public interface KaraokeEvent {
    public void onDragTo(KaraokeView view, long position);

    public void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int index, int total);
}
