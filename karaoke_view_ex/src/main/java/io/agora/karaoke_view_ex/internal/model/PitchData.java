package io.agora.karaoke_view_ex.internal.model;

import androidx.annotation.NonNull;

/**
 * Model class representing pitch information for karaoke scoring.
 * Contains data about pitch value, start time, and duration.
 */
public class PitchData {
    /**
     * The pitch value (frequency)
     */
    public float pitch;

    /**
     * Start time of this pitch segment in milliseconds
     */
    public int startTime;

    /**
     * Duration of this pitch segment in milliseconds
     */
    public int duration;

    /**
     * Returns a string representation of the pitch data
     *
     * @return String containing all pitch data properties
     */
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
