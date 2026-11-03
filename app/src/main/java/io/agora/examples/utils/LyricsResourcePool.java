package io.agora.examples.utils;

import java.util.ArrayList;
import java.util.List;

public class LyricsResourcePool {
    public static class MusicResource {
        public final int index;

        public final long songCode;
        public final String songName;
        MusicResource(int index, long songCode, String songName) {
            this.index = index;
            this.songCode = songCode;
            this.songName = songName;
        }
    }


    public static List<MusicResource> asMusicList() {
        ArrayList<MusicResource> list = new ArrayList<>();

        list.add(new MusicResource(0, 7162848775797850L, "爱情转移"));
        list.add(new MusicResource(1, 7104926139658410L, "说爱你"));
        list.add(new MusicResource(2, 7162848696618210L, "love story"));

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
