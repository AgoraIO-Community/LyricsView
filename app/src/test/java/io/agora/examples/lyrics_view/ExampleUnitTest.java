package io.agora.examples.lyrics_view;

import org.junit.Test;

import static org.junit.Assert.*;

import io.agora.lyrics_view.PitchView;
import io.agora.lyrics_view.VoicePitchChanger;

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
    public void testVoiceChanger() {
        System.out.println(TAG + " handlePitch(300, 189, 500): " + mVoicePitchChanger.handlePitch(300, 189, 500));
        System.out.println(TAG + " handlePitch(310, 176, 500): " + mVoicePitchChanger.handlePitch(310, 176, 500));
        System.out.println(TAG + " handlePitch(320, 137, 500): " + mVoicePitchChanger.handlePitch(320, 137, 500));
        System.out.println(TAG + " handlePitch(390, 120, 500): " + mVoicePitchChanger.handlePitch(390, 120, 500));
        System.out.println(TAG + " handlePitch(430, 210, 500): " + mVoicePitchChanger.handlePitch(430, 210, 500));
        System.out.println(TAG + " handlePitch(460, 234, 500): " + mVoicePitchChanger.handlePitch(460, 234, 500));
        System.out.println(TAG + " handlePitch(490, 199, 500): " + mVoicePitchChanger.handlePitch(490, 199, 500));
        System.out.println(TAG + " handlePitch(300, 300, 500): " + mVoicePitchChanger.handlePitch(300, 300, 500));
    }

    private VoicePitchChanger mVoicePitchChanger = new VoicePitchChanger();

    public double calculateScore2(float level, float offset, double tone, double tone_ref,
                                  int scorePerSentence, int minimumScorePerTone) {
        double scoreAfterNormalization; // [0, 1]

        double score = 1 - (level * Math.abs(tone - tone_ref)) / 100 + offset / 100;

        // 得分线以下的分数归零
        score = score >= minimumScorePerTone ? score : 0f;
        // 得分太大的置一
        score = score > 1 ? 1 : score;

        scoreAfterNormalization = score;
        // 百分制分数 * 每句固定分数
        score *= scorePerSentence;
        return scoreAfterNormalization;
    }

    private double pitchToScore(double refPitch, double minPitch, double maxPitch, double pitch,
                                int level, int offset, int scorePerSentence) {

        if (pitch == 0 || pitch < minPitch || pitch > maxPitch) {
            return 0;
        }

        if (refPitch == 0) {
            return 0;
        }

        double scoreAfterNormalization = calculateScore2(level, offset,
                PitchView.pitchToTone(pitch), PitchView.pitchToTone(refPitch), scorePerSentence, 0);
        return scoreAfterNormalization;
    }

    @Test
    public void testPitchToScore() {
        System.out.println(TAG + " pitchToScore(300, 0, 500, 500, 10, 0, 100): "
                + pitchToScore(300, 0, 500, 500, 10, 0, 100));
        System.out.println(TAG + " pitchToScore(300, 0, 500, 400, 10, 0, 100): "
                + pitchToScore(300, 0, 500, 400, 10, 0, 100));
        System.out.println(TAG + " pitchToScore(300, 0, 500, 301, 10, 0, 100): "
                + pitchToScore(300, 0, 500, 301, 10, 0, 100));
        System.out.println(TAG + " pitchToScore(300, 0, 500, 300, 10, 0, 100): "
                + pitchToScore(300, 0, 500, 300, 10, 0, 100));
        System.out.println(TAG + " pitchToScore(300, 0, 500, 299, 10, 0, 100): "
                + pitchToScore(300, 0, 500, 299, 10, 0, 100));
        System.out.println(TAG + " pitchToScore(300, 0, 500, 200, 10, 0, 100): "
                + pitchToScore(300, 0, 500, 200, 10, 0, 100));
        System.out.println(TAG + " pitchToScore(300, 0, 500, 100, 10, 0, 100): "
                + pitchToScore(300, 0, 500, 100, 10, 0, 100));
        System.out.println(TAG + " pitchToScore(300, 0, 500, 50, 10, 0, 100): "
                + pitchToScore(300, 0, 500, 50, 10, 0, 100));
        System.out.println(TAG + " pitchToScore(300, 0, 500, 1, 10, 0, 100): "
                + pitchToScore(300, 0, 500, 1, 10, 0, 100));
    }
}