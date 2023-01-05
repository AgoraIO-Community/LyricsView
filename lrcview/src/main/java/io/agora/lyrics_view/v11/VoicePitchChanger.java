package io.agora.lyrics_view.v11;

public class VoicePitchChanger {

    double offset = 0.0F;
    int n = 0;

    /// 处理 Pitch
    /// - Parameters:
    ///   - wordPitch: 标准值 来自歌词文件
    ///   - voicePitch: 实际值 来自 rtc 回调
    ///   - wordMaxPitch: 最大值 来自标准值
    /// - Returns: 处理后的值
    double handlePitch(double wordPitch,
                       double voicePitch,
                       double wordMaxPitch) {
        if (voicePitch <= 0) {
            return 0;
        }

        n += 1;
        double gap = wordPitch - voicePitch;

        offset = offset * (n - 1) / n + gap / n;

        if (offset < 0) {
            offset = Math.max(offset, -1 * wordMaxPitch * 0.4);
        } else {
            offset = Math.min(offset, wordMaxPitch * 0.4);
        }

        return Math.min(voicePitch + offset, wordMaxPitch);
    }

    void reset() {
        offset = 0.0;
        n = 0;
    }
}
