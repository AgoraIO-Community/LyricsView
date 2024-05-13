package io.agora.karaoke_view.downloader;

import io.agora.karaoke_view.constants.DownloadError;

public interface LyricsFileDownloaderCallback {
    void onLyricsFileDownloadProgress(int requestId, float progress);

    void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error);
}
