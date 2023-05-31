package io.agora.karaoke_view.v11;

import io.agora.karaoke_view.v11.model.LyricsLineModel;

public interface KaraokeEvent {
    /**
     * Called automatically when lyrics is dragged
     * <p>
     * Do not block this callback
     *
     * @param view
     * @param progress
     */
    void onDragTo(KaraokeView view, long progress);

    /**
     * Called automatically when reference pitch is encountered
     * <p>
     * Do not block this callback
     *
     * @param refPitch
     * @param numberOfRefPitches
     */
    void onRefPitchUpdate(float refPitch, int numberOfRefPitches);

    /**
     * Called automatically when the line is finished
     * <p>
     * Do not block this callback
     *
     * @param view
     * @param line
     * @param score
     * @param cumulativeScore
     * @param index
     * @param numberOfLines
     */
    void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulativeScore, int index, int numberOfLines);
}
