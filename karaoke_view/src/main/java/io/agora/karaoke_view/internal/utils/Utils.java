package io.agora.karaoke_view.internal.utils;

import java.io.File;
import java.io.FileInputStream;

public class Utils {
    public static byte[] getFileBytes(File file) {
        if (file == null || !file.exists()) {
            LogUtils.e("file is null or not exists");
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int size = fis.read(data);
            if (size != file.length()) {
                LogUtils.e("Content not as expected size: " + file.length() + ", actual: " + size);
                return null;
            }
            return data;
        } catch (Exception e) {
            LogUtils.e("doParse error: " + e.getMessage());
        }
        return null;
    }
}
