package io.agora.karaoke_view_ex.internal.utils;

import java.io.File;
import java.io.FileInputStream;

/**
 * Utility class providing common file and string operations
 */
public class Utils {
    /**
     * Reads a file and returns its contents as a byte array
     *
     * @param file The file to read
     * @return Byte array containing file contents, or null if reading fails
     */
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

    /**
     * Removes the Byte Order Mark (BOM) from the beginning of a string if present
     *
     * @param input The input string that may contain a BOM
     * @return String with BOM removed if it was present, otherwise the original string
     */
    public static String removeStringBom(String input) {
        // Check if the string starts with a BOM character
        if (input != null && !input.isEmpty() && input.charAt(0) == '\uFEFF') {
            // Remove BOM by taking substring starting from the second character
            return input.substring(1);
        }
        return input;
    }
}
