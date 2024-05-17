package io.agora.karaoke_view_ex.internal;

import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.internal.config.Config;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.KrcPitchData;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.model.LyricsPitchLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * State/Information of playing/rendering/on-going lyrics
 * <p>
 * Non-ui related, shared by all components
 */
public class LyricMachine {
    private LyricModel mLyricsModel;
    private final OnLyricListener mListener;
    private long mCurrentProgress = 0;
    private float mMaximumRefPitch = 0;
    private float mMinimumRefPitch = 100;

    private List<LyricsPitchLineModel> mShowLyricsLines;

    public LyricMachine(OnLyricListener listener) {
        reset();
        this.mListener = listener;
    }

    private static final LyricModel EMPTY_LYRICS_MODEL = new LyricModel(LyricType.LRC);

    // Set data and prepare to rendering
    public void prepare(LyricModel model) {
        reset();

        if (model == null || model.lines == null || model.lines.isEmpty()) {
            LogUtils.e("Invalid lyrics model, use built-in EMPTY_LYRICS_MODEL");
            mLyricsModel = EMPTY_LYRICS_MODEL;
            return;
        }

        mLyricsModel = model;

        if (model.type == LyricType.KRC) {
            if (model.pitchDataList != null) {
                for (KrcPitchData data : model.pitchDataList) {
                    mMinimumRefPitch = (float) Math.min(mMinimumRefPitch, data.pitch);
                    mMaximumRefPitch = (float) Math.max(mMaximumRefPitch, data.pitch);
                }
            }

            mShowLyricsLines = new ArrayList<>(mLyricsModel.lines.size());
            for (LyricsLineModel line : mLyricsModel.lines) {
                LyricsPitchLineModel lineModel = new LyricsPitchLineModel();
                long startTime = line.getStartTime();
                long endTime = line.getEndTime();
                for (KrcPitchData data : model.pitchDataList) {
                    if (data.startTime >= startTime && data.startTime < endTime) {
                        LyricsPitchLineModel.Pitch pitch = new LyricsPitchLineModel.Pitch();
                        pitch.begin = data.startTime;
                        pitch.end = data.startTime + data.duration;
                        pitch.pitch = (int) data.pitch;
                        lineModel.pitches.add(pitch);
                    }
                }
                mShowLyricsLines.add(lineModel);
            }
        }


        LogUtils.d("prepare mMinimumRefPitch:" + mMinimumRefPitch + ",mMaximumRefPitch:" + mMaximumRefPitch);
    }

    public boolean isReady() {
        return mLyricsModel != null;
    }


    /**
     * 更新歌词进度，单位毫秒
     *
     * @param progress 当前播放时间，毫秒
     *                 progress 一定要大于歌词结束时间才可以触发最后一句的回调
     */
    public void setProgress(long progress) {
        if (progress <= 0L) {
            resetStats();
            if (mListener != null) {
                mListener.resetUi();
            }
        }

        this.mCurrentProgress = progress;

        if (mLyricsModel == null) {
            if (mListener != null) {
                mListener.resetUi();
            }
            return;
        }

        if (mListener != null) {
            mListener.requestRefreshUi();
        }
    }

    public void setPitch(float speakerPitch, int progressInMs) {
        if (mLyricsModel == null) {
            if (mListener != null) {
                mListener.resetUi();
            }
            return;
        }

        mCurrentProgress = progressInMs;
        if (Config.DEBUG) {
            LogUtils.d("setPitch speakerPitch:" + speakerPitch + ",progressInMs:" + progressInMs);
        }
    }


    public void whenDraggingHappen(long progress) {
        minorReset();

        for (int index = 0; index < mLyricsModel.lines.size(); index++) {
            LyricsLineModel line = mLyricsModel.lines.get(index);
            for (int toneIndex = 0; toneIndex < line.tones.size(); toneIndex++) {
                LyricsLineModel.Tone tone = line.tones.get(toneIndex);
                tone.resetHighlight();
            }
        }

        mCurrentProgress = progress;
    }

    public void reset() {
        resetProperties();

        resetStats();
    }

    private void resetProperties() { // Reset when song changed
        mLyricsModel = null;
        mMinimumRefPitch = 100;
        mMaximumRefPitch = 0;
    }

    private void resetStats() {
        minorReset();
    }

    private void minorReset() { // Will recover immediately
        mCurrentProgress = 0;
    }

    public void prepareUi() {
        if (mListener != null) {
            mListener.resetUi();
        }
    }

    public LyricModel getLyricsModel() {
        return this.mLyricsModel;
    }

    public long getCurrentProgress() {
        return mCurrentProgress;
    }

    public float getMinimumRefPitch() {
        return this.mMinimumRefPitch;
    }

    public float getMaximumRefPitch() {
        return this.mMaximumRefPitch;
    }

    public List<LyricsPitchLineModel> getShowLyricsLines() {
        return mShowLyricsLines;
    }

    public float getRefPitch(int progressInMs) {
        if (mLyricsModel.type == LyricType.KRC) {
            if (null != mLyricsModel.pitchDataList) {
                for (KrcPitchData data : mLyricsModel.pitchDataList) {
                    if (data.startTime <= progressInMs && data.startTime + data.duration >= progressInMs) {
                        return data.pitch;
                    }
                }
            }
        } else {
            for (LyricsLineModel line : mLyricsModel.lines) {
                for (LyricsLineModel.Tone tone : line.tones) {
                    if (tone.begin <= progressInMs && tone.end >= progressInMs) {
                        return tone.pitch;
                    }
                }
            }
        }
        return 0;
    }


    public interface OnLyricListener {
        public void resetUi();

        public void requestRefreshUi();
    }
}
