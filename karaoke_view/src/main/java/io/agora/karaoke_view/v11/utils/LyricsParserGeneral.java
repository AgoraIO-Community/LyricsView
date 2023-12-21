package io.agora.karaoke_view.v11.utils;

import android.text.TextUtils;
import android.text.format.DateUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.agora.karaoke_view.v11.constants.Constants;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;
import io.agora.logging.LogManager;

/**
 * 加载 lrc 歌词(逐句)
 * 样式：[00:08.15]一盏黄黄旧旧的灯
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/7/6
 */
class LyricsParserGeneral {
    private static final Pattern PATTERN_LINE = Pattern.compile("((\\[\\d{2}:\\d{2}\\.\\d{2,3}\\])+)(.+)");
    private static final Pattern PATTERN_TIME = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]");

    private static final String TAG = Constants.TAG + "-LyricsParserGeneral";

    /**
     * 从文件解析歌词
     */
    public static LyricsModel parseLrc(File file) {
        if (file == null || !file.isFile() || !file.exists() || !file.canRead() || file.length() == 0) {
            return null;
        }

        LyricsModel lyrics = new LyricsModel(LyricsModel.Type.General);

        List<LyricsLineModel> lines = new ArrayList<>();
        lyrics.lines = lines;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                List<LyricsLineModel> list = parseLine(line);
                if (list != null && !list.isEmpty()) {
                    lines.addAll(list);
                }
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < lines.size() - 1; i++) {
            LyricsLineModel cur = lines.get(i);
            LyricsLineModel next = lines.get(i + 1);

            if (cur.tones == null || cur.tones.size() <= 0) {
                continue;
            }

            LyricsLineModel.Tone first = cur.tones.get(0);
            first.end = next.getStartTime();
        }

        if (lines.size() > 0) {
            long faked = lines.get(lines.size() - 1).getStartTime() + 8765;
            lines.get(lines.size() - 1).tones.get(0).end = faked;
            lyrics.duration = faked; // We do not know the last end timestamp of song
        }

        lyrics.startOfVerse = lines.get(0).getStartTime(); // Always the first line of lyrics

        if (lyrics.duration <= 0 || lyrics.startOfVerse < 0) {
            LogManager.instance().error(TAG, "no sentence or unexpected timestamp of sentence: " + lyrics.startOfVerse + " " + lyrics.duration);
            return null; // Invalid lyrics
        }

        return lyrics;
    }

    /**
     * 解析一行歌词
     */
    private static List<LyricsLineModel> parseLine(String line) {
        if (TextUtils.isEmpty(line)) {
            return null;
        }

        line = line.trim();
        // [00:17.65]让我掉下眼泪的
        Matcher lineMatcher = PATTERN_LINE.matcher(line);
        if (!lineMatcher.matches()) {
            return null;
        }

        String times = lineMatcher.group(1);
        if (times == null) {
            return null;
        }

        String text = lineMatcher.group(3);
        List<LyricsLineModel> lines = new ArrayList<>();

        // [00:17.65]
        Matcher timeMatcher = PATTERN_TIME.matcher(times);
        while (timeMatcher.find()) {
            long min = Long.parseLong(timeMatcher.group(1));
            long sec = Long.parseLong(timeMatcher.group(2));
            String milInString = timeMatcher.group(3);
            long mil = Long.parseLong(milInString);
            // 如果毫秒是两位数，需要乘以 10
            if (milInString.length() == 2) {
                mil = mil * 10;
            }
            long beginTime = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil;

            LyricsLineModel.Tone tone = new LyricsLineModel.Tone();
            tone.begin = beginTime;
            tone.word = text;
            tone.lang = LyricsLineModel.Lang.Chinese;
            lines.add(new LyricsLineModel(tone));
        }
        return lines;
    }
}
