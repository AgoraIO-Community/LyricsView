package io.agora.karaoke_view_ex;

import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;

public interface KaraokeEvent {
    /**
     * Called automatically when lyrics is dragged
     * <p>
     * Do not block this callback
     *
     * @param view     KaraokeView
     * @param progress progress
     */
    void onDragTo(KaraokeView view, long progress);

    /**
     * Accept a lyrics drag before the component changes its lyric/scoring position.
     * Return false when the media player rejects the seek. Existing listeners retain
     * their onDragTo behavior and accept the seek by default.
     */
    default boolean onSeekRequested(KaraokeView view, long progress) {
        onDragTo(view, progress);
        return true;
    }


    /**
     * Called automatically when the line is finished
     * <p>
     * Do not block this callback
     *
     * @param view            KaraokeView
     * @param line            LyricsLineModel
     * @param score           score
     * @param cumulativeScore cumulativeScore
     * @param index           index
     * @param lineCount       lineCount
     */
    void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount);
}
