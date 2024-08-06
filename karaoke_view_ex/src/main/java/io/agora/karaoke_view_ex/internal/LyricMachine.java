package io.agora.karaoke_view_ex.internal;

import io.agora.karaoke_view_ex.internal.constants.LyricType;
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
    private long mCurrentLyricProgress = 0;


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
        this.mCurrentLyricProgress = progress;

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

    public void whenDraggingHappen(long progress) {
        minorReset();

        mCurrentLyricProgress = progress;
    }

    public void reset() {
        resetProperties();

        resetStats();
    }

    private void resetProperties() { // Reset when song changed
        mLyricsModel = null;
    }

    private void resetStats() {
        minorReset();
    }

    private void minorReset() { // Will recover immediately
        mCurrentLyricProgress = 0;
    }

    public void prepareUi() {
        if (mListener != null) {
            mListener.resetUi();
        }
    }

    public LyricModel getLyricsModel() {
        return this.mLyricsModel;
    }

    public long getCurrentLyricProgress() {
        return mCurrentLyricProgress;
    }

    public interface OnLyricListener {
        public void resetUi();

        public void requestRefreshUi();
    }
}
