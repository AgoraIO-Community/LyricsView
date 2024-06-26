package io.agora.examples.karaoke_view;

import java.util.ArrayList;
import java.util.List;

public class LyricsResourcePool {

    protected static class LyricsResource {
        public final int index;

        public final String lyrics;

        public final String pitches;

        public final String description;

        LyricsResource(int index, String lyrics, String pitches, String description) {
            this.index = index;
            this.lyrics = lyrics;
            this.pitches = pitches;
            this.description = description;
        }
    }

    protected static class MusicResource {
        public final int index;

        public final long songCode;
        public final String songName;

        MusicResource(int index, long songCode, String songName) {
            this.index = index;
            this.songCode = songCode;
            this.songName = songName;
        }
    }

    public static final String LRC_SAMPLE_1 = "https://webdemo.agora.io/ktv/chocolateice.xml"; // Deprecated

    /**
     * <sentence>
     * <tone begin="153.67" end="153.951" pitch="" pronounce="" lang="1">
     * <word>独</word>
     * </tone>
     * <tone begin="153.951" end="154.55" pitch="" pronounce="" lang="1">
     * <word>自</word>
     * </tone>
     * <tone begin="154.55" end="154.947" pitch="" pronounce="" lang="1">
     * <word>泪</word>
     * </tone>
     * <tone begin="154.947" end="155.687" pitch="" pronounce="" lang="1">
     * <word>流</word>
     * </tone>
     * <tone begin="155.687" end="155.687" pitch="" pronounce="" lang="1"/>
     * <tone begin="155.687" end="156.1" pitch="" pronounce="" lang="1">
     * <word>独</word>
     * </tone>
     * <tone begin="156.1" end="156.551" pitch="" pronounce="" lang="1">
     * <word>自</word>
     * </tone>
     * <tone begin="156.551" end="157.125" pitch="" pronounce="" lang="1">
     * <word>忍</word>
     * </tone>
     * <tone begin="157.125" end="157.688" pitch="" pronounce="" lang="1">
     * <word>受</word>
     * </tone>
     * </sentence>
     */
    public static final String LRC_SAMPLE_2 = "https://webdemo.agora.io/ktv/001.xml"; // Empty pitch value/word missing
    public static final String LRC_SAMPLE_3 = "https://solutions-apaas.agora.io/rte-ktv/0609f0627e114a669008d26e312f7613.zip"; // Zip packaged xml, start and end(previous line) may have same timestamp
    public static final String LRC_SAMPLE_4 = "https://d1n8x1oristvw.cloudfront.net/song_resource/20220705/7b95e6e99afb4d099bca10cc5e3f74a0.xml"; // Empty pitch value/empty title/empty artist
    public static final String LRC_SAMPLE_5 = "https://accktv.sd-rtn.com/202211021744/7191509b5d335b3956debbf1b06056dc/release/lyric/zip_utf8/1/0f7d7e5dd1ab4d84927b6aa78ce69fd6.zip"; // File 430 forbidden
    public static final String LRC_SAMPLE_6 = "https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/meta/demo/fulldemoStatic/privacy/loadFil.xml"; // Non-perfect xml format
    public static final String LRC_SAMPLE_7 = "https://accktv.sd-rtn.com/202211091649/release/lyric/zip_utf8/3/901174.zip"; // File not available
    public static final String LRC_SAMPLE_8 = "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/8.lrc"; // File not available

    public static final String LRC_SAMPLE_LOCAL_FILE_CJT44552 = "CJt1320044552.xml";

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

    public static final String LRC_SAMPLE_LOCAL_FILE_LRC_6246262727282260 = "6246262727282260.lrc";
    public static final String LRC_SAMPLE_LOCAL_FILE_PITCH_6246262727282260 = "6246262727282260.bin";

    public static List<LyricsResource> asList() {
        ArrayList<LyricsResource> list = new ArrayList<>();
        list.add(new LyricsResource(0, LRC_SAMPLE_1, null, "DEPRECATED"));
        list.add(new LyricsResource(0, LRC_SAMPLE_2, null, "EMPTY_PITCH/WORD_MISSING"));
        list.add(new LyricsResource(0, LRC_SAMPLE_3, null, "ZIP/TS_PITCH_SAME"));
        list.add(new LyricsResource(0, LRC_SAMPLE_4, null, "EMPTY_PITCH/EMPTY_TITLE/EMPTY_ARTIST"));
        list.add(new LyricsResource(0, LRC_SAMPLE_5, null, "403/INVALID_REQUEST/SHOW_NO_LYRICS_TIPS"));
        list.add(new LyricsResource(0, LRC_SAMPLE_6, null, "NON_PERFECT_XML"));
        list.add(new LyricsResource(0, LRC_SAMPLE_7, null, "404/NOT_FOUND"));
        list.add(new LyricsResource(0, LRC_SAMPLE_8, null, "LRC_FILE"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_CJT44552, null, "FAST"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_810507, null, "ONE_LINE"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_825003, null, "TS_PITCH_SAME"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_147383, null, "TS_PITCH_OVERLAP"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_151675, null, "PERFECT"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_793566, null, "PERFECT"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_227732_2_INVALID, null, "Unexpected Content"));
        list.add(new LyricsResource(0, LRC_SAMPLE_LOCAL_FILE_LRC_6246262727282260, LRC_SAMPLE_LOCAL_FILE_PITCH_6246262727282260, "Lrc file with extra standalone pitches"));
        return list;
    }

    public static List<MusicResource> asMusicList() {
        ArrayList<MusicResource> list = new ArrayList<>();
        list.add(new MusicResource(0, 6246262727282860L, "爱情转移"));
        list.add(new MusicResource(1, 6654550221757560L, "说爱你"));
        list.add(new MusicResource(2, 6246262727300580L, "江南"));
        list.add(new MusicResource(3, 6625526608670440L, "容易受伤的女人"));
        list.add(new MusicResource(4, 6625526618861450L, "卖汤圆"));

        //test domain mccDomain = "api-test.agora.io";
//        list.add(new MusicResource(0, 6625526604952630L, "日不落"));
//        list.add(new MusicResource(1, 6654550250051940L, "稻香"));
//        list.add(new MusicResource(2, 6625526606517650L, "荷塘月色"));
//        list.add(new MusicResource(3, 6625526608670440L, "容易受伤的女人"));
//        list.add(new MusicResource(4, 6625526618861450L, "卖汤圆"));
//        list.add(new MusicResource(5, 6625526619767100L, "海盗"));
//        list.add(new MusicResource(6, 6654550256811200L, "魔鬼中的天使"));
//        list.add(new MusicResource(7, 6625526603907880L, "痴心绝对"));
//        list.add(new MusicResource(8, 6654550242185930L, "好久不见"));
//        list.add(new MusicResource(9, 6625526603433040L, "遇见"));
//        list.add(new MusicResource(10, 6654550244516420L, "迷宫"));
//        list.add(new MusicResource(11, 6625526603472520L, "倒带"));
//        list.add(new MusicResource(12, 6625526603742770L, "说爱你"));
//        list.add(new MusicResource(13, 6625526639142490L, "如果这都不算爱"));

        return list;
    }
}
