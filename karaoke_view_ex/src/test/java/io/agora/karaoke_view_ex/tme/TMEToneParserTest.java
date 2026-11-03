package io.agora.karaoke_view_ex.tme;

import org.junit.Test;

import io.agora.karaoke_view_ex.internal.lyric.parse.TMEToneParser;
import io.agora.karaoke_view_ex.internal.model.TMELyricModel;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.model.LyricModel;

import static org.junit.Assert.*;

public class TMEToneParserTest extends TMEFileTestSupport {

    @Test public void decimalTimesMetadataAndFrequencyRemainPrecise() throws Exception {
        TMELyricModel model = (TMELyricModel) parse(
                "[{\"st\":\"43300\",\"d\":\"500\",\"p\":\"32\"},{\"st\":44456,\"d\":200,\"p\":44}]",
                "\ufeff[ti:歌曲]\n[ar:歌手]\n[00:44.456]第二句\n[00:43.30]  第一行  ");
        assertEquals("歌曲", model.name);
        assertEquals("歌手", model.singer);
        assertEquals(43300, model.lines.get(0).getStartTime());
        assertEquals(44456, model.lines.get(1).getStartTime());
        assertEquals("  第一行  ", model.lines.get(0).tones.get(0).word);
        assertEquals(55 * (1 - 1e-6), model.scoringLines.get(0).notes.get(0).frequency, 0.00001);
        assertEquals(55 * (2 - 1e-6), model.scoringLines.get(1).notes.get(0).frequency, 0.00001);
        assertEquals("", model.scoringLines.get(0).tones.get(0).word);
        assertEquals(LyricsLineModel.Lang.Unknown, model.scoringLines.get(0).tones.get(0).lang);
        assertEquals(44656, model.lines.get(1).getEndTime());
        assertEquals(43300, model.preludeEndPosition);
        assertTrue(model.hasPitch);
        assertTrue(model.copy() instanceof TMELyricModel);
    }

    @Test public void keepsDisplayLinesButDropsCreditsAndClipsNotes() throws Exception {
        TMELyricModel model = (TMELyricModel) parse(
                "[{\"st\":500,\"d\":100,\"p\":40},{\"st\":1500,\"d\":100,\"p\":40},"
                + "{\"st\":2200,\"d\":1800,\"p\":40},{\"st\":5000,\"d\":200,\"p\":50}]",
                "[ti:歌名]\n[ar:歌手]\n[00:00.00]歌名 - 歌手\n[00:01.00]作词：某人\n"
                + "[00:02.00]演唱一\n[00:03.00]\n[00:04.00]没有音符\n[00:05.00]演唱二");
        assertEquals(6, model.lines.size());
        assertEquals(2, model.scoringLines.size());
        assertSame(model.lines.get(2), model.scoringLines.get(0).displayLine);
        assertEquals(3000, model.scoringLines.get(0).tones.get(0).end);
        assertEquals(1, model.scoringLines.get(0).tones.size());
        assertEquals(2000, model.preludeEndPosition);
        assertEquals(5200, model.duration);
    }

    @Test public void skipsBadRecordsWithoutLosingValidNotes() throws Exception {
        TMELyricModel model = (TMELyricModel) parse(
                "[null,{},true,{\"st\":0,\"d\":0,\"p\":40},{\"st\":-1,\"d\":2,\"p\":40},"
                + "{\"st\":0,\"d\":2,\"p\":31},{\"st\":true,\"d\":2,\"p\":40},"
                + "{\"st\":9223372036854775808,\"d\":2,\"p\":40},"
                + "{\"st\":9223372036854775807,\"d\":2,\"p\":40},"
                + "{\"st\":0.5,\"d\":2,\"p\":40},{\"st\":0,\"d\":2,\"p\":1e300},"
                + "{\"st\":1000,\"d\":100,\"p\":40}]", "[00:01.00]有效");
        assertEquals(1, model.scoringLines.get(0).tones.size());
    }

    @Test public void invalidInputsReturnNull() throws Exception {
        for (String input : new String[]{"{}", "null", "[]", "[{}]", "[", "[TRUE]",
                "[{\"st\":0,\"d\":100,\"p\":40}] garbage"}) {
            assertNull(parse(input, "[00:00.00]歌词"));
        }
        assertNull(parse("[{\"st\":0,\"d\":100,\"p\":40}]", "[ti:没有时间行]"));
        assertNull(parse("[{\"st\":0,\"d\":100,\"p\":40}]", "[00:01.00]无法归属"));
        assertNull(parse("[{\"st\":0,\"d\":100,\"p\":40}]", "[00:00.00]编曲：某人"));
        assertNull(TMEToneParser.parse(null, null));
        assertNull(TMEToneParser.parse("/does/not/exist", "/does/not/exist"));
    }

    @Test public void multiTagsFractionDigitsAndEqualStartsAreDeterministic() throws Exception {
        TMELyricModel model = (TMELyricModel) parse(
                "[{\"st\":1200,\"d\":100,\"p\":40},{\"st\":2123,\"d\":100,\"p\":40}]",
                "[00:01.2][00:02.1234]重复\n[00:01.2]同起点\n[00:99.00]非法秒");
        assertEquals(3, model.lines.size());
        assertEquals(2, model.scoringLines.size());
        assertSame(model.lines.get(1), model.scoringLines.get(0).displayLine);
        assertEquals(2123, model.lines.get(2).getStartTime());
    }

    @Test public void lastLineEndsAtItsOwnRetainedNotes() throws Exception {
        TMELyricModel model = (TMELyricModel) parse(
                "[{\"st\":0,\"d\":100000,\"p\":44},{\"st\":2000,\"d\":500,\"p\":44}]",
                "[00:00.00]作词：某人\n[00:02.00]最后一句");
        assertEquals(2500, model.lines.get(1).getEndTime());
        assertEquals(2500, model.duration);
        TMELyricModel trailingEmpty = (TMELyricModel) parse(
                "[{\"st\":1000,\"d\":100000,\"p\":44}]", "[00:01.00]演唱\n[00:02.00]");
        assertEquals(2000, trailingEmpty.lines.get(1).getEndTime());
    }
}
