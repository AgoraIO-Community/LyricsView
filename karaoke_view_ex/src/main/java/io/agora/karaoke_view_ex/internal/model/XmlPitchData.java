package io.agora.karaoke_view_ex.internal.model;

import java.util.ArrayList;

public class XmlPitchData {
    public XmlPitchData(ArrayList<Float> pitches) {
        this.pitches = pitches;
    }

    public int version;
    public int interval;
    public int reserved;

    public ArrayList<Float> pitches;
}
