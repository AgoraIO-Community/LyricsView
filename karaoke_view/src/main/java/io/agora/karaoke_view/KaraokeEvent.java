package io.agora.karaoke_view;

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
}
