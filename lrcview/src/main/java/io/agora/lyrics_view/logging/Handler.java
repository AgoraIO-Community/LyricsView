package io.agora.lyrics_view.logging;

public interface Handler {
    void onWarn(String tag, String message);

    void onInfo(String tag, String message);

    void onDebug(String tag, String message);

    void onError(String tag, String message);
}

