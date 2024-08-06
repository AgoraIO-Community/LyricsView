package io.agora.examples.utils;

import java.util.ArrayList;
import java.util.List;

public class LyricsResourcePool {
    public static class MusicResource {
        public final int index;

        public final long songCode;
        public final String songName;
        public final String songId;
        public final int songType;
        public final String mediaType;

        MusicResource(int index, String songId, String songName) {
            this.index = index;
            this.songId = songId;
            this.songName = songName;
            this.songCode = -1;
            this.songType = 0;
            this.mediaType = "";
        }

        MusicResource(int index, String songId, String songName, String mediaType) {
            this.index = index;
            this.songId = songId;
            this.songName = songName;
            this.songCode = -1;
            this.songType = 0;
            this.mediaType = mediaType;
        }

        MusicResource(int index, long songCode, String songName, int songType) {
            this.index = index;
            this.songCode = songCode;
            this.songName = songName;
            this.songId = "";
            this.songType = songType;
            this.mediaType = "";
        }

        MusicResource(int index, long songCode, String songName, String mediaType) {
            this.index = index;
            this.songCode = songCode;
            this.songName = songName;
            this.songId = "";
            this.songType = 0;
            this.mediaType = mediaType;
        }

        MusicResource(int index, long songCode, String songName, int songType, String mediaType) {
            this.index = index;
            this.songCode = songCode;
            this.songName = songName;
            this.songId = "";
            this.songType = songType;
            this.mediaType = mediaType;
        }
    }


    public static List<MusicResource> asMusicList() {
        ArrayList<MusicResource> list = new ArrayList<>();

        list.add(new MusicResource(0, 7162848775797850L, "爱情转移", 4));
        list.add(new MusicResource(1, 7104926139658410L, "说爱你", 4));
        list.add(new MusicResource(2, 7162848696618210L, "love story", 4));
        //海外曲库
        list.add(new MusicResource(3, 6800601815479800L, "趁着你", 4));

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

    public static List<MusicResource> asMusicListEx() {
        ArrayList<MusicResource> list = new ArrayList<>();
        list.add(new MusicResource(0, "40289835", "十年"));
        list.add(new MusicResource(0, "32183724", "你在不在"));
        list.add(new MusicResource(0, "89488966", "在你的身边", "{\"format\":{\"highPart\":1}}"));
        list.add(new MusicResource(0, "310937426", "怀抱"));
        list.add(new MusicResource(0, "542869354", "Masih Mencintainya"));
        list.add(new MusicResource(0, "625281172", "最后一页"));
        list.add(new MusicResource(0, "621162805", "梦回花事了"));
        list.add(new MusicResource(0, "288308118", "还是分开"));
        list.add(new MusicResource(0, "130598261", "火力全开"));
        return list;
    }
}
