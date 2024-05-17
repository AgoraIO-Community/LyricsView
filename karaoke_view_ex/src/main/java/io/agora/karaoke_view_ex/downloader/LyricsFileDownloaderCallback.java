package io.agora.karaoke_view_ex.downloader;

import io.agora.karaoke_view_ex.constants.DownloadError;

public interface LyricsFileDownloaderCallback {
    void onLyricsFileDownloadProgress(int requestId, float progress);

    void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error);
}
