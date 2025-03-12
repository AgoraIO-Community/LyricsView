package io.agora.karaoke_view_ex.downloader;

import io.agora.karaoke_view_ex.constants.DownloadError;

/**
 * Callback interface for lyrics file download operations.
 * Provides methods to track download progress and completion status.
 */
public interface LyricsFileDownloaderCallback {
    /**
     * Called when download progress is updated
     *
     * @param requestId The ID of the download request
     * @param progress  The current download progress (0.0 to 1.0)
     */
    void onLyricsFileDownloadProgress(int requestId, float progress);

    /**
     * Called when download is completed or encounters an error
     *
     * @param requestId The ID of the download request
     * @param fileData  The downloaded file data as byte array, null if download failed
     * @param error     The error that occurred during download, null if download succeeded
     */
    void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error);
}
