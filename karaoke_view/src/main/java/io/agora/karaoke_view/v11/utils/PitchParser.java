package io.agora.karaoke_view.v11.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import io.agora.karaoke_view.v11.internal.PitchesModel;

public class PitchParser {
    private static final String TAG = "PitchParser";

    @Nullable
    public static PitchesModel doParse(File file) {
        PitchesModel model = new PitchesModel(new ArrayList<>());

        if (file == null || !file.isFile() || !file.exists() || !file.canRead() || file.length() == 0) {
            return model;
        }

        FileInputStream fis = null;
        DataInputStream dis = null;
        try {
            fis = new FileInputStream(file);
            dis = new DataInputStream(fis);
            // Read int32 values until the end of the file
            model.version = readLittleEndianInt(dis);
            model.interval = readLittleEndianInt(dis);
            model.reserved = readLittleEndianInt(dis);

            Log.i(TAG, "Version for the pitch file: " + model.version);
            Log.i(TAG, "Interval for the pitch file: " + model.interval);
            Log.i(TAG, "Reserved for the pitch file: " + model.reserved);

            while (dis.available() >= 8) { // Each pitch value at least takes 8 bits
                double pitch = readLittleEndianDouble(dis);
                DecimalFormat formatter = new DecimalFormat("#.###");
                String formattedPitch = formatter.format(pitch);

                model.pitches.add(Double.parseDouble(formattedPitch));
            }
        } catch (IOException e) {
        } finally {
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException e) {
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                }
            }
        }

        return model;
    }

    public static double fetchPitchWithRange(PitchesModel data, long startOfFirstTone, long start, long end) {
        if (data == null) {
            return 0d;
        }

        int fromIdx = (int) ((start - startOfFirstTone) / data.interval);
        int toIdx = (int) ((end - startOfFirstTone) / data.interval);

        double total = 0d;
        int numberOfValidPitches = 0;
        for (int idx = fromIdx; idx < toIdx; idx++) {
            double pitch = data.pitches.get(idx);
            if (pitch > 0) { // Filter value <= 0
                total += pitch;
                numberOfValidPitches++;
            }
        }
        if (numberOfValidPitches > 0) {
            return total / numberOfValidPitches;
        }
        return 0d;
    }

    private static int readLittleEndianInt(DataInputStream inputStream) throws IOException {
        int b1 = inputStream.readUnsignedByte();
        int b2 = inputStream.readUnsignedByte();
        int b3 = inputStream.readUnsignedByte();
        int b4 = inputStream.readUnsignedByte();

        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    private static double readLittleEndianDouble(DataInputStream inputStream) throws IOException {
        long bits = readLittleEndianLong(inputStream);
        return Double.longBitsToDouble(bits);
    }

    private static long readLittleEndianLong(DataInputStream inputStream) throws IOException {
        long b1 = inputStream.readUnsignedByte();
        long b2 = inputStream.readUnsignedByte();
        long b3 = inputStream.readUnsignedByte();
        long b4 = inputStream.readUnsignedByte();
        long b5 = inputStream.readUnsignedByte();
        long b6 = inputStream.readUnsignedByte();
        long b7 = inputStream.readUnsignedByte();
        long b8 = inputStream.readUnsignedByte();

        return (b8 << 56) | (b7 << 48) | (b6 << 40) | (b5 << 32) | (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }
}
