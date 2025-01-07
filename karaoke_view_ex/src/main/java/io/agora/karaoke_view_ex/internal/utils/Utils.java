package io.agora.karaoke_view_ex.internal.utils;

import java.io.File;
import java.io.FileInputStream;

public class Utils {
    public static byte[] getFileBytes(File file) {
        if (file == null) {
            return null;
        }

        if (!file.exists()) {
            LogUtils.e("file(" + file.getAbsolutePath() + ") is null or not exists");
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

    public static String removeStringBom(String input) {
        // 检查字符串是否以BOM开始
        if (input != null && !input.isEmpty() && input.charAt(0) == '\uFEFF') {
            // 去除BOM，从第二个字符开始取
            return input.substring(1);
        }
        return input;
    }
}
