package io.agora.karaoke_view_ex.internal.model;

import java.util.ArrayList;

/**
 * Model class representing pitch data in XML format.
 * Contains version information, timing interval, and a list of pitch values.
 */
public class XmlPitchData {
    /**
     * Constructor for XmlPitchData
     *
     * @param pitches List of pitch values
     */
    public XmlPitchData(ArrayList<Float> pitches) {
        this.pitches = pitches;
    }

    /**
     * Version number of the pitch data format
     */
    public int version;

    /**
     * Time interval between pitch values in milliseconds
     */
    public int interval;

    /**
     * Reserved field for future use
     */
    public int reserved;

    /**
     * List of pitch values
     */
    public ArrayList<Float> pitches;
}
