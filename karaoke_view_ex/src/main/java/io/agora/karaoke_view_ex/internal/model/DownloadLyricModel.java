package io.agora.karaoke_view_ex.internal.model;

import androidx.annotation.NonNull;

import io.agora.karaoke_view_ex.internal.net.HttpUrlRequest;

public class DownloadLyricModel {
    private int requestId;
    private String url;
    private String filePath;
    private HttpUrlRequest httpUrlRequest;

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public HttpUrlRequest getHttpUrlRequest() {
        return httpUrlRequest;
    }

    public void setHttpUrlRequest(HttpUrlRequest httpUrlRequest) {
        this.httpUrlRequest = httpUrlRequest;
    }

    @NonNull
    @Override
    public String toString() {
        return "DownloadLyricModel{" +
                "requestId=" + requestId +
                ", url='" + url + '\'' +
                ", filePath='" + filePath + '\'' +
                ", httpUrlRequest=" + httpUrlRequest +
                '}';
    }
}
