package io.agora.karaoke_view_ex.utils;

import android.text.TextUtils;

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

    public static void deleteFolder(String folderPath) {
        File folder = new File(folderPath);

        // 如果文件夹存在
        if (folder.exists()) {
            File[] files = folder.listFiles(); // 获取文件夹下的所有文件和文件夹

            // 删除文件夹下所有的文件和文件夹
            for (File file : files) {
                if (file.isFile()) {
                    file.delete(); // 如果是文件，直接删除
                } else {
                    deleteFolder(file.getAbsolutePath()); // 如果是文件夹，递归调用删除文件夹方法
                }
            }

            folder.delete(); // 删除文件夹本身
            System.out.println("文件夹删除成功！");
        } else {
            System.out.println("文件夹不存在！");
        }
    }

    public static String removeQuotes(String content) {
        if (TextUtils.isEmpty(content)) {
            return content;
        }
        return content.replaceAll("[\"']", "");
    }
}
