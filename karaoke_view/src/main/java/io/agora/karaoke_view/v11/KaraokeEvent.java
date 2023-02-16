package io.agora.karaoke_view.v11;

import io.agora.karaoke_view.v11.model.LyricsLineModel;

public interface KaraokeEvent {
    void onDragTo(KaraokeView view, long position);

    void onRefPitchUpdate(float refPitch, int numberOfRefPitches);

    void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulativeScore, int index, int total);
}
