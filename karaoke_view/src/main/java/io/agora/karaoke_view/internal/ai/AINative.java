package io.agora.karaoke_view.internal.ai;

public class AINative {
    static {
        System.loadLibrary("agora-karaoke-ai");
    }

    public static double pitchToTone(double pitch) {
        return nativePitchToTone(pitch);
    }

    public static float calculatedScore(double voicePitch, double stdPitch, int scoreLevel, int scoreCompensationOffset) {
        return nativeCalculatedScore(voicePitch, stdPitch, scoreLevel, scoreCompensationOffset);
    }

    public static double handlePitch(double stdPitch, double voicePitch, double stdMaxPitch) {
        return nativeHandlePitch(stdPitch, voicePitch, stdMaxPitch);
    }

    public static void reset() {
        nativeReset();
    }

    private static native double nativePitchToTone(double pitch);

    private static native float nativeCalculatedScore(double voicePitch, double stdPitch, int scoreLevel, int scoreCompensationOffset);


    private static native double nativeHandlePitch(double stdPitch, double voicePitch, double stdMaxPitch);

    private static native void nativeReset();

}
