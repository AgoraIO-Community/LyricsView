package io.agora.karaoke_view_ex.internal.model;

import androidx.annotation.NonNull;

import io.agora.karaoke_view_ex.internal.net.HttpUrlRequest;

/**
 * Model class representing a lyric download request.
 * Contains information about the download request, including URL, file path, and HTTP request object.
 */
public class DownloadLyricModel {
    private int mRequestId;
    private String mUrl;
    private String mFilePath;
    private HttpUrlRequest mHttpUrlRequest;

    /**
     * Gets the request identifier
     *
     * @return The request ID
     */
    public int getRequestId() {
        return mRequestId;
    }

    /**
     * Sets the request identifier
     *
     * @param requestId The request ID to set
     */
    public void setRequestId(int requestId) {
        this.mRequestId = requestId;
    }

    /**
     * Gets the URL of the lyrics file
     *
     * @return The URL string
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Sets the URL of the lyrics file
     *
     * @param url The URL to set
     */
    public void setUrl(String url) {
        this.mUrl = url;
    }

    /**
     * Gets the local file path for saving the lyrics
     *
     * @return The file path string
     */
    public String getFilePath() {
        return mFilePath;
    }

    /**
     * Sets the local file path for saving the lyrics
     *
     * @param filePath The file path to set
     */
    public void setFilePath(String filePath) {
        this.mFilePath = filePath;
    }

    /**
     * Gets the HTTP request object
     *
     * @return The HTTP request object
     */
    public HttpUrlRequest getHttpUrlRequest() {
        return mHttpUrlRequest;
    }

    /**
     * Sets the HTTP request object
     *
     * @param httpUrlRequest The HTTP request object to set
     */
    public void setHttpUrlRequest(HttpUrlRequest httpUrlRequest) {
        this.mHttpUrlRequest = httpUrlRequest;
    }

    /**
     * Returns a string representation of the download model
     *
     * @return String containing all model properties
     */
    @NonNull
    @Override
    public String toString() {
        return "DownloadLyricModel{" +
                "requestId=" + mRequestId +
                ", url='" + mUrl + '\'' +
                ", filePath='" + mFilePath + '\'' +
                ", httpUrlRequest=" + mHttpUrlRequest +
                '}';
    }
}
