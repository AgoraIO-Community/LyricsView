package io.agora.karaoke_view_ex.internal.lyric.parse;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.agora.karaoke_view_ex.internal.model.PitchData;
import io.agora.karaoke_view_ex.internal.model.XmlPitchData;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;

/**
 * Parser for pitch data in various formats.
 * This class provides methods to parse pitch data from different file formats
 * and convert them into standardized data structures for use in karaoke applications.
 */
public class PitchParser {
    /**
     * Parses XML format pitch data
     *
     * @param fileData The byte array containing XML pitch data
     * @return An XmlPitchData object containing the parsed pitch data
     */
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

    /**
     * Fetches the average pitch value within a specified time range
     *
     * @param data             The XmlPitchData containing pitch values
     * @param startOfFirstTone The start time of the first tone in milliseconds
     * @param start            The start time of the range in milliseconds
     * @param end              The end time of the range in milliseconds
     * @return The average pitch value within the range, or 0 if no valid pitches found
     */
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

    /**
     * Reads a 32-bit integer in little-endian format from a ByteBuffer
     *
     * @param buffer The ByteBuffer to read from
     * @return The integer value read
     * @throws IOException If an I/O error occurs
     */
    private static int readLittleEndianInt(ByteBuffer buffer) throws IOException {
        int b1 = getUnsignedByte(buffer);
        int b2 = getUnsignedByte(buffer);
        int b3 = getUnsignedByte(buffer);
        int b4 = getUnsignedByte(buffer);

        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    /**
     * Reads a 64-bit double in little-endian format from a ByteBuffer
     *
     * @param buffer The ByteBuffer to read from
     * @return The double value read
     * @throws IOException If an I/O error occurs
     */
    private static double readLittleEndianDouble(ByteBuffer buffer) throws IOException {
        long bits = readLittleEndianLong(buffer);
        return Double.longBitsToDouble(bits);
    }

    /**
     * Reads a 64-bit long in little-endian format from a ByteBuffer
     *
     * @param buffer The ByteBuffer to read from
     * @return The long value read
     * @throws IOException If an I/O error occurs
     */
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

    /**
     * Gets an unsigned byte from the current position in a ByteBuffer and advances the position
     *
     * @param buffer The ByteBuffer to read from
     * @return The unsigned byte value as an int
     */
    static int getUnsignedByte(ByteBuffer buffer) {
        int pos = buffer.position();
        int rtn = getUnsignedByte(buffer, pos);
        buffer.position(pos + 1);
        return rtn;
    }

    /**
     * Gets an unsigned byte from a specific position in a ByteBuffer
     *
     * @param buffer The ByteBuffer to read from
     * @param offset The position to read from
     * @return The unsigned byte value as an int
     */
    static int getUnsignedByte(ByteBuffer buffer, int offset) {
        return asUnsignedByte(buffer.get(offset));
    }

    /**
     * Converts a signed byte to an unsigned byte value
     *
     * @param b The signed byte to convert
     * @return The unsigned byte value as an int
     */
    static int asUnsignedByte(byte b) {
        return b & 0xFF;
    }

    /**
     * Parses KRC format pitch data
     *
     * @param fileData The byte array containing KRC pitch data
     * @return A list of PitchData objects, or null if parsing fails
     */
    public static List<PitchData> doParseKrc(byte[] fileData) {
        if (fileData == null || fileData.length == 0) {
            return null;
        }
        try {
            String jsonData = new String(fileData);
            JSONObject jsonObject = new JSONObject(jsonData);
            if (jsonObject.has("pitchDatas")) {
                jsonData = jsonObject.getString("pitchDatas");
                Gson gson = new Gson();
                return Arrays.asList(gson.fromJson(jsonData, PitchData[].class));
            }
        } catch (Exception e) {
            LogUtils.e("doParse error: " + e.getMessage());
        }
        return null;
    }
}
