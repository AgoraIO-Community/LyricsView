package io.agora.lyrics_view.logging;

public interface Logger {

    void addHandler(Handler handler);

    /**
     * Dispatch log message to target handler.
     * Should not block this method
     *
     * @param level
     * @param tag
     * @param message
     */
    void log(int level, String tag, String message);
}

