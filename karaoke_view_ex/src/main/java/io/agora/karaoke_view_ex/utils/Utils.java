package io.agora.karaoke_view_ex.utils;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import io.agora.karaoke_view_ex.internal.utils.LogUtils;

/**
 * Utility class providing common file operations and string manipulation methods.
 * Contains methods for reading files, deleting directories, and text processing.
 */
public class Utils {
    /**
     * Reads a file and returns its contents as a byte array
     *
     * @param path The path to the file to be read
     * @return The file contents as a byte array, or null if the file cannot be read
     */
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

    /**
     * Reads a file containing numeric values and converts them to a double array
     *
     * @param file The file to read (each line should contain a single numeric value)
     * @return An array of double values read from the file, or null if the file cannot be read
     */
    public static double[] readFileToDoubleArray(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        ArrayList<Double> doubleList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                double value = Double.parseDouble(line.trim());
                doubleList.add(value);
            }
        } catch (IOException e) {
            LogUtils.e("Error reading file: " + e.getMessage());
        } catch (NumberFormatException e) {
            LogUtils.e("Error parsing number: " + e.getMessage());
        }

        double[] doubleArray = new double[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            doubleArray[i] = doubleList.get(i);
        }

        return doubleArray;
    }

    /**
     * Recursively deletes a folder and all its contents
     *
     * @param folderPath The path to the folder to be deleted
     */
    public static void deleteFolder(String folderPath) {
        File folder = new File(folderPath);

        // Check if folder exists
        if (folder.exists()) {
            // Get all files and folders in the directory
            File[] files = folder.listFiles();

            // Delete all files and subdirectories
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        file.delete(); // Delete file directly
                    } else {
                        // Recursively delete subdirectories
                        deleteFolder(file.getAbsolutePath());
                    }
                }
            }

            folder.delete(); // Delete the empty folder
            LogUtils.d("Folder deleted successfully");
        } else {
            LogUtils.d("Folder does not exist");
        }
    }

    /**
     * Removes single and double quotes from a string
     *
     * @param content The string to process
     * @return The string with all quotes removed, or the original string if empty
     */
    public static String removeQuotes(String content) {
        if (TextUtils.isEmpty(content)) {
            return content;
        }
        return content.replaceAll("[\"']", "");
    }
}
