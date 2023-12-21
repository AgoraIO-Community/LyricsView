package io.agora.karaoke_view.v11.utils;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;

import io.agora.karaoke_view.v11.constants.Constants;
import io.agora.karaoke_view.v11.internal.PitchesModel;
import io.agora.logging.LogManager;

public class PitchParser {
    private static final String TAG = Constants.TAG + "-PitchParser";

    @Nullable
    public static PitchesModel doParse(File file) {
        PitchesModel model = new PitchesModel(new ArrayList<>());

        if (file == null || !file.isFile() || !file.exists() || !file.canRead() || file.length() == 0) {
            return model;
        }

        FileInputStream fis = null;
        ByteBuffer buffer = null;
        try {
            fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int size = fis.read(data);
            if (size != file.length()) {
                LogManager.instance().error(TAG, "Content not as expected size: " + file.length() + ", actual: " + size);
                return model;
            }
            buffer = ByteBuffer.wrap(data);
            // Read int32 values until the end of the file
            model.version = readLittleEndianInt(buffer);
            LogManager.instance().info(TAG, "Version for the pitch file: " + model.version);
            model.interval = readLittleEndianInt(buffer);
            LogManager.instance().info(TAG, "Interval for the pitch file: " + model.interval);
            model.reserved = readLittleEndianInt(buffer);
            LogManager.instance().info(TAG, "Reserved for the pitch file: " + model.reserved);

            DecimalFormat formatter = new DecimalFormat("#.###");
            while (buffer.remaining() >= 8) { // Each pitch value at least takes 8 bits
                double pitch = readLittleEndianDouble(buffer);
                String formattedPitch = formatter.format(pitch);
                model.pitches.add(Double.parseDouble(formattedPitch));
            }
        } catch (IOException e) {
        } finally {
            if (buffer != null) {
                buffer.clear();
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

    private static int readLittleEndianInt(ByteBuffer buffer) throws IOException {
        int b1 = getUnsignedByte(buffer);
        int b2 = getUnsignedByte(buffer);
        int b3 = getUnsignedByte(buffer);
        int b4 = getUnsignedByte(buffer);

        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    private static double readLittleEndianDouble(ByteBuffer buffer) throws IOException {
        long bits = readLittleEndianLong(buffer);
        return Double.longBitsToDouble(bits);
    }

    private static long readLittleEndianLong(ByteBuffer buffer) throws IOException {
        long b1 = getUnsignedByte(buffer);
        long b2 = getUnsignedByte(buffer);
        long b3 = getUnsignedByte(buffer);
        long b4 = getUnsignedByte(buffer);
        long b5 = getUnsignedByte(buffer);
        long b6 = getUnsignedByte(buffer);
        long b7 = getUnsignedByte(buffer);
        long b8 = getUnsignedByte(buffer);

        return (b8 << 56) | (b7 << 48) | (b6 << 40) | (b5 << 32) | (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    static int getUnsignedByte(ByteBuffer buffer) {
        int pos = buffer.position();
        int rtn = getUnsignedByte(buffer, pos);
        buffer.position(pos + 1);
        return rtn;
    }

    static int getUnsignedByte(ByteBuffer buffer, int offset) {
        return asUnsignedByte(buffer.get(offset));
    }

    static int asUnsignedByte(byte b) {
        return b & 0xFF;
    }
}
