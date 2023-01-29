package io.agora.examples.karaoke_view;

import java.util.ArrayList;
import java.util.List;

public class LyricsResourcePool {
    public static final String LRC_SAMPLE_1 = "https://webdemo.agora.io/ktv/chocolateice.xml";
    public static final String LRC_SAMPLE_2 = "https://webdemo.agora.io/ktv/001.xml";
    public static final String LRC_SAMPLE_3 = "https://solutions-apaas.agora.io/rte-ktv/0609f0627e114a669008d26e312f7613.zip";
    public static final String LRC_SAMPLE_4 = "https://d1n8x1oristvw.cloudfront.net/song_resource/20220705/7b95e6e99afb4d099bca10cc5e3f74a0.xml"; // Empty pitch value
    public static final String LRC_SAMPLE_5 = "https://accktv.sd-rtn.com/202211021744/7191509b5d335b3956debbf1b06056dc/release/lyric/zip_utf8/1/0f7d7e5dd1ab4d84927b6aa78ce69fd6.zip"; // File 430 forbidden
    public static final String LRC_SAMPLE_6 = "https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/meta/demo/fulldemoStatic/privacy/loadFil.xml"; // Non-perfect xml format
    public static final String LRC_SAMPLE_7 = "https://accktv.sd-rtn.com/202211091649/release/lyric/zip_utf8/3/901174.zip"; // File not available

    public static final String LRC_SAMPLE_LOCAL_FILE_810507 = "810507.xml"; // Located under assets folder, one and only one line xml file

    public static final String LRC_SAMPLE_LOCAL_FILE_825003 = "825003.xml"; // Start and end(previous line) may have same timestamp

    public static final String LRC_SAMPLE_LOCAL_FILE_147383 = "147383.xml"; // Start and end(previous line) may overlap

    public static final List<String> asList() {
        ArrayList<String> list = new ArrayList<>();
        list.add(LRC_SAMPLE_1);
        list.add(LRC_SAMPLE_2);
        list.add(LRC_SAMPLE_3);
        list.add(LRC_SAMPLE_4);
        list.add(LRC_SAMPLE_5);
        list.add(LRC_SAMPLE_6);
        list.add(LRC_SAMPLE_7);
        list.add(LRC_SAMPLE_LOCAL_FILE_810507);
        list.add(LRC_SAMPLE_LOCAL_FILE_825003);
        list.add(LRC_SAMPLE_LOCAL_FILE_147383);
        return list;
    }
}
