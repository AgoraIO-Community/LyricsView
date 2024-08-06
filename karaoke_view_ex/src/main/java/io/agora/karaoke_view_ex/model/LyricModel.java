package io.agora.karaoke_view_ex.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.model.PitchData;

public class LyricModel {
    /**
     * 歌词类型
     */
    public LyricType type;
    /**
     * 歌曲名称
     */
    public String name;
    /**
     * 歌星名称
     */
    public String singer;

    /**
     * 歌词行
     */
    public List<LyricsLineModel> lines;

    /**
     * 前奏结束时间, milliseconds
     */
    public long preludeEndPosition;

    /**
     * 歌词总时长, milliseconds
     */
    public long duration;

    /**
     * 是否包含音高信息
     */
    public boolean hasPitch;

    /**
     * 版权句行数
     */
    public int copyrightSentenceLineCount;

    /**
     * 音高文件数据
     */
    public List<PitchData> pitchDataList;

    public LyricModel(LyricType type) {
        this.type = type;
        copyrightSentenceLineCount = 0;
    }

    public LyricModel copy() {
        LyricModel lyricModel = new LyricModel(type);
        lyricModel.name = name;
        lyricModel.singer = singer;
        if (null != lines) {
            lyricModel.lines = new ArrayList<>(lines);
        }
        lyricModel.preludeEndPosition = preludeEndPosition;
        lyricModel.duration = duration;
        lyricModel.hasPitch = hasPitch;
        if (null != pitchDataList) {
            lyricModel.pitchDataList = new ArrayList<>(pitchDataList);
        }
        return lyricModel;
    }

    @NonNull
    @Override
    public String toString() {
        return "LyricModel{" +
                "name='" + name + '\'' +
                ", singer='" + singer + '\'' +
                ", type=" + type +
                ", lines=" + lines +
                ", preludeEndPosition=" + preludeEndPosition +
                ", duration=" + duration +
                ", hasPitch=" + hasPitch +
                ", pitchDataList=" + pitchDataList +
                '}';
    }
}
