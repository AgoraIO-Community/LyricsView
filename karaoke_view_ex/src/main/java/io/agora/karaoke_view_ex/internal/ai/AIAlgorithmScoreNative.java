package io.agora.karaoke_view_ex.internal.ai;

/**
 * Native interface for AI-based pitch processing and scoring algorithms.
 * This class provides access to native C/C++ implementations of pitch analysis
 * and scoring calculations for karaoke applications.
 */
public class AIAlgorithmScoreNative {
    static {
        System.loadLibrary("agora-karaoke-ai");
    }

    /**
     * Converts a pitch value to a musical tone
     *
     * @param pitch The pitch value to convert
     * @return The corresponding tone value
     */
    public static double pitchToTone(double pitch) {
        return nativePitchToTone(pitch);
    }

    /**
     * Calculates a score based on the comparison between user's voice pitch and standard pitch
     *
     * @param voicePitch              The pitch detected from the user's voice
     * @param stdPitch                The standard/reference pitch from the song
     * @param scoreLevel              The difficulty level for scoring (higher = more lenient)
     * @param scoreCompensationOffset Compensation offset to adjust scoring
     * @return The calculated score value
     */
    public static float calculatedScore(double voicePitch, double stdPitch, int scoreLevel, int scoreCompensationOffset) {
        return nativeCalculatedScore(voicePitch, stdPitch, scoreLevel, scoreCompensationOffset);
    }

    /**
     * Processes and adjusts pitch values for scoring
     *
     * @param stdPitch    The standard/reference pitch from the song
     * @param voicePitch  The pitch detected from the user's voice
     * @param stdMaxPitch The maximum standard pitch in the song
     * @return The processed pitch value
     */
    public static double handlePitch(double stdPitch, double voicePitch, double stdMaxPitch) {
        return nativeHandlePitch(stdPitch, voicePitch, stdMaxPitch);
    }

    /**
     * Resets the internal state of the native scoring algorithm
     */
    public static void reset() {
        nativeReset();
    }

    /**
     * Native method for pitch to tone conversion
     *
     * @param pitch The pitch value to convert
     * @return The corresponding tone value
     */
    private static native double nativePitchToTone(double pitch);

    /**
     * Native method for score calculation
     *
     * @param voicePitch              The pitch detected from the user's voice
     * @param stdPitch                The standard/reference pitch from the song
     * @param scoreLevel              The difficulty level for scoring
     * @param scoreCompensationOffset Compensation offset to adjust scoring
     * @return The calculated score value
     */
    private static native float nativeCalculatedScore(double voicePitch, double stdPitch, int scoreLevel, int scoreCompensationOffset);

    /**
     * Native method for pitch processing
     *
     * @param stdPitch    The standard/reference pitch from the song
     * @param voicePitch  The pitch detected from the user's voice
     * @param stdMaxPitch The maximum standard pitch in the song
     * @return The processed pitch value
     */
    private static native double nativeHandlePitch(double stdPitch, double voicePitch, double stdMaxPitch);

    /**
     * Native method to reset the internal state
     */
    private static native void nativeReset();
}
