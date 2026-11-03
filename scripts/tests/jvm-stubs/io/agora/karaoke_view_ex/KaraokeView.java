package io.agora.karaoke_view_ex;

import io.agora.karaoke_view_ex.internal.lyric.parse.TMEToneParser;
import io.agora.karaoke_view_ex.model.LyricModel;

/** Host-only View facade; static file parsing delegates to the unchanged production parser. */
public final class KaraokeView {
    public static LyricModel parseTMEToneData(String pitchPath, String lyricPath) {
        return TMEToneParser.parse(pitchPath, lyricPath);
    }
}
