package io.agora.lyrics_view.v11.utils;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import io.agora.lyrics_view.v11.model.LyricsModel;

/**
 * 加载歌词
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/7/6
 */
public class LyricsParser {

    @Nullable
    public static LyricsModel parse(File lrcFile) {
        return parse(null, lrcFile);
    }

    @Nullable
    public static LyricsModel parse(LyricsModel.Type type, File lrcFile) {
        if (type == null) {
            InputStream instream = null;
            InputStreamReader inputreader = null;
            BufferedReader buffreader = null;
            try {
                instream = new FileInputStream(lrcFile);
                inputreader = new InputStreamReader(instream);
                buffreader = new BufferedReader(inputreader);
                String line = buffreader.readLine();
                if (line.contains("xml") || line.contains("<song>")) {
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
                    if (instream != null) {
                        instream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    if (inputreader != null) {
                        inputreader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    if (buffreader != null) {
                        buffreader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (type == LyricsModel.Type.General) {
            return LyricsParserGeneral.parseLrc(lrcFile);
        } else if (type == LyricsModel.Type.Migu) {
            return LyricsParserMigu.parseLrc(lrcFile);
        } else {
            return null;
        }
    }
}
