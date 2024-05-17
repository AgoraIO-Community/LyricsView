package io.agora.examples.karaoke_view_ex;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

import io.agora.karaoke_view.internal.lyric.parse.LyricPitchParser;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    private static final String TAG = "ExampleUnitTest";

    private void Logx(String msg) {
        /**
         * Log.d|e|w|i not supported by unit test
         */
        System.out.println(TAG + " " + msg);
    }

    @Test
    public void invalidFileChecking() {
        Exception exception = null;
        File lyrics = new File("");
        try {
            LyricPitchParser.parseFile(lyrics, null);
        } catch (Exception e) {
            exception = e;
        }
        if (exception != null) {
            exception.printStackTrace();
        }
        assertTrue(exception instanceof IllegalArgumentException);
        Logx("invalidFileChecking: expected IllegalArgumentException above");
    }

}
