package io.agora.karaoke_view_ex.internal.utils;

/**
 * Utility class for adjusting voice pitch in karaoke applications.
 * Provides functionality to handle pitch adjustments based on reference values.
 */
public class VoicePitchChanger {

    /**
     * The accumulated pitch offset
     */
    private double mOffset = 0.0F;

    /**
     * Counter for the number of pitch adjustments made
     */
    private int mN = 0;

    /**
     * Processes and adjusts the pitch value based on reference values.
     * This method is not idempotent - special care should be taken when using it.
     *
     * @param refPitch    Standard pitch value from the lyrics file
     * @param pitch       Actual pitch value from RTC callback
     * @param refMaxPitch Maximum reference pitch value
     * @return The processed pitch value
     */
    public double handlePitch(double refPitch,
                              double pitch,
                              double refMaxPitch) {
        if (pitch <= 0 || refPitch <= 0) {
            return 0;
        }

        mN += 1;
        double gap = refPitch - pitch;

        mOffset = mOffset * (mN - 1) / mN + gap / mN;

        if (mOffset < 0) {
            mOffset = Math.max(mOffset, -1 * refMaxPitch * 0.4);
        } else {
            mOffset = Math.min(mOffset, refMaxPitch * 0.4);
        }

        // 这个算法问题
        // 1) 第一个 pitch 的时候直接返回 refPitch，但这在默认整首歌当中都只有一次。
        // 是否需要每句歌词都应用这样的逻辑(也就是累积效应只在每一句当中)。
        // 2) 看看是否要增加 abs(pitch - refPitch) / maxPitch <= 0.2f 的时候，可以直接返回 pitch
        if (Math.abs(gap) < 1) { // The chance would be not much, try to apply `gap / wordMaxPitch <= 0.2f` if necessary
            return Math.min(pitch, refMaxPitch);
        }

        switch (mN) {
            case 1:
                return Math.min(pitch + 0.5 * mOffset, refMaxPitch);
            case 2:
                return Math.min(pitch + 0.6 * mOffset, refMaxPitch);
            case 3:
                return Math.min(pitch + 0.7 * mOffset, refMaxPitch);
            case 4:
                return Math.min(pitch + 0.8 * mOffset, refMaxPitch);
            case 5:
                return Math.min(pitch + 0.9 * mOffset, refMaxPitch);
            default:
                return Math.min(pitch + mOffset, refMaxPitch);
        }
    }

    /**
     * Resets the pitch changer state.
     * Clears the accumulated offset and counter.
     */
    void reset() {
        mOffset = 0.0;
        mN = 0;
    }
}
