package io.agora.examples.utils;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Utils {

    @Nullable
    public static File copyAssetsToCreateNewFile(@NonNull Context context, @NonNull String name) {
        final AssetManager assets = context.getAssets();

        if (assets == null || name == null || name.isEmpty()) {
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

}
