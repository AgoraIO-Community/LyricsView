package io.agora.karaoke_view_ex.internal.lyric.parse;

import android.text.TextUtils;
import android.text.format.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;
import io.agora.karaoke_view_ex.utils.Utils;

/**
 * Lyrics parser for different file formats.
 * This class provides methods to parse lyrics from various formats (KRC, LRC, XML)
 * and convert them into a standardized LyricModel.
 */
public class LyricParser {
    /**
     * Regular expression pattern for matching time tags in LRC format
     */
    private static final Pattern LRC_PATTERN_TIME = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]");

    /**
     * Regular expression pattern for matching lyric content with time tags in LRC format
     */
    private static final Pattern LRC_LYRIC_CONTENT_PATTERN = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{3})>(.*?)(?=<|$)");

    /**
     * Parses KRC format lyrics file data
     *
     * @param krcFileData The byte array containing KRC file data
     * @param lyricOffset Time offset to apply to lyrics (in milliseconds)
     * @return A LyricModel containing the parsed lyrics data
     */
    public static LyricModel doParseKrc(byte[] krcFileData, int lyricOffset) {
        String content = new String(krcFileData);

        String[] lines = content.split("\\n|\\r\\n");

        Map<String, String> metadata = new HashMap<>(lines.length);

        List<LyricsLineModel> lineModels = new ArrayList<>();

        boolean isFirstLine = true;
        for (String line : lines) {
            if (isFirstLine) {
                line = io.agora.karaoke_view_ex.internal.utils.Utils.removeStringBom(line);
                isFirstLine = false;
            }
            // Process metadata section: `[ti:星晴]`
            if (line.startsWith("[")) {
                int index = line.indexOf(":");
                if (index != -1) {
                    // Key value, from the second character to before ":"
                    String key = line.substring(1, index);
                    // Value, from after ":" to before "]"
                    String value = line.substring(index + 1, line.length() - 1);
                    metadata.put(key, value);
                } else {
                    if (line.contains("<") && line.contains(">")) {
                        long offsetValue = 0;
                        if (metadata.containsKey("offset")) {
                            try {
                                offsetValue = Long.parseLong(Objects.requireNonNull(metadata.get("offset")));
                            } catch (Exception e) {
                                LogUtils.e("parse offset error");
                            }

                        }
                        LyricsLineModel lineModel = parseKrcLine(line, lyricOffset);
                        if (lineModel != null) {
                            lineModels.add(lineModel);
                        } else {
                            LogUtils.e("parseLine error");
                        }
                    }
                }
            }
        }

        LyricModel lyrics = new LyricModel(LyricType.KRC);
        lyrics.name = metadata.containsKey("ti") ? metadata.get("ti") : "unknownTitle";
        lyrics.singer = metadata.containsKey("ar") ? metadata.get("ar") : "unknownSinger";
        lyrics.lines = lineModels;
        lyrics.preludeEndPosition = 0;
        if (lineModels.isEmpty()) {
            lyrics.duration = 0;
        } else {
            LyricsLineModel lastLine = lineModels.get(lineModels.size() - 1);
            lyrics.duration = lastLine.getStartTime() + lastLine.duration;
        }
        return lyrics;
    }

    /**
     * Parses a single line from KRC format
     *
     * @param line   The line to parse
     * @param offset Time offset to apply (in milliseconds)
     * @return A LyricsLineModel representing the parsed line, or null if parsing fails
     */
    private static LyricsLineModel parseKrcLine(String line, long offset) {
        try {
            int rangeStart = line.indexOf("[");
            int rangeEnd = line.indexOf("]");
            if (rangeStart == -1 || rangeEnd == -1) {
                return null;
            }

            String timeStr = line.substring(rangeStart + 1, rangeEnd);
            String[] timeComponents = timeStr.split(",");

            // Process line time: `0,1600`
            if (timeComponents.length != 2) {
                return null;
            }

            long startTimeOrigin = Long.parseLong(timeComponents[0].trim());
            long lineStartTime = startTimeOrigin >= offset ? startTimeOrigin - offset : startTimeOrigin;

            long lineDuration = Long.parseLong(timeComponents[1].trim());
            String lineContent = line.substring(rangeEnd + 1).trim();

            // Parse line content
            List<LyricsLineModel.Tone> tones = new ArrayList<>();
            String[] toneComponents = lineContent.split("<");
            for (String toneComponent : toneComponents) {
                if (toneComponent.isEmpty()) {
                    continue;
                }

                // Parse word content: '0,177,0>星'
                String[] toneParts = toneComponent.split(">");
                if (toneParts.length == 2) {
                    String word = toneParts[1];

                    String[] timeParts = toneParts[0].split(",");
                    if (timeParts.length == 3) {
                        long startTime = lineStartTime + Long.parseLong(timeParts[0]);
                        long duration = Long.parseLong(timeParts[1]);
                        double pitch = Double.parseDouble(timeParts[2]);
                        LyricsLineModel.Tone tone = new LyricsLineModel.Tone();
                        tone.begin = startTime;
                        tone.end = startTime + duration;
                        tone.word = word;
                        tone.pitch = (int) pitch;
                        tone.lang = LyricsLineModel.Lang.Chinese;
                        tones.add(tone);
                    }
                }
            }
            LyricsLineModel lineModel = new LyricsLineModel(tones);
            lineModel.duration = lineDuration;
            return lineModel;
        } catch (Exception e) {
            LogUtils.e("parseKrcLine error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Parses LRC format lyrics file data
     *
     * @param fileData The byte array containing LRC file data
     * @return A LyricModel containing the parsed lyrics data, or null if parsing fails
     */
    public static LyricModel doParseLrc(byte[] fileData) {
        if (null == fileData) {
            return null;
        }

        List<LyricsLineModel> lines = new ArrayList<>();
        try {
            String fileContent = new String(fileData, Constants.UTF_8);
            String[] lineArray = fileContent.split("\\n|\\r\\n");
            boolean isFirstLine = true;
            for (String line : lineArray) {
                if (isFirstLine) {
                    line = io.agora.karaoke_view_ex.internal.utils.Utils.removeStringBom(line);
                    isFirstLine = false;
                }
                List<LyricsLineModel> list = parseLrcLine(line);
                if (list != null && !list.isEmpty()) {
                    lines.addAll(list);
                }
            }
        } catch (Exception e) {
            LogUtils.e("doParse error: " + e.getMessage());
        }


        return parseLrcLines(lines);
    }

    /**
     * Processes a list of parsed LRC lines into a complete LyricModel
     *
     * @param lines List of parsed LyricsLineModel objects
     * @return A LyricModel containing the processed lyrics data
     */
    private static LyricModel parseLrcLines(List<LyricsLineModel> lines) {
        LyricModel lyrics = new LyricModel(LyricType.LRC);
        if (null == lines || lines.isEmpty()) {
            return lyrics;
        }
        lyrics.lines = lines;
        for (int i = 0; i < lines.size() - 1; i++) {
            LyricsLineModel cur = lines.get(i);
            LyricsLineModel next = lines.get(i + 1);

            if (cur.tones == null || cur.tones.size() <= 0) {
                continue;
            }

            LyricsLineModel.Tone first = cur.tones.get(0);
            first.end = next.getStartTime();
        }

        if (!lines.isEmpty()) {
            long faked = lines.get(lines.size() - 1).getStartTime() + 8765;
            lines.get(lines.size() - 1).tones.get(0).end = faked;
            // We do not know the last end timestamp of song
            lyrics.duration = faked;
        }
        // Always the first line of lyrics
        lyrics.preludeEndPosition = lines.get(0).getStartTime();

        if (lyrics.duration <= 0 || lyrics.preludeEndPosition < 0) {
            LogUtils.e("no sentence or unexpected timestamp of sentence: " + lyrics.preludeEndPosition + " " + lyrics.duration);
            // Invalid lyrics
            return null;
        }
        return lyrics;
    }

    /**
     * Parses a single line from LRC format
     *
     * @param line The line to parse
     * @return A list of LyricsLineModel objects representing the parsed line, or null if parsing fails
     */
    private static List<LyricsLineModel> parseLrcLine(String line) {
        if (TextUtils.isEmpty(line)) {
            return null;
        }

        List<LyricsLineModel> lines = new ArrayList<>();

        try {
            line = line.trim();
            // [00:17.65]让我掉下眼泪的
            Matcher lineMatcher = LyricPitchParser.LRC_PATTERN_LINE.matcher(line);
            if (!lineMatcher.matches()) {
                return null;
            }

            String times = lineMatcher.group(1);
            if (times == null) {
                return null;
            }

            String text = lineMatcher.group(3);


            if (TextUtils.isEmpty(text)) {
                return lines;
            }

            if (LRC_LYRIC_CONTENT_PATTERN.matcher(text).matches()) {
                Matcher matcher = LRC_LYRIC_CONTENT_PATTERN.matcher(text);
                LyricsLineModel lineModel = new LyricsLineModel();
                while (matcher.find()) {
                    String content = Utils.removeQuotes(matcher.group(4));

                    long min = Long.parseLong(Objects.requireNonNull(matcher.group(1)));
                    long sec = Long.parseLong(Objects.requireNonNull(matcher.group(2)));
                    long mil = Long.parseLong(Objects.requireNonNull(matcher.group(3)));

                    long beginTime = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil;

                    LyricsLineModel.Tone tone = new LyricsLineModel.Tone();
                    tone.begin = beginTime;
                    tone.word = content;
                    tone.lang = LyricsLineModel.Lang.Chinese;
                    lineModel.tones.add(tone);
                }
                lines.add(lineModel);
                return lines;
            } else {
                // [00:17.65]
                Matcher timeMatcher = LRC_PATTERN_TIME.matcher(times);
                while (timeMatcher.find()) {
                    long min = Long.parseLong(Objects.requireNonNull(timeMatcher.group(1)));
                    long sec = Long.parseLong(Objects.requireNonNull(timeMatcher.group(2)));
                    String milInString = timeMatcher.group(3);
                    long mil = 0;
                    if (null != milInString) {
                        mil = Long.parseLong(milInString);
                    }
                    // If milliseconds is two digits, multiply by 10
                    if (!TextUtils.isEmpty(milInString) && milInString.length() == 2) {
                        mil = mil * 10;
                    }
                    long beginTime = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil;

                    LyricsLineModel.Tone tone = new LyricsLineModel.Tone();
                    tone.begin = beginTime;
                    tone.word = text;
                    tone.lang = LyricsLineModel.Lang.Chinese;
                    tone.isFullLine = true;
                    lines.add(new LyricsLineModel(tone));
                }
            }
        } catch (Exception e) {
            LogUtils.e("parseLrcLine error: " + e.getMessage());
        }
        return lines;
    }

    /**
     * Parses XML format lyrics file data
     *
     * @param fileData The byte array containing XML file data
     * @return A LyricModel containing the parsed lyrics data
     */
    public static LyricModel doParseXml(byte[] fileData) {
        if (null == fileData) {
            return null;
        }
        return LyricsParserXml.parseXml(fileData);
    }
}
