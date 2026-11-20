package io.agora.lyricsscore;

import io.agora.lyricsscore.internal.lyric.parse.TMEToneParser;
import io.agora.lyricsscore.model.LyricModel;

/** Host-only View facade; static file parsing delegates to the unchanged production parser. */
public final class KaraokeView {
    public static LyricModel parseTMEToneData(String pitchPath, String lyricPath) {
        return TMEToneParser.parse(pitchPath, lyricPath);
    }
}
