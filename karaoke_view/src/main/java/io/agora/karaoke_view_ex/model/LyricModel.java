package io.agora.karaoke_view_ex.model;

import java.util.List;

import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.KrcPitchData;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;

public class LyricModel {
    //歌曲名称
    public String name;
    //歌星名称
    public String singer;
    //歌词类型
    public LyricType type;
    //歌词行
    public List<LyricsLineModel> lines;
    //前奏结束时间, milliseconds
    public long preludeEndPosition;
    //歌词总时长,milliseconds
    public long duration;
    public boolean hasPitch;
    public List<KrcPitchData> pitchDataList;


    public LyricModel(LyricType type) {
        this.type = type;
    }
}
