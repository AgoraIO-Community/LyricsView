package io.agora.karaoke_view.v11.constants;

public class Constants {
    public static final String TAG = "Karaoke";
    public static final String LOG_FILE_NAME = "agora.AgoraLyricsScore";
    //seconds
    public static final int HTTP_TIMEOUT = 60;
    public static final String UTF_8 = "UTF-8";
    public static final String LYRICS_FILE_DOWNLOAD_DIR = "lyrics";
    public static final String LYRICS_FILE_TEMP_DIR = LYRICS_FILE_DOWNLOAD_DIR + "/temp";

    public static final String FILE_EXTENSION_ZIP = "zip";
    public static final String FILE_EXTENSION_XML = "xml";
    public static final String FILE_EXTENSION_LRC = "lrc";


    public static final int ERROR_NONE = 0;
    public static final int ERROR_GENERAL = -1;
    public static final int ERROR_UNZIP_ERROR = -4;
    public static final int ERROR_HTTP_TIMEOUT = -1001;
    public static final int ERROR_HTTP_UNKNOWN_HOST = -1003;
    public static final int ERROR_HTTP_NOT_CONNECT = -1004;
}
