package io.agora.examples.utils;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ResourceHelper {

    @Nullable
    public static File copyAssetsToCreateNewFile(@NonNull Context context, @NonNull String name) {
        final AssetManager assets = context.getAssets();

        if (assets == null) {
            return null;
        }

        File target = new File(context.getCacheDir(), name);
        if (target.exists() && target.isFile()) {
            target.delete();
        }

        try {
            InputStream input = null;
            OutputStream output = null;
            try {
                output = new FileOutputStream(target);
                input = assets.open(name);
                copyFile(input, output);
                output.flush();
            } finally {
                if (input != null) {
                    input.close();
                }
                if (output != null) {
                    output.close();
                }
            }
        } catch (IOException e) {
            return null;
        }

        return target;
    }

    static private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    @Nullable
    public static String loadAsString(@NonNull Context context, @NonNull String name) {
        final AssetManager assets = context.getAssets();

        if (assets == null) {
            return null;
        }

        try {
            try (final InputStream input = assets.open(name)) {
                return loadAsString(input);
            }
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    public static String loadAsString(@Nullable InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }

        try {
            try (final ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int length;

                while ((length = inputStream.read(buffer)) > 0) {
                    result.write(buffer, 0, length);
                }
                return result.toString("UTF-8");
            }
        } catch (IOException e) {
            return null;
        }
    }
}
