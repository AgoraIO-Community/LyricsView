package io.agora.examples.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

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

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static int dp2pix(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (dp * density);
    }

    public static int sp2pix(Context context, float sp) {
        float density = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (sp * density + 0.5);
    }

    public static int colorInStringToDex(String color) {
        int colorInDex = 0;
        switch (color) {
            case "Yellow":
                colorInDex = Color.YELLOW;
                break;
            case "White":
                colorInDex = Color.WHITE;
                break;
            case "Red":
                colorInDex = Color.RED;
                break;
            case "Gray":
                colorInDex = Color.parseColor("#9E9E9E");
                break;
            case "Orange":
                colorInDex = Color.parseColor("#FFA500");
                break;
            case "Blue":
                colorInDex = Color.BLUE;
                break;
            case "Brown":
                colorInDex = Color.parseColor("#654321");
                break;
            case "Green":
                colorInDex = Color.GREEN;
                break;
            default:
                colorInDex = 0;
                break;
        }
        return colorInDex;
    }
}
