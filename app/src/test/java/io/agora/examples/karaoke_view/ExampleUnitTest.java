package io.agora.examples.karaoke_view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

import io.agora.karaoke_view.internal.LyricMachine;
import io.agora.karaoke_view.internal.lyric.parse.LyricPitchParser;
import io.agora.karaoke_view.internal.utils.VoicePitchChanger;

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

    private VoicePitchChanger mVoicePitchChanger = new VoicePitchChanger();

    @Test
    public void testVoiceChanger() {
        // TODO(Hai_Guo) Should figure out how to check the result if expected
        Logx("handlePitch(300, 189, 500): " + mVoicePitchChanger.handlePitch(300, 189, 500));
        Logx("handlePitch(310, 176, 500): " + mVoicePitchChanger.handlePitch(310, 176, 500));
        Logx("handlePitch(320, 137, 500): " + mVoicePitchChanger.handlePitch(320, 137, 500));
        Logx("handlePitch(390, 120, 500): " + mVoicePitchChanger.handlePitch(390, 120, 500));
        Logx("handlePitch(430, 210, 500): " + mVoicePitchChanger.handlePitch(430, 210, 500));
        Logx("handlePitch(460, 234, 500): " + mVoicePitchChanger.handlePitch(460, 234, 500));
        Logx("handlePitch(490, 199, 500): " + mVoicePitchChanger.handlePitch(490, 199, 500));
        Logx("handlePitch(300, 300, 500): " + mVoicePitchChanger.handlePitch(300, 300, 500));
    }

    @Test
    public void testVoiceChanger2() {
        assertEquals(0d, mVoicePitchChanger.handlePitch(0, 0, 400), 0d);
        assertEquals(0d, mVoicePitchChanger.handlePitch(1, 0, 400), 0d);
        assertEquals(1d, mVoicePitchChanger.handlePitch(1, 1, 400), 0d);
        assertEquals(93d, mVoicePitchChanger.handlePitch(100, 90, 400), 0d);
        assertEquals(87d, mVoicePitchChanger.handlePitch(100, 80, 400), 0d);
        assertEquals(110d, mVoicePitchChanger.handlePitch(200, 80, 400), 0d);
        assertEquals(164.60000000000002d, mVoicePitchChanger.handlePitch(400, 80, 400), 0d);
        assertEquals(311.66666666666663d, mVoicePitchChanger.handlePitch(400, 200, 400), 0d);
        assertEquals(400d, mVoicePitchChanger.handlePitch(400, 400, 400), 0d);
        assertEquals(400d, mVoicePitchChanger.handlePitch(400, 500, 400), 0d);
        assertEquals(400d, mVoicePitchChanger.handlePitch(400, 600, 400), 0d);
    }

    @Test
    public void testScoreCalculation2() {
        // TODO(Hai_Guo) Should figure out how to check the result if expected
        double scoreAfterNormalization = LyricMachine.calculateScore2(0f, 10, 0, 500f, 300);
        assertEquals(0.11564141395769778, scoreAfterNormalization, 0.01f);
        Logx("ScoringMachine.calculateScore2(0f, 10f, 0f, 500f, 300): " + scoreAfterNormalization);

        scoreAfterNormalization = LyricMachine.calculateScore2(0f, 10, 0, 400f, 300);
        assertEquals(0.5019550802136014, scoreAfterNormalization, 0.01f);
        Logx("ScoringMachine.calculateScore2(0f, 10f, 0f, 400f, 300): " + scoreAfterNormalization);

        scoreAfterNormalization = LyricMachine.calculateScore2(0f, 10, 0, 301f, 300);
        assertEquals(0.9942388175378764, scoreAfterNormalization, 0.01f);
        Logx("ScoringMachine.calculateScore2(0f, 10f, 0f, 301f, 300): " + scoreAfterNormalization);

        scoreAfterNormalization = LyricMachine.calculateScore2(0f, 10, 0, 300f, 300);
        assertEquals(1.0, scoreAfterNormalization, 0.01f);
        Logx("ScoringMachine.calculateScore2(0f, 10f, 0f, 300f, 300): " + scoreAfterNormalization);

        scoreAfterNormalization = LyricMachine.calculateScore2(0f, 10, 0, 299f, 300);
        assertEquals(0.9942195815041831, scoreAfterNormalization, 0.01f);
        Logx("ScoringMachine.calculateScore2(0f, 10f, 0f, 299f, 300): " + scoreAfterNormalization);

        scoreAfterNormalization = LyricMachine.calculateScore2(0f, 10, 0, 200f, 300);
        assertEquals(0.2980451578310306, scoreAfterNormalization, 0.01f);
        Logx("ScoringMachine.calculateScore2(0f, 10f, 0f, 200f, 300): " + scoreAfterNormalization);

        scoreAfterNormalization = LyricMachine.calculateScore2(0f, 10, 0, 100f, 300);
        assertEquals(0.0, scoreAfterNormalization, 0.01f);
        Logx("ScoringMachine.calculateScore2(0f, 10f, 0f, 100f, 300): " + scoreAfterNormalization);

        scoreAfterNormalization = LyricMachine.calculateScore2(0f, 10, 0, 50f, 300);
        assertEquals(0.0, scoreAfterNormalization, 0.01f);
        Logx("ScoringMachine.calculateScore2(0f, 10f, 0f, 50f, 300): " + scoreAfterNormalization);

        scoreAfterNormalization = LyricMachine.calculateScore2(0f, 10, 0, 1f, 300);
        assertEquals(0.0, scoreAfterNormalization, 0.01f);
        Logx("ScoringMachine.calculateScore2(0f, 10f, 0f, 1f, 300): " + scoreAfterNormalization);
    }
}
