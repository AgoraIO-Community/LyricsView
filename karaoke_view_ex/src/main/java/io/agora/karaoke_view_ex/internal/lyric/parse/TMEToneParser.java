package io.agora.karaoke_view_ex.internal.lyric.parse;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.model.TMELyricModel;
import io.agora.karaoke_view_ex.model.LyricModel;
import io.agora.karaoke_view_ex.tme.TMEStrictJson;

/** Separate TME reader; never changes the generic LRC/XML/KRC parsers. */
public final class TMEToneParser {
    private static final Pattern TIME = Pattern.compile("\\[([0-9]+):([0-9]{2})(?:\\.([0-9]+))?]");
    private static final Pattern META = Pattern.compile("^\\[(ti|ar):(.*)]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDIT = Pattern.compile("^(词|曲|作词|作曲|编曲)\\s*[:：]");

    private TMEToneParser() {}

    public static LyricModel parse(String toneFilePath, String lyricFilePath) {
        if (toneFilePath == null || lyricFilePath == null) return null;
        try {
            List<TMELyricModel.Note> notes = readNotes(readFile(toneFilePath));
            TMELyricModel model = readLyrics(readFile(lyricFilePath));
            if (notes.isEmpty() || model.lines.isEmpty()) return null;
            int noteIndex = 0;
            for (int i = 0; i < model.lines.size(); i++) {
                LyricsLineModel line = model.lines.get(i);
                long start = line.getStartTime();
                boolean lastLine = i + 1 == model.lines.size();
                long end = lastLine ? Long.MAX_VALUE : model.lines.get(i + 1).getStartTime();
                boolean excluded = excluded(line.tones.get(0).word, model);
                List<TMELyricModel.Note> lineNotes = new ArrayList<>();
                while (noteIndex < notes.size() && notes.get(noteIndex).begin < start) noteIndex++;
                while (noteIndex < notes.size() && notes.get(noteIndex).begin < end) {
                    TMELyricModel.Note note = notes.get(noteIndex++);
                    long clippedEnd = Math.min(end, note.end);
                    if (!excluded && clippedEnd > note.begin) {
                        lineNotes.add(new TMELyricModel.Note(note.begin, clippedEnd, note.frequency));
                    }
                }
                if (lastLine) {
                    end = start;
                    for (TMELyricModel.Note note : lineNotes) end = Math.max(end, note.end);
                }
                line.tones.get(0).end = end;
                line.duration = end - start;
                if (!lineNotes.isEmpty()) model.scoringLines.add(new TMELyricModel.ScoringLine(line, lineNotes));
            }
            if (model.scoringLines.isEmpty()) return null;
            model.hasPitch = true;
            model.preludeEndPosition = model.scoringLines.get(0).getStartTime();
            model.duration = model.lines.get(model.lines.size() - 1).getEndTime();
            return model;
        } catch (Exception failure) {
            return null;
        }
    }

    private static String readFile(String path) throws Exception {
        StringBuilder text = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(path), "UTF-8")) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) text.append(buffer, 0, count);
        }
        return text.toString();
    }

    private static List<TMELyricModel.Note> readNotes(String json) throws Exception {
        if (json.startsWith("\ufeff")) json = json.substring(1);
        TMEStrictJson.INSTANCE.validate(json);
        JsonElement root;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            root = new Gson().getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || root == null || !root.isJsonArray()) {
                throw new IllegalArgumentException("Expected tone array");
            }
        }
        List<TMELyricModel.Note> notes = new ArrayList<>();
        for (JsonElement item : root.getAsJsonArray()) {
            try {
                if (!item.isJsonObject()) continue;
                JsonObject data = item.getAsJsonObject();
                long start = number(data.get("st")).longValueExact();
                long duration = number(data.get("d")).longValueExact();
                double tone = number(data.get("p")).doubleValue();
                if (start < 0 || duration <= 0 || duration > Long.MAX_VALUE - start || tone < 32) continue;
                float frequency = (float) (55 * (Math.pow(2, (tone - 32) / 12) - 1e-6));
                if (Float.isNaN(frequency) || Float.isInfinite(frequency) || frequency <= 0) continue;
                notes.add(new TMELyricModel.Note(start, start + duration, frequency));
            } catch (IllegalArgumentException | ArithmeticException ignored) {
                // Individual malformed records do not invalidate the remaining song.
            }
        }
        Collections.sort(notes, (a, b) -> a.begin < b.begin ? -1 : a.begin == b.begin ? 0 : 1);
        return notes;
    }

    private static BigDecimal number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Missing number");
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber() && !primitive.isString()) throw new IllegalArgumentException("Invalid number type");
        return new BigDecimal(primitive.getAsString());
    }

    private static TMELyricModel readLyrics(String text) {
        TMELyricModel model = new TMELyricModel();
        if (text.startsWith("\ufeff")) text = text.substring(1);
        for (String raw : text.split("\\r\\n|\\n|\\r", -1)) {
            Matcher metadata = META.matcher(raw.trim());
            if (metadata.matches()) {
                if ("ti".equalsIgnoreCase(metadata.group(1))) model.name = metadata.group(2).trim();
                else model.singer = metadata.group(2).trim();
                continue;
            }
            Matcher times = TIME.matcher(raw);
            List<Long> starts = new ArrayList<>();
            int cursor = 0;
            while (times.find(cursor) && times.start() == cursor) {
                try {
                    long minutes = Long.parseLong(times.group(1));
                    int seconds = Integer.parseInt(times.group(2));
                    String fraction = times.group(3);
                    long millis = fraction == null ? 0 : new BigDecimal("0." + fraction).movePointRight(3).longValue();
                    if (seconds < 60 && minutes <= (Long.MAX_VALUE - seconds * 1000L - millis) / 60000L) {
                        starts.add(minutes * 60000L + seconds * 1000L + millis);
                    }
                } catch (NumberFormatException ignored) {}
                cursor = times.end();
            }
            for (long start : starts) {
                LyricsLineModel.Tone display = new LyricsLineModel.Tone();
                display.begin = start;
                display.end = start;
                display.word = raw.substring(cursor);
                display.lang = LyricsLineModel.Lang.Unknown;
                display.isFullLine = true;
                model.lines.add(new LyricsLineModel(display));
            }
        }
        Collections.sort(model.lines, (a, b) -> a.getStartTime() < b.getStartTime() ? -1
                : a.getStartTime() == b.getStartTime() ? 0 : 1);
        return model;
    }

    private static boolean excluded(String text, TMELyricModel model) {
        String content = text.trim();
        if (content.isEmpty() || CREDIT.matcher(content).find()) return true;
        if (model.name != null && !model.name.isEmpty() && content.equals(model.name)) return true;
        if (model.singer != null && !model.singer.isEmpty() && content.equals(model.singer)) return true;
        if (model.name != null && model.singer != null && !model.name.isEmpty() && !model.singer.isEmpty()) {
            String title = content.replaceAll("\\s+", "");
            String song = model.name.replaceAll("\\s+", "");
            String artist = model.singer.replaceAll("\\s+", "");
            return title.equals(song + "-" + artist) || title.equals(song + "–" + artist)
                    || title.equals(song + "—" + artist) || title.equals(song + "(" + artist + ")")
                    || title.equals(song + "（" + artist + "）");
        }
        return false;
    }
}
