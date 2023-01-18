package io.agora.lyrics_view;

public class VoicePitchChanger {

    double offset = 0.0F;
    int n = 0;

    /// 处理 Pitch
    /// - Parameters:
    ///   - wordPitch: 标准值 来自歌词文件
    ///   - voicePitch: 实际值 来自 rtc 回调
    ///   - wordMaxPitch: 最大值 来自标准值
    /// - Returns: 处理后的值
    public double handlePitch(double wordPitch,
                       double voicePitch,
                       double wordMaxPitch) {
        if (voicePitch <= 0) {
            return 0;
        }

        n += 1;
        double gap = wordPitch - voicePitch;

        // 这个算法问题
        // 1) 第一个 pitch 的时候直接返回 refPitch，但这在默认整首歌当中都只有一次。
        // 是否需要每句歌词都应用这样的逻辑(也就是累积效应只在每一句当中)。
        // 2) 看看是否要增加 abs(pitch - refPitch) / maxPitch <= 0.2f 的时候，可以直接返回 pitch
        if (Math.abs(gap) < 1) { // The chance would be not much, try to apply `gap / wordMaxPitch <= 0.2f` if necessary
            return Math.min(voicePitch, wordMaxPitch);
        }

        offset = offset * (n - 1) / n + gap / n;

        if (offset < 0) {
            offset = Math.max(offset, -1 * wordMaxPitch * 0.4);
        } else {
            offset = Math.min(offset, wordMaxPitch * 0.4);
        }

        return Math.min(voicePitch + offset, wordMaxPitch);
    }

    public void reset() {
        offset = 0.0;
        n = 0;
    }
}
