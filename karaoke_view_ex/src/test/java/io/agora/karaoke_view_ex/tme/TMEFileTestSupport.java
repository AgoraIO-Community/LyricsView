package io.agora.karaoke_view_ex.tme;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import io.agora.karaoke_view_ex.internal.lyric.parse.TMEToneParser;
import io.agora.karaoke_view_ex.model.LyricModel;

public abstract class TMEFileTestSupport {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    LyricModel parse(String tone, String lyric) throws Exception {
        File toneFile = files.newFile();
        File lyricFile = files.newFile();
        try (FileOutputStream out = new FileOutputStream(toneFile)) {
            out.write(tone.getBytes(StandardCharsets.UTF_8));
        }
        try (FileOutputStream out = new FileOutputStream(lyricFile)) {
            out.write(lyric.getBytes(StandardCharsets.UTF_8));
        }
        return TMEToneParser.parse(toneFile.getAbsolutePath(), lyricFile.getAbsolutePath());
    }
}
