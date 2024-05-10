package io.agora.karaoke_view.v11.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.agora.karaoke_view.v11.constants.Constants;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;
import io.agora.logging.LogManager;

/**
 * krc格式歌词解析
 */
public class KRCParser {
    private static final String logTag = Constants.TAG + "-KRCParser";

    private static void checkParameters(File file) {
        if (file == null || !file.isFile() || !file.exists() || !file.canRead() || file.length() == 0) {
            StringBuilder builder = new StringBuilder("Not a valid file for parser: " + file);
            if (file != null) {
                builder.append("\n");
                builder.append("{isFile: " + file.isFile() + ", ");
                builder.append("exists: " + file.exists() + ", ");
                builder.append("canRead: " + file.canRead() + ", ");
                builder.append("length: " + file.length() + "}");
            }
            throw new IllegalArgumentException(builder.toString());
        }
    }

    public static LyricsModel doParse(File file) {
        checkParameters(file);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int size = fis.read(data);
            if (size != file.length()) {
                LogManager.instance().error(logTag, "Content not as expected size: " + file.length() + ", actual: " + size);
                return new LyricsModel(LyricsModel.Type.KRC);
            }
            return doParse(data);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return new LyricsModel(LyricsModel.Type.KRC);
    }

    public static LyricsModel doParse(byte[] krcFileData) {
        String content = new String(krcFileData);
        Map<String, String> metadata = new HashMap<>();
        List<LyricsLineModel> lineModels = new ArrayList<>();

        String[] lines = content.split("\r\n");

        for (String line : lines) {
            // 处理metadata部分：`[ti:星晴]`
            if (line.startsWith("[")) {
                int index = line.indexOf(":");
                if (index != -1) {
                    // key值，从第二个字符开始取，到“:”之前
                    String key = line.substring(1, index);
                    // value值，“:”之后到“]”之前
                    String value = line.substring(index + 1, line.length() - 1);
                    metadata.put(key, value);
                } else {
                    if (line.contains("<") && line.contains(">")) {
                        String offsetString = metadata.get("offset");
                        if (offsetString != null) {
                            long offsetValue = Long.parseLong(offsetString);
                            LyricsLineModel lineModel = parseLine(line, offsetValue);
                            if (lineModel != null) {
                                lineModels.add(lineModel);
                            } else {
                                LogManager.instance().error(logTag, "parseLine error");
                            }
                        }
                    }
                }
            }
        }

        LyricsModel lyrics = new LyricsModel(LyricsModel.Type.KRC);
        lyrics.title = metadata.containsKey("ti") ? metadata.get("ti") : "unknownTitle";
        lyrics.artist = metadata.containsKey("ar") ? metadata.get("ar") : "unknownSinger";
        lyrics.lines = lineModels;
        lyrics.startOfVerse = 0;
        if (lineModels.isEmpty()) {
            lyrics.duration = 0;
        } else {
            LyricsLineModel lastLine = lineModels.get(lineModels.size() - 1);
            lyrics.duration = lastLine.getStartTime() + lastLine.duartion;

        }
        return lyrics;
    }

    // 解析行内容
    private static LyricsLineModel parseLine(String line, long offset) {
        int rangeStart = line.indexOf("[");
        int rangeEnd = line.indexOf("]");
        if (rangeStart == -1 || rangeEnd == -1) {
            return null;
        }

        String timeStr = line.substring(rangeStart + 1, rangeEnd);
        String[] timeComponents = timeStr.split(",");

        // 处理行时间: `0,1600`
        if (timeComponents.length != 2) {
            return null;
        }

        long lineStartTime = offset + Long.parseLong(timeComponents[0].trim());
        long lineDuration = Long.parseLong(timeComponents[1].trim());
        String lineContent = line.substring(rangeEnd + 1).trim();

        // 解析行内容
        List<LyricsLineModel.Tone> tones = new ArrayList<>();
        String[] toneComponents = lineContent.split("<");
        for (String toneComponent : toneComponents) {
            if (toneComponent.isEmpty()) {
                continue;
            }

            // 解析字内容： '0,177,0>星'
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
        lineModel.duartion = lineDuration;
        return lineModel;
    }
}

