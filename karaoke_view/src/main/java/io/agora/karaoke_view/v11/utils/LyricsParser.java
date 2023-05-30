package io.agora.karaoke_view.v11.utils;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import io.agora.karaoke_view.v11.logging.LogManager;
import io.agora.karaoke_view.v11.model.LyricsModel;

/**
 * 加载歌词
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/7/6
 */
public class LyricsParser {
    private static final String TAG = "LyricsParser";

    private static void checkParameters(File file) {
        if (file == null || !file.isFile() || !file.exists() || !file.canRead() || file.length() == 0) {
            StringBuilder builder = new StringBuilder("Not a valid file for parser: " + file);
            if (file != null) {
                builder.append("\n");
                builder.append("{isFile: " + file.isFile() + ", ");
                builder.append("exists: " + file.exists() + ", ");
                builder.append("canRead: " + file.canRead() + ", ");
                builder.append("length: " + file.length() + "}");
            }
            throw new IllegalArgumentException(builder.toString());
        }
    }

    @Nullable
    public static LyricsModel parse(@NonNull File file) {
        checkParameters(file);
        return doParse(null, file);
    }

    @Nullable
    public static LyricsModel parse(@NonNull File file, @Nullable File pitch) {
        checkParameters(file);
        return doParse(null, file);
    }

    @Nullable
    public static LyricsModel parse(@NonNull LyricsModel.Type type, @NonNull File file) {
        checkParameters(file);
        return doParse(type, file);
    }

    @Nullable
    private static LyricsModel doParse(LyricsModel.Type type, File file) {
        if (type == null) {
            InputStream inputStream = null;
            InputStreamReader inputStreamReader = null;
            BufferedReader bufferedReader = null;
            try {
                inputStream = new FileInputStream(file);
                inputStreamReader = new InputStreamReader(inputStream);
                bufferedReader = new BufferedReader(inputStreamReader);
                String line = bufferedReader.readLine();
                if (line != null && (line.contains("xml") || line.contains("<song>"))) {
                    type = LyricsModel.Type.Migu;
                } else {
                    type = LyricsModel.Type.General;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    if (inputStreamReader != null) {
                        inputStreamReader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    if (bufferedReader != null) {
                        bufferedReader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (type == LyricsModel.Type.General) {
            return LyricsParserGeneral.parseLrc(file);
        } else if (type == LyricsModel.Type.Migu) {
            return LyricsParserMigu.parseLrc(file);
        } else {
            LogManager.instance().error(TAG, "Do not support the lyrics file type " + type);
            return null;
        }
    }
}
