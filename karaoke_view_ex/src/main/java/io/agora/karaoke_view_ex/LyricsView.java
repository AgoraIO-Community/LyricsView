package io.agora.karaoke_view_ex;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import io.agora.karaoke_view_ex.internal.LyricMachine;
import io.agora.karaoke_view_ex.internal.config.Config;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.internal.utils.LyricsLineDrawerHelper;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * A custom view for displaying karaoke lyrics with various visual effects.
 * Supports features like highlighting current line, scrolling animations,
 * line wrapping, and touch interactions for seeking.
 */
@SuppressLint("StaticFieldLeak")
public class LyricsView extends View {
    /**
     * The current lyrics model containing all lyrics data
     */
    private volatile LyricModel mLyricsModel;

    /**
     * The lyrics state machine managing lyrics progression
     */
    private LyricMachine mLyricMachine;

    /**
     * Text paint for rendering "No Lyrics" label
     */
    private final TextPaint mPaintNoLyricsFg = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Text paint for rendering highlighted (foreground) lyrics
     */
    private final TextPaint mPaintFg = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Text paint for rendering normal (background) lyrics
     */
    private final TextPaint mPaintBg = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Whether to show lyrics lines before the current line
     */
    private boolean mEnablePreviousLines = true;

    /**
     * Whether to show lyrics lines after the current line
     */
    private boolean mEnableUpcomingLines = true;

    /**
     * Text color for previous lyrics lines
     */
    private int mPreviousLineTextColor;

    /**
     * Text color for upcoming lyrics lines
     */
    private int mUpcomingLineTextColor;

    /**
     * Text size for normal lyrics lines
     */
    private float mTextSize;

    /**
     * Whether to enable automatic line wrapping for long lyrics
     */
    private boolean mEnableLineWrap = false;

    /**
     * Text color for current line's normal text
     */
    private int mCurrentLineTextColor;

    /**
     * Text size for current line
     */
    private float mCurrentLineTextSize;

    /**
     * Text color for current line's highlighted portion
     */
    private int mCurrentLineHighlightedTextColor;

    /**
     * Vertical spacing between lyrics lines
     */
    private float mLineSpacing;

    /**
     * Top padding for the entire lyrics view
     */
    private float mPaddingTop;

    /**
     * Text to display when no lyrics are available
     */
    private String mNoLyricsLabel;

    /**
     * Index of the currently displayed lyrics line
     */
    private int mIndexOfCurrentLine = -1;

    /**
     * Whether to show the prelude end position indicator
     */
    private boolean mEnablePreludeEndPositionIndicator = true;

    /**
     * Top padding for the prelude end position indicator
     */
    private float mPreludeEndPositionIndicatorPaddingTop;

    /**
     * Radius of the prelude end position indicator dots
     */
    private float mPreludeEndPositionIndicatorRadius;

    /**
     * Color of the prelude end position indicator
     */
    private int mPreludeEndPositionIndicatorColor;

    /**
     * Paint for drawing the prelude end position indicator
     */
    private final TextPaint mPreludeEndPositionIndicator = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Horizontal alignment of lyrics text
     */
    private LyricsLineDrawerHelper.Gravity mTextGravity;

    /**
     * Current UI update type flag
     */
    private int mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;

    /**
     * Static class defining UI update types
     */
    private static final class UpdateUiType {
        /**
         * No UI update needed
         */
        private static final int UPDATE_UI_TYPE_NO_NEED = 0;
        /**
         * Normal UI update
         */
        private static final int UPDATE_UI_TYPE_NORMAL = 1;
        /**
         * UI update with animation
         */
        private static final int UPDATE_UI_TYPE_WITH_ANIMATION = 2;
    }

    /**
     * Rectangle for clipping the highlighted portion of lyrics
     */
    private final Rect mRectClip = new Rect();
    /**
     * Last right position of clip rectangle for tracking changes
     */
    private int mLastRightOfRectClip = mRectClip.right;
    /**
     * Source rectangle for bitmap operations
     */
    private final Rect mRectSrc = new Rect();
    /**
     * Destination rectangle for bitmap operations
     */
    private final Rect mRectDst = new Rect();

    /**
     * Current timestamp in the lyrics playback
     */
    private long mCurrentTime = 0;
    /**
     * Total duration of the lyrics
     */
    private long mDuration;

    /**
     * Bitmap for background lyrics rendering
     */
    private Bitmap mBitmapBg;
    /**
     * Canvas for background lyrics rendering
     */
    private Canvas mCanvasBg;

    /**
     * Bitmap for foreground (highlighted) lyrics rendering
     */
    private Bitmap mBitmapFg;
    /**
     * Canvas for foreground lyrics rendering
     */
    private Canvas mCanvasFg;

    /**
     * Listener for lyrics seeking events
     */
    private OnLyricsSeekListener mOnSeekActionListener;
    /**
     * Whether dragging is enabled
     */
    private boolean mEnableDragging = true;
    /**
     * Whether dragging is currently in progress
     */
    private volatile boolean mDraggingInProgress = false;
    /**
     * Gesture detector for handling touch events
     */
    private GestureDetector mGestureDetector;
    /**
     * Vertical offset for lyrics positioning
     */
    private float mOffset;

    /**
     * Scaling ratio for lyrics width
     */
    private float mWidthRatio = 1.0f;

    /**
     * Handler for updating UI on the main thread
     */
    private Handler mHandler;

    /**
     * Gesture detector listener for handling touch events.
     * Implements dragging functionality for lyrics seeking.
     */
    private final GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            mDraggingInProgress = true;

