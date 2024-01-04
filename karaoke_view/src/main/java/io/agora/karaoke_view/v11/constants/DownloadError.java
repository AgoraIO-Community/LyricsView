package io.agora.karaoke_view.v11.constants;

public enum DownloadError {
    GENERAL(0),
    REPEAT_DOWNLOADING(1),
    HTTP_DOWNLOAD_ERROR(2),
    HTTP_DOWNLOAD_ERROR_LOGIC(3),
    UNZIP_FAIL(4);

    private int type;
    private int errorCode;
    private String message;

    DownloadError(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return this.name() + "{" +
                "type=" + type +
                ", errorCode=" + errorCode +
                ", message='" + message + '\'' +
                '}';
    }
}
