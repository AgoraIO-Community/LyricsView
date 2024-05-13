package io.agora.karaoke_view.internal.model;

import java.util.ArrayList;

public class LrcPitchData {
    public LrcPitchData(ArrayList<Float> pitches) {
        this.pitches = pitches;
    }

    public int version;
    public int interval;
    public int reserved;

    public ArrayList<Float> pitches;
}
