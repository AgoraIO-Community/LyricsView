package io.agora.karaoke_view_ex.internal.model;

import androidx.annotation.NonNull;

public class PitchData {
    public float pitch;
    public int startTime;
    public int duration;

    @NonNull
    @Override
    public String toString() {
        return "PitchData{" +
                "pitch=" + pitch +
                ", startTime=" + startTime +
                ", duration=" + duration +
                '}';
    }
}
