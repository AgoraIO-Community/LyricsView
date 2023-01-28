package io.agora.karaoke_view.v11;

import io.agora.karaoke_view.v11.model.LyricsLineModel;

public interface KaraokeEvent {
    public void onDragTo(KaraokeView view, long position);

    public void onRefPitchUpdate(float refPitch, int numberOfRefPitches);

    public void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulativeScore, int index, int total);
}
