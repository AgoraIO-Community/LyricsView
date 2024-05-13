package io.agora.karaoke_view.internal.lyric.parse;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.agora.karaoke_view.internal.model.KrcPitchData;
import io.agora.karaoke_view.internal.model.XmlPitchData;
import io.agora.karaoke_view.internal.utils.LogUtils;

public class PitchParser {
    @NonNull
    public static XmlPitchData doParseXml(byte[] fileData) {
        XmlPitchData model = new XmlPitchData(new ArrayList<>());

        if (null == fileData) {
            return model;
        }

        ByteBuffer buffer = null;
        try {
            buffer = ByteBuffer.wrap(fileData);
            // Read int32 values until the end of the file
            model.version = readLittleEndianInt(buffer);
            LogUtils.d("Version for the pitch file: " + model.version);

            model.interval = readLittleEndianInt(buffer);
            LogUtils.d("Interval for the pitch file: " + model.interval);

            model.reserved = readLittleEndianInt(buffer);
            LogUtils.d("Reserved for the pitch file: " + model.reserved);

            DecimalFormat formatter = new DecimalFormat("#.###");
            while (buffer.remaining() >= 8) {
                // Each pitch value at least takes 8 bits
                double pitch = readLittleEndianDouble(buffer);
                String formattedPitch = formatter.format(pitch);
                model.pitches.add(Float.parseFloat(formattedPitch));
            }
        } catch (Exception e) {
            LogUtils.e("doParse error: " + e.getMessage());
        } finally {
            if (buffer != null) {
                buffer.clear();
            }
        }

        return model;
    }

    public static double fetchPitchWithRange(XmlPitchData data, long startOfFirstTone, long start, long end) {
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


    public static List<KrcPitchData> doParseKrc(byte[] fileData) {
        if (fileData == null || fileData.length == 0) {
            return null;
        }
        try {
            String jsonData = new String(fileData);
            JSONObject jsonObject = new JSONObject(jsonData);
            if (jsonObject.has("pitchDatas")) {
                jsonData = jsonObject.getString("pitchDatas");
                Gson gson = new Gson();
                return Arrays.asList(gson.fromJson(jsonData, KrcPitchData[].class));
            }
        } catch (Exception e) {
            LogUtils.e("doParse error: " + e.getMessage());
        }
        return null;
    }
}
