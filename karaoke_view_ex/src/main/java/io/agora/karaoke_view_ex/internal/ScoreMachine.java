package io.agora.karaoke_view_ex.internal;

import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.model.LyricsPitchLineModel;
import io.agora.karaoke_view_ex.internal.model.PitchData;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * State/Information of playing/rendering/on-going lyrics
 * <p>
 * Non-ui related, shared by all components
 */
public class ScoreMachine {
    private LyricModel mLyricsModel;
    private final OnScoreListener mListener;
    private long mCurrentPitchProgress = 0;
    private float mMaximumRefPitch = 0;
    private float mMinimumRefPitch = 100;

    private List<LyricsPitchLineModel> mPitchLines;

    public ScoreMachine(OnScoreListener listener) {
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

        if (mLyricsModel.hasPitch) {
            if (model.pitchDataList != null) {
                for (PitchData data : model.pitchDataList) {
                    mMinimumRefPitch = (float) Math.min(mMinimumRefPitch, data.pitch);
                    mMaximumRefPitch = (float) Math.max(mMaximumRefPitch, data.pitch);
                }
                if (null != mLyricsModel.lines) {
                    mPitchLines = new ArrayList<>(mLyricsModel.lines.size());
                    for (LyricsLineModel line : mLyricsModel.lines) {
                        LyricsPitchLineModel lineModel = new LyricsPitchLineModel();
                        long startTime = line.getStartTime();
                        long endTime = line.getEndTime();
                        for (PitchData data : model.pitchDataList) {
                            if (data.startTime >= startTime && data.startTime < endTime) {
                                LyricsPitchLineModel.Pitch pitch = new LyricsPitchLineModel.Pitch();
                                pitch.begin = data.startTime;
                                pitch.end = data.startTime + data.duration;
                                pitch.pitch = (int) data.pitch;
                                lineModel.pitches.add(pitch);
                            }
                        }
                        mPitchLines.add(lineModel);
                    }
                }
            } else {
                if (null != mLyricsModel.lines) {
                    mPitchLines = new ArrayList<>(mLyricsModel.lines.size());
                    for (LyricsLineModel line : mLyricsModel.lines) {
                        LyricsPitchLineModel lineModel = new LyricsPitchLineModel();
                        if (null != line.tones) {
                            for (LyricsLineModel.Tone tone : line.tones) {
                                LyricsPitchLineModel.Pitch pitch = new LyricsPitchLineModel.Pitch();
                                pitch.begin = tone.begin;
                                pitch.end = tone.end;
                                pitch.pitch = (int) tone.pitch;

                                mMinimumRefPitch = (float) Math.min(mMinimumRefPitch, pitch.pitch);
                                mMaximumRefPitch = (float) Math.max(mMaximumRefPitch, pitch.pitch);

                                lineModel.pitches.add(pitch);
                            }
                        }
                        mPitchLines.add(lineModel);
                    }
                }
            }
        }

        LogUtils.d("prepare mMinimumRefPitch:" + mMinimumRefPitch + ",mMaximumRefPitch:" + mMaximumRefPitch);
    }

    public boolean isReady() {
        return mLyricsModel != null;
    }

    public double calculateScoreWithPitch(float speakerPitch, int progressInMs) {
        mCurrentPitchProgress = progressInMs;
        float refPitch = getRefPitch(progressInMs);
        double scoreAfterNormalization = 0;
        if (refPitch != 0) {
            scoreAfterNormalization = Math.abs(speakerPitch - refPitch) < 5 ? 100 : 0;
        }
        return scoreAfterNormalization;
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
        mCurrentPitchProgress = 0;
    }

    public void prepareUi() {
        if (mListener != null) {
            mListener.resetUi();
        }
    }

    public LyricModel getLyricsModel() {
        return this.mLyricsModel;
    }


    public long getCurrentPitchProgress() {
        return mCurrentPitchProgress;
    }

    public float getMinimumRefPitch() {
        return this.mMinimumRefPitch;
    }

    public float getMaximumRefPitch() {
        return this.mMaximumRefPitch;
    }

    public List<LyricsPitchLineModel> getPitchLines() {
        return mPitchLines;
    }

    public boolean hasPitchData() {
        if (null == mLyricsModel) {
            return false;
        }
        return mLyricsModel.hasPitch;
    }

    public long getLineStartTime(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= mLyricsModel.lines.size()) {
            return 0;
        }

        return mLyricsModel.lines.get(lineIndex).getStartTime();
    }

    public float getRefPitch(int progressInMs) {
        if (mLyricsModel.hasPitch) {
            if (null != mLyricsModel.pitchDataList) {
                for (PitchData data : mLyricsModel.pitchDataList) {
                    if (data.startTime <= progressInMs && data.startTime + data.duration >= progressInMs) {
                        return data.pitch;
                    }
                }
            } else {
                if (null != mLyricsModel.lines) {
                    for (LyricsLineModel line : mLyricsModel.lines) {
                        for (LyricsLineModel.Tone tone : line.tones) {
                            if (tone.begin <= progressInMs && tone.end >= progressInMs) {
                                return tone.pitch;
                            }
                        }
                    }
                }
            }
        }
        return 0;
    }


    public interface OnScoreListener {
        void resetUi();

        void requestRefreshUi();
    }
}
