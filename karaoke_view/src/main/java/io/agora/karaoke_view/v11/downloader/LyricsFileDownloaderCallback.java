package io.agora.karaoke_view.v11.downloader;

import io.agora.karaoke_view.v11.constants.DownloadError;

public interface LyricsFileDownloaderCallback {
    void onLyricsFileDownloadProgress(int requestId, float progress);

    void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error);
}
