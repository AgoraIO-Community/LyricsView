package io.agora.karaoke_view.v11.internal;

import java.util.ArrayList;

public class PitchesModel {
    public PitchesModel(ArrayList<Double> pitches) {
        this.pitches = pitches;
    }

    public int version;
    public int interval;
    public int reserved;

    public ArrayList<Double> pitches;
}
