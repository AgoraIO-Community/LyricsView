package io.agora.examples.karaoke_view;

import java.util.ArrayList;
import java.util.List;

public class LyricsResourcePool {

    protected static class LyricsResource {
        public final int index;

        public final String uri;

        public final String description;

        LyricsResource(int index, String uri, String description) {
            this.index = index;
            this.uri = uri;
            this.description = description;
        }
    }

    public static final String LRC_SAMPLE_1 = "https://webdemo.agora.io/ktv/chocolateice.xml"; // Deprecated

    /**
     * <sentence>
     *   <tone begin="153.67" end="153.951" pitch="" pronounce="" lang="1">
     *     <word>独</word>
     *   </tone>
     *   <tone begin="153.951" end="154.55" pitch="" pronounce="" lang="1">
     *     <word>自</word>
     *   </tone>
     *   <tone begin="154.55" end="154.947" pitch="" pronounce="" lang="1">
     *     <word>泪</word>
     *   </tone>
     *   <tone begin="154.947" end="155.687" pitch="" pronounce="" lang="1">
     *     <word>流</word>
     *   </tone>
     *   <tone begin="155.687" end="155.687" pitch="" pronounce="" lang="1"/>
     *   <tone begin="155.687" end="156.1" pitch="" pronounce="" lang="1">
     *     <word>独</word>
     *   </tone>
     *   <tone begin="156.1" end="156.551" pitch="" pronounce="" lang="1">
     *     <word>自</word>
     *   </tone>
     *   <tone begin="156.551" end="157.125" pitch="" pronounce="" lang="1">
     *     <word>忍</word>
     *   </tone>
     *   <tone begin="157.125" end="157.688" pitch="" pronounce="" lang="1">
     *     <word>受</word>
     *   </tone>
     * </sentence>
     */
    public static final String LRC_SAMPLE_2 = "https://webdemo.agora.io/ktv/001.xml"; // Empty pitch value/word missing
    public static final String LRC_SAMPLE_3 = "https://solutions-apaas.agora.io/rte-ktv/0609f0627e114a669008d26e312f7613.zip"; // Zip packaged xml, start and end(previous line) may have same timestamp
    public static final String LRC_SAMPLE_4 = "https://d1n8x1oristvw.cloudfront.net/song_resource/20220705/7b95e6e99afb4d099bca10cc5e3f74a0.xml"; // Empty pitch value/empty title/empty artist
    public static final String LRC_SAMPLE_5 = "https://accktv.sd-rtn.com/202211021744/7191509b5d335b3956debbf1b06056dc/release/lyric/zip_utf8/1/0f7d7e5dd1ab4d84927b6aa78ce69fd6.zip"; // File 430 forbidden
    public static final String LRC_SAMPLE_6 = "https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/meta/demo/fulldemoStatic/privacy/loadFil.xml"; // Non-perfect xml format
    public static final String LRC_SAMPLE_7 = "https://accktv.sd-rtn.com/202211091649/release/lyric/zip_utf8/3/901174.zip"; // File not available

    public static final String LRC_SAMPLE_LOCAL_FILE_810507 = "810507.xml"; // Located under assets folder, one and only one line xml file

    public static final String LRC_SAMPLE_LOCAL_FILE_825003 = "825003.xml"; // Start and end(previous line) may have same timestamp

    public static final String LRC_SAMPLE_LOCAL_FILE_147383 = "147383.xml"; // Start and end(previous line) may overlap

    public static final String LRC_SAMPLE_LOCAL_FILE_151675 = "151675.xml";

    public static final String LRC_SAMPLE_LOCAL_FILE_141466 = "141466.xml";

    public static final String LRC_SAMPLE_LOCAL_FILE_140778 = "140778.xml";

    public static final String LRC_SAMPLE_LOCAL_FILE_140017 = "140017.xml";

    public static final String LRC_SAMPLE_LOCAL_FILE_108700 = "108700.xml";

    public static final String LRC_SAMPLE_LOCAL_FILE_793566 = "793566.xml";

    public static final String LRC_SAMPLE_LOCAL_FILE_227732_2_INVALID = "237732-invalid-content-2.xml";

    public static final List<LyricsResource> asList() {
        ArrayList<LyricsResource> list = new ArrayList<>();
        list.add(new LyricsResource(0, LRC_SAMPLE_1, "DEPRECATED"));
        list.add(new LyricsResource(0, LRC_SAMPLE_2, "EMPTY_PITCH/WORD_MISSING"));
        list.add(new LyricsResource(0, LRC_SAMPLE_3, "ZIP/TS_PITCH_SAME"));
        list.add(new LyricsResource(0, LRC_SAMPLE_4, "EMPTY_PITCH/EMPTY_TITLE/EMPTY_ARTIST"));
        list.add(new LyricsResource(0, LRC_SAMPLE_5, "403/INVALID_REQUEST"));
        list.add(new LyricsResource(0, LRC_SAMPLE_6, "NON_PERFECT_XML"));
        list.add(new LyricsResource(0, LRC_SAMPLE_7, "404/NOT_FOUND"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_810507, "ONE_LINE"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_825003, "TS_PITCH_SAME"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_147383, "TS_PITCH_OVERLAP"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_151675, "PERFECT"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_793566, "PERFECT"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_227732_2_INVALID, "Unexpected Content"));
        return list;
    }
}
