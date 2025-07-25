package io.agora.karaoke_view_ex.constants;

import androidx.annotation.NonNull;

/**
 * Enumeration of download error types for karaoke content.
 * Defines various error scenarios that can occur during download operations.
 */
public enum DownloadError {
    /**
     * General error during download operation
     */
    GENERAL(0),

    /**
     * Error when attempting to download an already downloading file
     */
    REPEAT_DOWNLOADING(1),

    /**
     * HTTP error during download operation
     */
    HTTP_DOWNLOAD_ERROR(2),

    /**
     * Logical error during HTTP download
     */
    HTTP_DOWNLOAD_ERROR_LOGIC(3),

    /**
     * Error during file unzip operation
     */
    UNZIP_FAIL(4);

    /**
     * Type identifier for the error
     */
    private final int mType;

    /**
     * Specific error code for detailed error information
     */
    private int mErrorCode;

    /**
     * Error message describing the error
     */
    private String mMessage;

    /**
     * Constructor for DownloadError
     *
     * @param type The type identifier for the error
     */
    DownloadError(int type) {
        this.mType = type;
    }

    /**
     * Get the error type identifier
     *
     * @return The error type value
     */
    public int getType() {
        return mType;
    }

    /**
     * Get the specific error code
     *
     * @return The error code value
     */
    public int getErrorCode() {
        return mErrorCode;
    }

    /**
     * Set the specific error code
     *
     * @param errorCode The error code to set
     */
    public void setErrorCode(int errorCode) {
        this.mErrorCode = errorCode;
    }

    /**
     * Get the error message
     *
     * @return The error message string
     */
    public String getMessage() {
        return mMessage;
    }

    /**
     * Set the error message
     *
     * @param message The message to set
     */
    public void setMessage(String message) {
        this.mMessage = message;
    }

    /**
     * Convert the error to a string representation
     *
     * @return A string containing the error name, type, code and message
     */
    @NonNull
    @Override
    public String toString() {
        return this.name() + "{" +
                "type=" + mType +
                ", errorCode=" + mErrorCode +
                ", message='" + mMessage + '\'' +
                '}';
    }
}
