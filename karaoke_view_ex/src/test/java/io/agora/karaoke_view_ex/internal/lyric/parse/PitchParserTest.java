package io.agora.karaoke_view_ex.internal.lyric.parse;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.agora.karaoke_view_ex.internal.model.PitchData;

public class PitchParserTest {
    @Test
    public void parsesTmePitchSegmentsAndSkipsInvalidRanges() {
        String json = "[{\"st\":\"22767\",\"d\":\"836\",\"p\":\"54\"},"
                + "{\"st\":24379,\"d\":321,\"p\":52},"
                + "{\"st\":25000,\"d\":0,\"p\":49}]";

        List<PitchData> pitches = PitchParser.doParseTme(json.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, pitches.size());
        assertEquals(22767, pitches.get(0).startTime);
        assertEquals(836, pitches.get(0).duration);
        assertEquals(54f, pitches.get(0).pitch, 0f);
        assertEquals(24379, pitches.get(1).startTime);
    }
}
