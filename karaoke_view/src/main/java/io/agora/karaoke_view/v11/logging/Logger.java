package io.agora.karaoke_view.v11.logging;

public interface Logger {
    /**
     * Dispatch log message to target handler.
     * Should not block this method
     *
     * @param level
     * @param tag
     * @param message
     */
    void onLog(int level, String tag, String message);
}
