package io.agora.karaoke_view.v11;

import io.agora.karaoke_view.v11.internal.ScoringMachine;

public class VoicePitchChanger {

    double offset = 0.0F;
    int n = 0;

    /// 处理 Pitch
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

        if (Math.abs(ScoringMachine.pitchToTone(pitch) - ScoringMachine.pitchToTone(refPitch)) < 0.5) { /** tone 差距过小，直接返回 **/
            return pitch;
        }

        return Math.min(pitch + offset, refMaxPitch);
    }

    void reset() {
        offset = 0.0;
        n = 0;
    }
}
