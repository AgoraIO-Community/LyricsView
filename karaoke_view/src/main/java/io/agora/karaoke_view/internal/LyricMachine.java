package io.agora.karaoke_view.internal;

import io.agora.karaoke_view.constants.Constants;
import io.agora.karaoke_view.internal.ai.AINative;
import io.agora.karaoke_view.internal.config.Config;
import io.agora.karaoke_view.internal.constants.LyricType;
import io.agora.karaoke_view.internal.model.KrcPitchData;
import io.agora.karaoke_view.internal.model.LyricsLineModel;
import io.agora.karaoke_view.internal.utils.LogUtils;
import io.agora.karaoke_view.model.LyricModel;

/**
 * State/Information of playing/rendering/on-going lyrics
 * <p>
 * Non-ui related, shared by all components
 */
public class LyricMachine {
    private static final String TAG = Constants.TAG + "-ScoringMachine";
    private LyricModel mLyricsModel;
    private final OnLyricListener mListener;
    private long mCurrentProgress = 0;
    private float mMaximumRefPitch = 0;
    private float mMinimumRefPitch = 100;

    private static final int ZERO_PITCH_COUNT_THRESHOLD = 10;

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

        if (model.type == LyricType.XML) {
            for (LyricsLineModel line : model.lines) {
                for (LyricsLineModel.Tone tone : line.tones) {
                    mMinimumRefPitch = Math.min(mMinimumRefPitch, tone.pitch);
                    mMaximumRefPitch = Math.max(mMaximumRefPitch, tone.pitch);
                }
            }
        } else if (model.type == LyricType.KRC) {

            if (model.pitchDataList != null) {
                for (KrcPitchData data : model.pitchDataList) {
                    mMinimumRefPitch = (float) Math.min(mMinimumRefPitch, data.pitch);
                    mMaximumRefPitch = (float) Math.max(mMaximumRefPitch, data.pitch);
                }
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
        if (this.mCurrentProgress >= 0 && progress > 0) {
            long deltaOfUpdate = (int) (progress - this.mCurrentProgress);
            if (deltaOfUpdate > 100 || deltaOfUpdate < 0) {
                // TODO(Hai_Guo) Need to show warning information when this method called not smoothly
                LogUtils.d("setProgress this method called not smoothly: current mDeltaOfUpdate=" + deltaOfUpdate + " and reset to 20ms");
                deltaOfUpdate = 20;
            }
        }

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

        if (Config.USE_AI_ALGORITHM) {
            AINative.reset();
        }
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


    public interface OnLyricListener {
        public void resetUi();

        public void requestRefreshUi();
    }
}
