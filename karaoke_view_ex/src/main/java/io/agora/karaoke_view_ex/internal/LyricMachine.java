package io.agora.karaoke_view_ex.internal;

import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * State/Information manager for playing/rendering/on-going lyrics.
 * This class handles the non-UI related logic and is shared by all components.
 * It manages the lyrics model, progress tracking, and UI update notifications.
 */
public class LyricMachine {
    /**
     * The current lyrics model being processed
     */
    private LyricModel mLyricsModel;

    /**
     * Listener for lyric-related events
     */
    private final OnLyricListener mListener;

    /**
     * Current progress of lyrics playback in milliseconds
     */
    private long mCurrentLyricProgress = 0;

    /**
     * Empty lyrics model used as a fallback
     */
    private static final LyricModel EMPTY_LYRICS_MODEL = new LyricModel(LyricType.LRC);

    /**
     * Constructs a new LyricMachine instance
     *
     * @param listener The listener to handle lyric-related events
     */
    public LyricMachine(OnLyricListener listener) {
        reset();
        this.mListener = listener;
    }

    /**
     * Sets the lyrics data and prepares for rendering
     *
     * @param model The lyrics model to be processed
     */
    public void prepare(LyricModel model) {
        reset();

        if (model == null || model.lines == null || model.lines.isEmpty()) {
            LogUtils.e("Invalid lyrics model, use built-in EMPTY_LYRICS_MODEL");
            mLyricsModel = EMPTY_LYRICS_MODEL;
            return;
        }

        mLyricsModel = model;
    }

    /**
     * Checks if the lyrics machine is ready for operation
     *
     * @return true if lyrics model is set, false otherwise
     */
    public boolean isReady() {
        return mLyricsModel != null;
    }

    /**
     * Updates the lyrics progress
     *
     * @param progress Current playback time in milliseconds.
     *                 Note: progress must be greater than the lyrics end time to trigger the callback for the last line
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

    /**
     * Handles progress updates when dragging occurs
     *
     * @param progress The new progress position in milliseconds
     */
    public void whenDraggingHappen(long progress) {
        minorReset();
        mCurrentLyricProgress = progress;
    }

    /**
     * Resets all states and properties
     */
    public void reset() {
        resetProperties();
        resetStats();
    }

    /**
     * Resets properties when song changes
     */
    private void resetProperties() {
        mLyricsModel = null;
    }

    /**
     * Resets statistics and progress
     */
    private void resetStats() {
        minorReset();
    }

    /**
     * Performs a minor reset that will recover immediately
     */
    private void minorReset() {
        mCurrentLyricProgress = 0;
    }

    /**
     * Prepares the UI for lyrics display
     */
    public void prepareUi() {
        if (mListener != null) {
            mListener.resetUi();
        }
    }

    /**
     * Gets the current lyrics model
     *
     * @return The current lyrics model
     */
    public LyricModel getLyricsModel() {
        return this.mLyricsModel;
    }

    /**
     * Gets the current lyrics progress
     *
     * @return Current progress in milliseconds
     */
    public long getCurrentLyricProgress() {
        return mCurrentLyricProgress;
    }

    /**
     * Interface for handling lyric-related events
     */
    public interface OnLyricListener {
        /**
         * Called when UI needs to be reset
         */
        void resetUi();

        /**
         * Called when UI refresh is requested
         */
        void requestRefreshUi();
    }
}