            if (mOnSeekActionListener != null) {
                mOnSeekActionListener.onStartTrackingTouch();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            mOffset += -distanceY;
            performInvalidateIfNecessary();
            return true;
        }
    };

    public LyricsView(Context context) {
        this(context, null);
    }

    public LyricsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LyricsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    /**
     * Initializes the view with the specified attributes.
     * Sets up text paints, colors, sizes, and other visual properties.
     *
     * @param attrs Attribute set from XML layout
     */
    private void init(@Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        this.mHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));

        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.LyricsView);
        mTextSize = ta.getDimension(R.styleable.LyricsView_textSize,
                getResources().getDimension(R.dimen.normal_text_size));
        mEnableLineWrap = ta.getBoolean(R.styleable.LyricsView_enableLineWrap, false);
        mCurrentLineTextSize = ta.getDimension(R.styleable.LyricsView_currentLineTextSize,
                getResources().getDimension(R.dimen.current_text_size));
        if (mCurrentLineTextSize == 0) {
            mCurrentLineTextSize = mTextSize;
        }

        mLineSpacing = ta.getDimension(R.styleable.LyricsView_lineSpacing,
                getResources().getDimension(R.dimen.line_spacing));
        mPaddingTop = ta.getDimension(R.styleable.LyricsView_paddingTop,
                getResources().getDimension(R.dimen.padding_top));

        mEnablePreviousLines = ta.getBoolean(R.styleable.LyricsView_enablePreviousLines, true);
        mEnableUpcomingLines = ta.getBoolean(R.styleable.LyricsView_enableUpcomingLines, true);

        mCurrentLineTextColor = ta.getColor(R.styleable.LyricsView_currentLineTextColor, Color.WHITE);
        mPreviousLineTextColor = ta.getColor(R.styleable.LyricsView_previousLineTextColor,
                getResources().getColor(R.color.previous_text_color));
        mUpcomingLineTextColor = ta.getColor(R.styleable.LyricsView_upcomingLineTextColor,
                getResources().getColor(R.color.upcoming_text_color));
        mCurrentLineHighlightedTextColor = ta.getColor(R.styleable.LyricsView_currentLineHighlightedTextColor,
                getResources().getColor(R.color.highlighted_text_color));

        mNoLyricsLabel = ta.getString(R.styleable.LyricsView_labelWhenNoLyrics);
        mNoLyricsLabel = TextUtils.isEmpty(mNoLyricsLabel) ? getContext().getString(R.string.no_lyrics_label)
                : mNoLyricsLabel;
        int textGravity = ta.getInteger(R.styleable.LyricsView_textGravity, 0);
        mTextGravity = LyricsLineDrawerHelper.Gravity.parse(textGravity);
        mEnableDragging = ta.getBoolean(R.styleable.LyricsView_enableDragging, false);

        mPreludeEndPositionIndicatorPaddingTop = ta.getDimension(R.styleable.LyricsView_preludeEndPositionIndicatorPaddingTop,
                getResources().getDimension(R.dimen.start_of_verse_indicator_padding_top));
        mPreludeEndPositionIndicatorRadius = ta.getDimension(R.styleable.LyricsView_preludeEndPositionIndicatorRadius,
                getResources().getDimension(R.dimen.start_of_verse_indicator_radius));
        mPreludeEndPositionIndicatorColor = ta.getColor(R.styleable.LyricsView_preludeEndPositionIndicatorColor,
                getResources().getColor(R.color.default_popular_color));

        ta.recycle();

        mPreludeEndPositionIndicator.setColor(mPreludeEndPositionIndicatorColor);
        mPreludeEndPositionIndicator.setAntiAlias(true);
        mPreludeEndPositionIndicator.setDither(true);

        mPaintNoLyricsFg.setTextSize(mCurrentLineTextSize);
        mPaintNoLyricsFg.setColor(mCurrentLineHighlightedTextColor);
        mPaintNoLyricsFg.setAntiAlias(true);
        mPaintNoLyricsFg.setDither(true);
        mPaintNoLyricsFg.setTextAlign(Paint.Align.LEFT);

        mPaintFg.setTextSize(mCurrentLineTextSize);
        mPaintFg.setColor(mCurrentLineHighlightedTextColor);
        mPaintFg.setAntiAlias(true);
        mPaintFg.setDither(true);
        mPaintFg.setTextAlign(Paint.Align.LEFT);

        mPaintBg.setTextSize(mCurrentLineTextSize);
        mPaintBg.setColor(mCurrentLineTextColor);
        mPaintBg.setAntiAlias(true);
        mPaintBg.setDither(true);
        mPaintBg.setTextAlign(Paint.Align.LEFT);

        mGestureDetector = new GestureDetector(getContext(), mSimpleOnGestureListener);
        mGestureDetector.setIsLongpressEnabled(false);
    }

    /**
     * Bind lyrics drag event callback to receive state or event callbacks during dragging. See {@link OnLyricsSeekListener} for specific events
     *
     * @param onSeekActionListener Callback for lyrics drag events
     */
    public void setSeekListener(OnLyricsSeekListener onSeekActionListener) {
        this.mOnSeekActionListener = onSeekActionListener;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mEnableDragging) {
            return super.onTouchEvent(event);
        }

        LyricMachine machine = this.mLyricMachine;
        LyricModel lyricsModel = this.mLyricsModel;
        if (uninitializedOrNoLyrics(machine)) {
            return super.onTouchEvent(event);
        }

        if (targetIndex < 0 || lyricsModel.lines.size() <= targetIndex) {
            return super.onTouchEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            mDraggingInProgress = false;
            mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;
            mRectClip.setEmpty();
            mLastRightOfRectClip = mRectClip.right;

            LyricsLineModel targetLine = lyricsModel.lines.get(targetIndex);
            updateByProgress(targetLine.getStartTime());

            if (mOnSeekActionListener != null) {
                mOnSeekActionListener.onProgressChanged(targetLine.getStartTime());
                mOnSeekActionListener.onStopTrackingTouch();
            }
        }
        return mGestureDetector.onTouchEvent(event);
    }

    /**
     * Set whether to allow vertical scrolling
     *
     * @param enable true to enable vertical scrolling, false to disable
     */
    public void enableDragging(boolean enable) {
        this.mEnableDragging = enable;
    }

    /**
     * Set the total music length in milliseconds
     *
     * @param duration time in milliseconds
     */
    @Deprecated
    public synchronized void setDuration(long duration) {
        mDuration = duration;
    }

    public void setPreviousLineTextColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.previous_text_color);
        }

        mPreviousLineTextColor = color;

        performInvalidateIfNecessary();
    }

    public void setUpcomingLineTextColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.upcoming_text_color);
        }

        mUpcomingLineTextColor = color;

        performInvalidateIfNecessary();
    }

    public void setTextColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.default_popular_color);
        }

        mPreviousLineTextColor = color;
        mUpcomingLineTextColor = color;

        performInvalidateIfNecessary();
    }

    /**
     * Convenient method, set color of previous and upcoming text at once
     *
     * @param color color
     */
    public void setInactiveLineTextColor(@ColorInt int color) {
        setTextColor(color);
    }

    public void setActiveLinePlayedTextColor(@ColorInt int color) {
        setCurrentLineHighlightedTextColor(color);
    }

    public void setActiveLineUpcomingTextColor(@ColorInt int color) {
        setCurrentLineTextColor(color);
    }

    /**
     * Set text size of previous and upcoming text
     */
    public void setTextSize(float size) {
        mTextSize = size;
        mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;
        performInvalidateIfNecessary();
    }

    /**
     * Set text size of current line text
     */
    public void setCurrentLineTextSize(float size) {
        mCurrentLineTextSize = size;
        mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;
        performInvalidateIfNecessary();
    }

    /**
     * Set text color of current line text
     */
    public void setCurrentLineTextColor(@ColorInt int color) {
        if (color == 0) {
            color = Color.WHITE;
        }
        mCurrentLineTextColor = color;
        mPaintBg.setColor(mCurrentLineTextColor);
        mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;
        performInvalidateIfNecessary();
    }

    /**
     * Set text color of current line highlighted text
     */
    public void setCurrentLineHighlightedTextColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.highlighted_text_color);
        }
        mCurrentLineHighlightedTextColor = color;
        mPaintFg.setColor(mCurrentLineHighlightedTextColor);
        mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;
        performInvalidateIfNecessary();
    }

    /**
     * Set the text displayed in the center of the screen when lyrics are empty, such as "No lyrics"
     */
    public void setLabelShownWhenNoLyrics(String label) {
        mNoLyricsLabel = label;
        performInvalidateIfNecessary();
    }

    public void setLabelShownWhenNoLyricsTextSize(float size) {
        mPaintNoLyricsFg.setTextSize(size);
    }

    public void setLabelShownWhenNoLyricsTextColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.highlighted_text_color);
        }
        mPaintNoLyricsFg.setColor(color);
    }

    public void setLineSpacing(float lineSpacing) {
        this.mLineSpacing = lineSpacing;
    }

    public void enablePreludeEndPositionIndicator(boolean enable) {
        this.mEnablePreludeEndPositionIndicator = enable;
    }

    public void setPreludeEndPositionIndicatorPaddingTop(float paddingTop) {
        this.mPreludeEndPositionIndicatorPaddingTop = paddingTop;
    }

    public void setPreludeEndPositionIndicatorRadius(float radius) {
        this.mPreludeEndPositionIndicatorRadius = radius;
    }

    public void setPreludeEndPositionIndicatorColor(int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.default_popular_color);
        }
        this.mPreludeEndPositionIndicatorColor = color;
        this.mPreludeEndPositionIndicator.setColor(color);
    }

    /**
     * Checks if the lyrics view is uninitialized or has no lyrics data.
     *
     * @param machine The lyrics state machine to check
     * @return true if lyrics are not available or not initialized
     */
    protected final boolean uninitializedOrNoLyrics(LyricMachine machine) {
        return machine == null || mLyricMachine == null || mLyricsModel == null || mLyricsModel.lines == null || mLyricsModel.lines.isEmpty();
    }

    /**
     * Requests a refresh of the UI.
     * Updates the progress and triggers a redraw if necessary.
     */
    public void requestRefreshUi() {
        LyricMachine machine = this.mLyricMachine;
        if (machine == null) {
            return;
        }
        // Always update progress even UI or data is not ready
        updateByProgress(machine.getCurrentLyricProgress());
    }

    /**
     * Refreshes the "No Lyrics" display state.
     *
     * @return true if no lyrics are available and refresh was performed
     */
    private boolean refreshNoLyrics() {
        LyricMachine machine = this.mLyricMachine;
        if (uninitializedOrNoLyrics(machine)) {
            if ((mCurrentTime / 1000) % 2 == 0) {
                performInvalidateIfNecessary();
            }
            return true;
        }
        return false;
    }

    /**
     * Updates the view based on the current timestamp.
     * Handles line transitions and triggers animations when necessary.
     *
     * @param timestamp Current playback position in milliseconds
     */
    private void updateByProgress(long timestamp) {
        mCurrentTime = timestamp;

        LyricModel lyricsModel = this.mLyricsModel;
        if (refreshNoLyrics()) {
            return;
        }

        int line = quickSearchLineByTimestamp(mCurrentTime);

        if (line < 0 || line >= lyricsModel.lines.size()) {
            mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NO_NEED;
            return;
        }

        if (line != mIndexOfCurrentLine) {
            // When changing lines, pre-set text size to reduce flickering
            if (mTextSize > mCurrentLineTextSize) {
                mPaintBg.setTextSize(mCurrentLineTextSize);
                mPaintFg.setTextSize(mCurrentLineTextSize);
            }
            
            // NORMAL & ANIMATION
            mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL | UpdateUiType.UPDATE_UI_TYPE_WITH_ANIMATION;
            mIndexOfCurrentLine = line;
        }

        performInvalidateIfNecessary();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            int w = right - left - getPaddingStart() - getPaddingEnd();
            int h = bottom - top - getPaddingTop() - getPaddingBottom();

            calculateWidthRatio(right - left, getScreenWidth());

            if (h > 0) {
                if (mBitmapFg == null) {
                    createBitmapFg(w, h);
                } else if (mBitmapFg.getWidth() != w || mBitmapFg.getHeight() != h) {
                    if (!mBitmapFg.isRecycled()) {
                        mBitmapFg.recycle();
                    }

                    createBitmapFg(w, h);
                }

                if (mBitmapBg == null) {
                    createBitmapBg(w, h);
                } else if (mBitmapBg.getWidth() != w || mBitmapBg.getHeight() != h) {
                    if (!mBitmapBg.isRecycled()) {
                        mBitmapBg.recycle();
                    }

                    createBitmapBg(w, h);
                }
            }

            mRectSrc.left = 0;
            mRectSrc.top = 0;
            mRectSrc.right = getViewportWidth();
            mRectSrc.bottom = getViewportHeight();

            mRectDst.left = getPaddingStart();
            mRectDst.top = getPaddingTop();
            mRectDst.right = getPaddingStart() + getViewportWidth();
            mRectDst.bottom = getPaddingTop() + getViewportHeight();

            performInvalidateIfNecessary();
        }
    }

    /**
     * Creates the background bitmap and canvas for lyrics rendering.
     *
     * @param w Width of the bitmap
     * @param h Height of the bitmap
     */
    private void createBitmapBg(int w, int h) {
        mBitmapBg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mCanvasBg = new Canvas(mBitmapBg);
    }

    /**
     * Creates the foreground bitmap and canvas for highlighted lyrics rendering.
     *
     * @param w Width of the bitmap
     * @param h Height of the bitmap
     */
    private void createBitmapFg(int w, int h) {
        mBitmapFg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mCanvasFg = new Canvas(mBitmapFg);
    }

    /**
     * Checks if the canvas is ready for drawing.
     *
     * @return true if any of the required bitmaps or canvases are not initialized
     */
    private boolean isCanvasNotReady() {
        return mBitmapBg == null || mCanvasBg == null || mBitmapFg == null || mCanvasFg == null;
    }

    private int targetIndex = 0;

    /**
     * Reusable helper for drawing lyrics lines
     */
    private LyricsLineDrawerHelper mReuseLyricsLineDrawerHelper;

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Fail fast
        if (isCanvasNotReady()) {
            return;
        }

        LyricMachine machine = this.mLyricMachine;
        LyricModel lyricsModel = this.mLyricsModel;
        if (uninitializedOrNoLyrics(machine)) {
            int width = getViewportWidth();
            int height = getViewportHeight();
            if (width == 0 || height == 0 || mCurrentTime <= 1000) {
                return;
            }
            @SuppressLint("DrawAllocation")
            StaticLayout staticLayout = new StaticLayout(
                    mNoLyricsLabel,
                    mPaintNoLyricsFg,
                    width,
                    Layout.Alignment.ALIGN_CENTER,
                    1f,
                    0f,
                    false);

            canvas.save();
            float y = getPaddingTop() + (height - staticLayout.getHeight()) / 2F;
            canvas.translate(0, y);
            staticLayout.draw(canvas);
            canvas.restore();
            return;
        }

        float centerY = getViewportHeight() / 2F + getPaddingTop() + mPaddingTop;
        if (mDraggingInProgress) {
            mBitmapBg.eraseColor(0);
            mBitmapFg.eraseColor(0);
            mPaintBg.setColor(mCurrentLineTextColor);

            LyricsLineDrawerHelper lyricsLineDrawerHelper;
            float y = 0;
            float yReal;
            for (int i = 0; i < lyricsModel.lines.size(); i++) {
                if (i == mIndexOfCurrentLine) {
                    mPaintBg.setTextSize(mCurrentLineTextSize);
                } else if (i < mIndexOfCurrentLine) {
                    mPaintBg.setColor(mPreviousLineTextColor);
                    mPaintBg.setTextSize(mTextSize);
                } else {
                    mPaintBg.setColor(mUpcomingLineTextColor);
                    mPaintBg.setTextSize(mTextSize);
                }

                LyricsLineModel lyricsTargetLineModel = lyricsModel.lines.get(i);
                lyricsLineDrawerHelper = new LyricsLineDrawerHelper(lyricsTargetLineModel, mPaintFg, mPaintBg, getViewportWidth(),
                        mTextGravity, mEnableLineWrap, mWidthRatio);

                yReal = y + mOffset;
                if (i == 0 && yReal > (centerY - getPaddingTop() - (lyricsLineDrawerHelper.getHeight() / 2F))) {
                    // Top limit
                    mOffset = centerY - getPaddingTop() - (lyricsLineDrawerHelper.getHeight() / 2F);
                    yReal = y + mOffset;
                }

                if (yReal + lyricsLineDrawerHelper.getHeight() < 0) {
                    y = y + lyricsLineDrawerHelper.getHeight() + mLineSpacing;
                    continue;
                }

                if (i == mIndexOfCurrentLine) {
                    Rect[] drawRects = lyricsLineDrawerHelper.getDrawRectByTime(mCurrentTime);

                    for (Rect dr : drawRects) {
                        if (dr.left == dr.right) {
                            continue;
                        }

                        mLastRightOfRectClip = mRectClip.right;

                        mRectClip.left = dr.left;
                        mRectClip.top = (int) (dr.top + yReal);
                        mRectClip.right = dr.right;
                        mRectClip.bottom = (int) (dr.bottom + yReal);

                        mCanvasFg.save();
                        mCanvasFg.clipRect(mRectClip);

                        checkIfXTranslationShouldApply(lyricsLineDrawerHelper);

                        mCanvasFg.translate(mCurrentLineTranslateX, yReal);

                        lyricsLineDrawerHelper.drawFg(mCanvasFg);
                        mCanvasFg.restore();
                    }
                }

                // Draw all background lines
                mCanvasBg.save();
                mCanvasBg.translate(i == mIndexOfCurrentLine ? mCurrentLineTranslateX : 0, yReal);
                lyricsLineDrawerHelper.draw(mCanvasBg);
                mCanvasBg.restore();

                if ((y - mLineSpacing + getPaddingTop() + mOffset) <= centerY
                        && centerY <= (y + lyricsLineDrawerHelper.getHeight() + getPaddingTop() + mOffset)) {
                    targetIndex = i;
                }

                y = y + lyricsLineDrawerHelper.getHeight() + mLineSpacing;
                if (y + mOffset > getViewportHeight()) {
                    break;
                }
            }

            canvas.drawBitmap(mBitmapBg, mRectSrc, mRectDst, null);
            canvas.drawBitmap(mBitmapFg, mRectSrc, mRectDst, null);

            canvas.drawLine(0, centerY, getWidth(), centerY + 1, mPaintFg);
        } else {
            LyricsLineModel currentLine = lyricsModel.lines.get(mIndexOfCurrentLine);
            float fraction = 1.0F; // NORMAL UPDATE
            if ((mForceUpdateUi & UpdateUiType.UPDATE_UI_TYPE_NORMAL) == UpdateUiType.UPDATE_UI_TYPE_NORMAL) {
                doConfigCanvasAndTexts(fraction);
                mReuseLyricsLineDrawerHelper = new LyricsLineDrawerHelper(currentLine, mPaintFg, mPaintBg, getViewportWidth(),
                        mTextGravity, mEnableLineWrap, mWidthRatio);

                LyricsLineDrawerHelper finalRefLyricsLineDrawerHelper = mReuseLyricsLineDrawerHelper;
                if ((mForceUpdateUi & UpdateUiType.UPDATE_UI_TYPE_WITH_ANIMATION) == UpdateUiType.UPDATE_UI_TYPE_WITH_ANIMATION) {

                    doScrollWithAnimationTo(mIndexOfCurrentLine, new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                            float fraction = animation.getAnimatedFraction();

                            if (Config.DEBUG) {
                                LogUtils.d("debugAnimation/onAnimationUpdate/REAL: fraction=" + fraction);
                            }

                            doConfigCanvasAndTexts(fraction);

                            drawCurrent(finalRefLyricsLineDrawerHelper, fraction);
                            drawPrevious(lyricsModel, finalRefLyricsLineDrawerHelper, fraction);
                            drawUpcoming(lyricsModel, finalRefLyricsLineDrawerHelper, fraction);

                            drawHighLight(finalRefLyricsLineDrawerHelper, fraction); // Will first `MAX_SMOOTH_SCROLL_DURATION` part of the highlight line
                        }
                    });
                } else if ((mForceUpdateUi & UpdateUiType.UPDATE_UI_TYPE_NORMAL) == UpdateUiType.UPDATE_UI_TYPE_NORMAL) {
                    drawCurrent(finalRefLyricsLineDrawerHelper, fraction);
                    drawPrevious(lyricsModel, finalRefLyricsLineDrawerHelper, fraction);
                    drawUpcoming(lyricsModel, finalRefLyricsLineDrawerHelper, fraction);
                }

                mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NO_NEED;
            }

            if (isLyricsLineOverlong(mReuseLyricsLineDrawerHelper) && !mContentScrolling) {
                mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;
            }

            canvas.drawBitmap(mBitmapBg, mRectSrc, mRectDst, null);

            if (!mContentScrolling) {
                // Continually to draw the rest part of highlight when scrolling stops
                drawHighLight(mReuseLyricsLineDrawerHelper, fraction);
            }

            canvas.drawBitmap(mBitmapFg, mRectSrc, mRectDst, null);
        }

        drawFirstToneIndicator(canvas);
    }

    private void doConfigCanvasAndTexts(float fraction) {
        // Calculate the current line text size considering font size differences
        float textSize;
        
        // When normal line font is larger than current line font, optimize to avoid flickering
        if (mTextSize > mCurrentLineTextSize) {
            // Directly use current line font size, avoid gradient processing during transitions
            textSize = mCurrentLineTextSize;
        } else {
            // Normal gradient calculation for cases when mCurrentLineTextSize >= mTextSize
            textSize = mTextSize + (mCurrentLineTextSize - mTextSize) * fraction;
        }

        mPaintBg.setColor(mCurrentLineTextColor);
        mPaintBg.setTextSize(textSize);

        mPaintFg.setColor(mCurrentLineHighlightedTextColor);
        mPaintFg.setTextSize(textSize);

        if (mBitmapBg != null) {
            mBitmapBg.eraseColor(0);
        }
        if (mBitmapFg != null) {
            mBitmapFg.eraseColor(0);
        }
    }

    private boolean mContentScrolling = false;
    /**
     * Maximum duration for smooth scrolling animation in milliseconds
     */
    private static final long MAX_SMOOTH_SCROLL_DURATION = 300;
    private ValueAnimator mScrollingAnimator;

    private void doScrollWithAnimationTo(final int position, ValueAnimator.AnimatorUpdateListener updateListener) {
        if (mContentScrolling) {
            mScrollingAnimator.cancel();
        }

        mContentScrolling = true;
        mScrollingAnimator = ValueAnimator.ofFloat(0, 1);
        mScrollingAnimator.setDuration(MAX_SMOOTH_SCROLL_DURATION);
        mScrollingAnimator.addUpdateListener(updateListener);
        mScrollingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mContentScrolling = false;
                performInvalidateIfNecessary();
            }
        });
        mScrollingAnimator.start();

    }

    /**
     * Draws the prelude end position indicator.
     * Shows countdown dots before lyrics start.
     *
     * @param canvas The canvas to draw on
     */
    private void drawFirstToneIndicator(Canvas canvas) {
        if (!mEnablePreludeEndPositionIndicator) {
            return;
        }
        LyricMachine machine = this.mLyricMachine;
        LyricModel lyricsModel = this.mLyricsModel;
        if (uninitializedOrNoLyrics(machine)) {
            return;
        }

        // to make start-of-verse indicator animation more smooth
        double countDown = Math.ceil((lyricsModel.preludeEndPosition - mCurrentTime) / 1000.f);
        if (countDown <= 0) {
            return;
        }

        // central of circle
        float sY = mPreludeEndPositionIndicatorPaddingTop + mPreludeEndPositionIndicatorRadius;
        // central of circle
        float sX = getWidth() / 2.f;

        // Indicator 1
        canvas.drawCircle(sX - mPreludeEndPositionIndicatorRadius * 3, sY, mPreludeEndPositionIndicatorRadius,
                mPreludeEndPositionIndicator);

        if ((countDown >= 2 & countDown < 3)) {
            // Indicator 2
            canvas.drawCircle(sX, sY, mPreludeEndPositionIndicatorRadius, mPreludeEndPositionIndicator);
        } else if ((countDown >= 3)) {
            // Indicator 2
            canvas.drawCircle(sX, sY, mPreludeEndPositionIndicatorRadius, mPreludeEndPositionIndicator);

            // After shown for a little time, then begin to blink
            if ((countDown % 2 == 1) || mCurrentTime < 2000L) {
                // Indicator 3
                canvas.drawCircle(sX + 3 * mPreludeEndPositionIndicatorRadius, sY, mPreludeEndPositionIndicatorRadius,
                        mPreludeEndPositionIndicator);
            }
        }
    }

    /**
     * Draws lyrics lines that come before the current line.
     *
     * @param lyricsModel           The lyrics data model
     * @param currentLineDrawHelper Helper for drawing the current line
     * @param fraction              Animation progress fraction
     */
    private void drawPrevious(LyricModel lyricsModel, LyricsLineDrawerHelper currentLineDrawHelper, float fraction) {
        if (!mEnablePreviousLines) {
            return;
        }

        if (currentLineDrawHelper == null || lyricsModel == null) {
            return;
        }

        float yOfTargetLine = (getViewportHeight() - currentLineDrawHelper.getHeight() * fraction) / 2F + mPaddingTop;

        LyricsLineModel lyricsTargetLineModel;
        LyricsLineDrawerHelper lyricsTargetLineDrawerHelper;
        mPaintBg.setTextSize(mTextSize);
        mPaintBg.setColor(mPreviousLineTextColor);

        mCanvasBg.save();
        mCanvasBg.translate(0, yOfTargetLine);

        for (int i = mIndexOfCurrentLine - 1; i >= 0; i--) {
            lyricsTargetLineModel = lyricsModel.lines.get(i);
            lyricsTargetLineDrawerHelper = new LyricsLineDrawerHelper(lyricsTargetLineModel, mPaintFg, mPaintBg, getViewportWidth(), mTextGravity, mEnableLineWrap, mWidthRatio);

            mOffset = mOffset - lyricsTargetLineDrawerHelper.getHeight() - mLineSpacing;

            if (yOfTargetLine - mLineSpacing - lyricsTargetLineDrawerHelper.getHeight() < 0) {
                continue;
            }

            float height = mLineSpacing + lyricsTargetLineDrawerHelper.getHeight();
            mCanvasBg.translate(0, -height);
            lyricsTargetLineDrawerHelper.draw(mCanvasBg);
            yOfTargetLine = yOfTargetLine - height;
        }
        mCanvasBg.restore();
    }

    private float mCurrentLineTranslateX = 0;

    /**
     * Checks if horizontal translation should be applied to the current line.
     * Handles horizontal scrolling for long lyrics lines.
     *
     * @param currentLineDrawHelper Helper for drawing the current line
     */
    private void checkIfXTranslationShouldApply(LyricsLineDrawerHelper currentLineDrawHelper) {
        int halfWidthOfLyricsView = getViewportWidth() / 2;
        if (isLyricsLineOverlong(currentLineDrawHelper) && !mContentScrolling) {
            // When to start:
            // 1. The highlight part is larger than half of the lyrics view
            // 2. The rested part is larger than half of the lyrics view
            if (mRectClip.width() > halfWidthOfLyricsView && currentLineDrawHelper.getWidth() - mRectClip.width() > halfWidthOfLyricsView) {
                mCurrentLineTranslateX -= (mRectClip.right - mLastRightOfRectClip);
            }
        } else {
            mCurrentLineTranslateX = 0;
        }
        if (Config.DEBUG) {
            LogUtils.d("checkIfXTranslationShouldApply xTranslation: " + mCurrentLineTranslateX + ", " +
                    "widthOfHighlightRect: " + mRectClip.width() + ", mLastRightOfRectClip: " + mLastRightOfRectClip + ", " +
                    "widthOfCurrentLine: " + currentLineDrawHelper.getWidth() + ", widthOfLyricsView: " + halfWidthOfLyricsView * 2);
        }
    }

    /**
     * Draws the current lyrics line.
     *
     * @param currentLineDrawHelper Helper for drawing the current line
     * @param fraction              Animation progress fraction
     */
    private void drawCurrent(LyricsLineDrawerHelper currentLineDrawHelper, float fraction) {
        if (currentLineDrawHelper == null) {
            return;
        }

        // Correct calculation method, considering font size differences
        float yOfTargetLine;
        
        // Special handling for vertical centering when normal line font is larger than current line font
        if (mTextSize > mCurrentLineTextSize) {
            // Adjust vertical position to avoid flickering
            float sizeRatio = mCurrentLineTextSize / mTextSize;
            yOfTargetLine = (getViewportHeight() - currentLineDrawHelper.getHeight()) / 2F + mPaddingTop;
        } else {
            // Normal vertical centering calculation
            yOfTargetLine = (getViewportHeight() - currentLineDrawHelper.getHeight() * fraction) / 2F + mPaddingTop;
        }

        mCanvasBg.save();

        // Only apply horizontal scrolling when line wrap is disabled
        if (!mEnableLineWrap) {
            checkIfXTranslationShouldApply(currentLineDrawHelper);
        } else {
            // No need for horizontal scrolling when line wrap is enabled
            mCurrentLineTranslateX = 0;
        }
        mCanvasBg.translate(mCurrentLineTranslateX, yOfTargetLine);
        currentLineDrawHelper.draw(mCanvasBg);
        mCanvasBg.restore();

        mOffset = yOfTargetLine;

        mLastRightOfRectClip = mRectClip.right;
    }

    /**
     * Draws lyrics lines that come after the current line.
     *
     * @param lyricsModel           The lyrics data model
     * @param currentLineDrawHelper Helper for drawing the current line
     * @param fraction              Animation progress fraction
     */
    private void drawUpcoming(LyricModel lyricsModel, LyricsLineDrawerHelper currentLineDrawHelper, float fraction) {
        if (!mEnableUpcomingLines) {
            return;
        }

        if (currentLineDrawHelper == null || lyricsModel == null) {
            return;
        }

        float yOfTargetLine = (getViewportHeight() + currentLineDrawHelper.getHeight() * (1 + (1 - fraction))) / 2F + mLineSpacing + mPaddingTop;

        LyricsLineModel lyricsTargetLineModel;
        LyricsLineDrawerHelper lyricsTargetLineDrawerHelper;
        mPaintBg.setTextSize(mTextSize);
        mPaintBg.setColor(mUpcomingLineTextColor);

        mCanvasBg.save();
        mCanvasBg.translate(0, yOfTargetLine);

        for (int i = mIndexOfCurrentLine + 1; i < lyricsModel.lines.size(); i++) {
            lyricsTargetLineModel = lyricsModel.lines.get(i);
            lyricsTargetLineDrawerHelper = new LyricsLineDrawerHelper(lyricsTargetLineModel, mPaintFg, mPaintBg, getViewportWidth(), mTextGravity, mEnableLineWrap, mWidthRatio);

            if (yOfTargetLine + lyricsTargetLineDrawerHelper.getHeight() > getViewportHeight()) {
                break;
            }

            lyricsTargetLineDrawerHelper.draw(mCanvasBg);
            float height = lyricsTargetLineDrawerHelper.getHeight() + mLineSpacing;
            mCanvasBg.translate(0, height);
            yOfTargetLine = yOfTargetLine + height;
        }
        mCanvasBg.restore();
    }

    /**
     * Draws the highlighted portion of the current lyrics line.
     *
     * @param currentLineDrawHelper Helper for drawing the current line
     * @param fraction              Animation progress fraction
     */
    private void drawHighLight(LyricsLineDrawerHelper currentLineDrawHelper, float fraction) {
        if (currentLineDrawHelper == null) {
            return;
        }

        mBitmapFg.eraseColor(0);

        Rect[] drawRects = currentLineDrawHelper.getDrawRectByTime(mCurrentTime);
        
        // Use the same vertical position calculation logic as in drawCurrent method
        float yOfTargetLine;
        if (mTextSize > mCurrentLineTextSize) {
            // Adjust vertical position to avoid flickering
            float sizeRatio = mCurrentLineTextSize / mTextSize;
            yOfTargetLine = (getViewportHeight() - currentLineDrawHelper.getHeight()) / 2F + mPaddingTop;
        } else {
            // Normal vertical centering calculation
            yOfTargetLine = (getViewportHeight() - currentLineDrawHelper.getHeight() * fraction) / 2F + mPaddingTop;
        }

        for (Rect dr : drawRects) {
            if (dr.left == dr.right) {
                continue;
            }

            mRectClip.left = dr.left;
            mRectClip.top = (int) (dr.top + yOfTargetLine);
            mRectClip.right = dr.right;
            mRectClip.bottom = (int) (dr.bottom + yOfTargetLine);

            mCanvasFg.save();
            mCanvasFg.clipRect(mRectClip);
            mCanvasFg.translate(mCurrentLineTranslateX, yOfTargetLine);
            currentLineDrawHelper.drawFg(mCanvasFg);
            mCanvasFg.restore();
        }
    }

    /**
     * Attaches a lyrics machine to this view.
     * The lyrics machine must be prepared before attaching.
     *
     * @param machine The lyrics machine to attach
     * @throws IllegalStateException if the machine is not ready
     */
    public void attachToLyricMachine(LyricMachine machine) {
        if (!machine.isReady()) {
            throw new IllegalStateException("Must call ScoringMachine.prepare before attaching");
        }
        this.mLyricMachine = machine;
        this.mLyricsModel = machine.getLyricsModel();
        forceCheckLineWrap();
        // Update values from UI view if necessary
    }

    /**
     * Resets the view to its initial state.
     * Clears all loaded lyrics and internal state.
     */
    public void reset() {
        resetInternal();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                invalidateForSureAndMarkTheTimestamp();
            }
        });
    }

    private long mLastViewInvalidateTs;

    /**
     * Performs view invalidation if enough time has passed since the last invalidation.
     * Helps prevent excessive redraw operations.
     */
    private void performInvalidateIfNecessary() {
        long delta = System.currentTimeMillis() - mLastViewInvalidateTs;
        if (delta <= 16) {
            return;
        }

        invalidateForSureAndMarkTheTimestamp();
    }

    /**
     * Forces an immediate view invalidation and updates the last invalidation timestamp.
     * Handles thread-safe invalidation on main or background threads.
     */
    private void invalidateForSureAndMarkTheTimestamp() {
        // Try to avoid too many `invalidate` operations, it is expensive
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            postInvalidate();
        } else {
            invalidate();
        }

        mLastViewInvalidateTs = System.currentTimeMillis();
    }

    /**
     * Resets all internal state variables to their default values.
     */
    private void resetInternal() {
        mLyricMachine = null;
        mLyricsModel = null;
        mIndexOfCurrentLine = -1;
        mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;
        mCurrentTime = 0;
        mOffset = 0;
        targetIndex = 0;
        mDuration = 0;
        mRectClip.setEmpty();
        mLastRightOfRectClip = mRectClip.right;
        mCurrentLineTranslateX = 0;
    }

    /**
     * Performs binary search to find the lyrics line that should be displayed at the given timestamp.
     *
     * @param time Current timestamp in milliseconds
     * @return Index of the lyrics line to display
     */
    private int quickSearchLineByTimestamp(long time) {
        LyricMachine machine = this.mLyricMachine;
        LyricModel lyricsModel = this.mLyricsModel;
        if (uninitializedOrNoLyrics(machine)) {
            return 0;
        }

        int left = 0;
        int right = lyricsModel.lines.size();
        while (left <= right) {
            int middle = (left + right) / 2;
            long middleTime = lyricsModel.lines.get(middle).getStartTime();

            if (time < middleTime) {
                right = middle - 1;
            } else {
                if (middle + 1 >= lyricsModel.lines.size()
                        || time < lyricsModel.lines.get(middle + 1).getStartTime()) {
                    return middle;
                }

                left = middle + 1;
            }
        }

        return 0;
    }

    /**
     * Gets the width of the view's content area excluding padding.
     *
     * @return Width in pixels of the drawable area
     */
    private int getViewportWidth() {
        return Math.max(getWidth() - getPaddingStart() - getPaddingEnd(), 0);
    }

    /**
     * Gets the height of the view's content area excluding padding.
     *
     * @return Height in pixels of the drawable area
     */
    private int getViewportHeight() {
        return Math.max((getHeight() - getPaddingTop() - getPaddingBottom()), 0);
    }

    /**
     * Checks if the current lyrics line is too long for the view width.
     *
     * @param currentLineDrawHelper Helper for drawing the current line
     * @return true if the line needs horizontal scrolling
     */
    private boolean isLyricsLineOverlong(LyricsLineDrawerHelper currentLineDrawHelper) {
        if (currentLineDrawHelper == null) {
            return false;
        }

        // If line wrap is enabled and lyrics line count is greater than 1, it means it's already displayed with line wrapping, no need for horizontal scrolling
        if (mEnableLineWrap && currentLineDrawHelper.getLineCount() > 1) {
            return false;
        }

        // Only need horizontal scrolling when single line and width exceeds view width
        return currentLineDrawHelper.getWidth() > getViewportWidth();
    }

    /**
     * Set whether to enable automatic lyrics line wrapping
     *
     * @param enable true to enable automatic line wrapping, false to use horizontal scrolling
     */
    public void enableLineWrap(boolean enable) {
        if (this.mEnableLineWrap != enable) {
            this.mEnableLineWrap = enable;
            forceCheckLineWrap();
            mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;
            performInvalidateIfNecessary();
        }
    }

    /**
     * Get whether automatic lyrics line wrapping is currently enabled
     *
     * @return true if automatic line wrapping is enabled, false if using horizontal scrolling
     */
    public boolean isLineWrapEnabled() {
        return mEnableLineWrap;
    }

    private void forceCheckLineWrap() {
        if (null == mLyricsModel || null == mLyricsModel.lines || mLyricsModel.lines.isEmpty()) {
            return;
        }
        if (mLyricsModel.type == LyricType.LRC) {
            mEnableLineWrap = true;
        }
    }

    /**
     * Gets the device's screen width.
     *
     * @return Screen width in pixels
     */
    private int getScreenWidth() {
        android.view.WindowManager wm = (android.view.WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getContext().getDisplay().getRealMetrics(metrics);
        } else {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        }
        return metrics.widthPixels;
    }

    /**
     * Calculates the width ratio for lyrics rendering based on view and screen width.
     * Updates the ratio if necessary and triggers a UI refresh.
     *
     * @param viewWidth   Width of the view
     * @param screenWidth Width of the screen
     */
    private void calculateWidthRatio(int viewWidth, int screenWidth) {
        if (viewWidth <= 0 || screenWidth <= 0) {
            return;
        }

        if (mEnableLineWrap) {
            mWidthRatio = 1.0f;
            return;
        }
        float baseRatio = (float) viewWidth / screenWidth;

        float ratio = 0.53f + 0.47f * baseRatio;

        if (this.mWidthRatio != ratio) {
            this.mWidthRatio = ratio;
            LogUtils.d("calculateWidthRatio viewWidth: " + viewWidth + "screenWidth:" + screenWidth + ", ratio: " + ratio);
            mForceUpdateUi = UpdateUiType.UPDATE_UI_TYPE_NORMAL;
            performInvalidateIfNecessary();
        }
    }

    /**
     * Interface for receiving lyrics seeking events.
     * Provides callbacks for tracking touch events and progress changes.
     */
    @MainThread
    public interface OnLyricsSeekListener {
        /**
         * Called when the lyrics progress changes during seeking.
         *
         * @param progress Current progress in milliseconds
         */
        void onProgressChanged(long progress);

        /**
         * Called when the user starts dragging the lyrics.
         */
        void onStartTrackingTouch();

        /**
         * Called when the user stops dragging the lyrics.
         */
        void onStopTrackingTouch();
    }
}
