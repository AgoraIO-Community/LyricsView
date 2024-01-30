package io.agora.karaoke_view.v11.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Utils {
    public static byte[] readFileToByteArray(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            long inSize = in.getChannel().size();
            if (inSize == 0) {
                return null;
            }

            byte[] buffer = new byte[in.available()];
            in.read(buffer);
            return buffer;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (null != in) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
