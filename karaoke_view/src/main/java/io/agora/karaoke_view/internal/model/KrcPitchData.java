package io.agora.karaoke_view.internal.model;

import androidx.annotation.NonNull;

public class KrcPitchData {
    public float pitch;
    public int startTime;
    public int duration;

    @NonNull
    @Override
    public String toString() {
        return "KrcPitchData{" +
                "pitch=" + pitch +
                ", startTime=" + startTime +
                ", duration=" + duration +
                '}';
    }
}
