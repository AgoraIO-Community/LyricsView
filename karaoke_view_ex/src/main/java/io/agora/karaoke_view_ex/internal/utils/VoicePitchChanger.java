package io.agora.karaoke_view_ex.internal.utils;

public class VoicePitchChanger {

    double offset = 0.0F;
    int n = 0;

    /// 处理 Pitch(该方法不是幂等的，使用时候要特别注意)
    /// - Parameters:
    ///   - refPitch: 标准值 来自歌词文件
    ///   - pitch: 实际值 来自 rtc 回调
    ///   - refMaxPitch: 最大值 来自标准值
    /// - Returns: 处理后的值
    public double handlePitch(double refPitch,
                              double pitch,
                              double refMaxPitch) {
        if (pitch <= 0 || refPitch <= 0) {
            return 0;
        }

        n += 1;
        double gap = refPitch - pitch;

        offset = offset * (n - 1) / n + gap / n;

        if (offset < 0) {
            offset = Math.max(offset, -1 * refMaxPitch * 0.4);
        } else {
            offset = Math.min(offset, refMaxPitch * 0.4);
        }

        // 这个算法问题
        // 1) 第一个 pitch 的时候直接返回 refPitch，但这在默认整首歌当中都只有一次。
        // 是否需要每句歌词都应用这样的逻辑(也就是累积效应只在每一句当中)。
        // 2) 看看是否要增加 abs(pitch - refPitch) / maxPitch <= 0.2f 的时候，可以直接返回 pitch
        if (Math.abs(gap) < 1) { // The chance would be not much, try to apply `gap / wordMaxPitch <= 0.2f` if necessary
            return Math.min(pitch, refMaxPitch);
        }

        switch (n) {
            case 1:
                return Math.min(pitch + 0.5 * offset, refMaxPitch);
            case 2:
                return Math.min(pitch + 0.6 * offset, refMaxPitch);
            case 3:
                return Math.min(pitch + 0.7 * offset, refMaxPitch);
            case 4:
                return Math.min(pitch + 0.8 * offset, refMaxPitch);
            case 5:
                return Math.min(pitch + 0.9 * offset, refMaxPitch);
            default:
                return Math.min(pitch + offset, refMaxPitch);
        }
    }

    void reset() {
        offset = 0.0;
        n = 0;
    }
}
