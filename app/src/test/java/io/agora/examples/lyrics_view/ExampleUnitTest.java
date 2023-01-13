package io.agora.examples.lyrics_view;

import org.junit.Test;

import static org.junit.Assert.*;

import android.util.Log;

import java.io.File;

import io.agora.lyrics_view.v11.utils.LyricsParser;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    private static final String TAG = "ExampleUnitTest";

    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void invalidFileChecking() {
        Exception exception = null;
        File lyrics = new File("");
        try {
            LyricsParser.parse(lyrics);
        } catch (Exception e) {
            exception = e;
        }
        if (exception != null) {
            exception.printStackTrace();
        }
        assertTrue(exception instanceof IllegalArgumentException);
    }
}