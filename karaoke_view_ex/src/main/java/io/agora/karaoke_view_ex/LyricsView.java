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
import androidx.annotation.Nullable;

import io.agora.karaoke_view_ex.R;
import io.agora.karaoke_view_ex.internal.LyricMachine;
import io.agora.karaoke_view_ex.internal.config.Config;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.internal.utils.LyricsLineDrawerHelper;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * 歌词视图
 * 主要负责歌词的显示，支持上下拖动调整进度。
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/7/6
 */
@SuppressLint("StaticFieldLeak")
public class LyricsView extends View {
    private volatile LyricModel mLyricsModel;

    private LyricMachine mLyricMachine;

    private final TextPaint mPaintNoLyricsFG = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mPaintFG = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mPaintBG = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private boolean mEnablePreviousLines = true;
    private boolean mEnableUpcomingLines = true;

    private int mPreviousLineTextColor;
    private int mUpcomingLineTextColor;
    private float mTextSize;

    private int mCurrentLineTextColor;
    private float mCurrentLineTextSize;
    private int mCurrentLineHighlightedTextColor;

    private float mLineSpacing;
    private float mPaddingTop;
    private String mNoLyricsLabel;
    private int mIndexOfCurrentLine = 0;

    private boolean mEnablePreludeEndPositionIndicator = true;
    private float mPreludeEndPositionIndicatorPaddingTop;
    private float mPreludeEndPositionIndicatorRadius;
    private int mPreludeEndPositionIndicatorColor;
    private final TextPaint mPreludeEndPositionIndicator = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /**
     * 歌词显示位置，靠左/居中/靠右
     */
    private LyricsLineDrawerHelper.Gravity mTextGravity;

    private int mForceUpdateUI = UpdateUIType.UpdateUIType_NORMAL;


    private static final class UpdateUIType {
        private static final int UpdateUIType_NO_NEED = 0;
        private static final int UpdateUIType_NORMAL = 1;
        private static final int UpdateUIType_WITH_ANIMATION = 2;
    }

    private final Rect mRectClip = new Rect();
    private int mLastRightOfRectClip = mRectClip.right;
    private final Rect mRectSrc = new Rect();
    private final Rect mRectDst = new Rect();

    private long mCurrentTime = 0;
    private long mDuration;

    private Bitmap mBitmapBG;
    private Canvas mCanvasBG;

    private Bitmap mBitmapFG;
    private Canvas mCanvasFG;

    private OnLyricsSeekListener mOnSeekActionListener;
    private boolean mEnableDragging = true;
    private volatile boolean mDraggingInProgress = false;
    private GestureDetector mGestureDetector;
    private float mOffset;
    private final GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            mDraggingInProgress = true;

            if (mOnSeekActionListener != null) {
                mOnSeekActionListener.onStartTrackingTouch();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
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

    private Handler mHandler;

    private void init(@Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        this.mHandler = new Handler(Looper.myLooper());

        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.LyricsView);
        mTextSize = ta.getDimension(R.styleable.LyricsView_textSize,
                getResources().getDimension(R.dimen.normal_text_size));
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

        mPaintNoLyricsFG.setTextSize(mCurrentLineTextSize);
        mPaintNoLyricsFG.setColor(mCurrentLineHighlightedTextColor);
        mPaintNoLyricsFG.setAntiAlias(true);
        mPaintNoLyricsFG.setDither(true);
        mPaintNoLyricsFG.setTextAlign(Paint.Align.LEFT);

        mPaintFG.setTextSize(mCurrentLineTextSize);
        mPaintFG.setColor(mCurrentLineHighlightedTextColor);
        mPaintFG.setAntiAlias(true);
        mPaintFG.setDither(true);
        mPaintFG.setTextAlign(Paint.Align.LEFT);

        mPaintBG.setTextSize(mCurrentLineTextSize);
        mPaintBG.setColor(mCurrentLineTextColor);
        mPaintBG.setAntiAlias(true);
        mPaintBG.setDither(true);
        mPaintBG.setTextAlign(Paint.Align.LEFT);

        mGestureDetector = new GestureDetector(getContext(), mSimpleOnGestureListener);
        mGestureDetector.setIsLongpressEnabled(false);
    }

    /**
     * 绑定歌词拖动事件回调，用于接收拖动事件中状态或者事件回调。具体事件参考 {@link OnLyricsSeekListener}
     *
     * @param onSeekActionListener
     */
    public void setSeekListener(OnLyricsSeekListener onSeekActionListener) {
        this.mOnSeekActionListener = onSeekActionListener;
    }

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
            mForceUpdateUI = UpdateUIType.UpdateUIType_NORMAL;
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
     * 设置是否允许上下滑动
     *
     * @param enable
     */
    public void enableDragging(boolean enable) {
        this.mEnableDragging = enable;
    }

    /**
     * 设置音乐总长度，单位毫秒
     *
     * @param duration 时间，单位毫秒
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
     * @param color
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
        mForceUpdateUI = UpdateUIType.UpdateUIType_NORMAL;
        performInvalidateIfNecessary();
    }

    /**
     * Set text size of current line text
     */
    public void setCurrentLineTextSize(float size) {
        mCurrentLineTextSize = size;
        mForceUpdateUI = UpdateUIType.UpdateUIType_NORMAL;
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
        mPaintBG.setColor(mCurrentLineTextColor);
        mForceUpdateUI = UpdateUIType.UpdateUIType_NORMAL;
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
        mPaintFG.setColor(mCurrentLineHighlightedTextColor);
        mForceUpdateUI = UpdateUIType.UpdateUIType_NORMAL;
        performInvalidateIfNecessary();
    }

    /**
     * 设置歌词为空时屏幕中央显示的文字，如 “暂无歌词”
     */
    public void setLabelShownWhenNoLyrics(String label) {
        mNoLyricsLabel = label;
        performInvalidateIfNecessary();
    }

    public void setLabelShownWhenNoLyricsTextSize(float size) {
        mPaintNoLyricsFG.setTextSize(size);
    }

    public void setLabelShownWhenNoLyricsTextColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.highlighted_text_color);
        }
        mPaintNoLyricsFG.setColor(color);
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

    protected final boolean uninitializedOrNoLyrics(LyricMachine machine) {
        return machine == null || mLyricMachine == null || mLyricsModel == null || mLyricsModel.lines == null || mLyricsModel.lines.isEmpty();
    }

    public void requestRefreshUi() {
        LyricMachine machine = this.mLyricMachine;
        if (machine == null) {
            return;
        }
        // Always update progress even UI or data is not ready
        updateByProgress(machine.getCurrentLyricProgress());
    }

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

    private void updateByProgress(long timestamp) {
        mCurrentTime = timestamp;

        LyricModel lyricsModel = this.mLyricsModel;
        if (refreshNoLyrics()) {
            return;
        }

        int line = quickSearchLineByTimestamp(mCurrentTime);

        if (line < 0 || line >= lyricsModel.lines.size()) {
            mForceUpdateUI = UpdateUIType.UpdateUIType_NO_NEED;
            return;
        }

        if (line != mIndexOfCurrentLine) {
            mForceUpdateUI = UpdateUIType.UpdateUIType_NORMAL | UpdateUIType.UpdateUIType_WITH_ANIMATION; // NORMAL & ANIMATION
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
            if (h > 0) {
                if (mBitmapFG == null) {
                    createBitmapFG(w, h);
                } else if (mBitmapFG.getWidth() != w || mBitmapFG.getHeight() != h) {
                    if (!mBitmapFG.isRecycled()) {
                        mBitmapFG.recycle();
                    }

                    createBitmapFG(w, h);
                }

                if (mBitmapBG == null) {
                    createBitmapBG(w, h);
                } else if (mBitmapBG.getWidth() != w || mBitmapBG.getHeight() != h) {
                    if (!mBitmapBG.isRecycled()) {
                        mBitmapBG.recycle();
                    }

                    createBitmapBG(w, h);
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

    private void createBitmapBG(int w, int h) {
        mBitmapBG = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mCanvasBG = new Canvas(mBitmapBG);
    }

    private void createBitmapFG(int w, int h) {
        mBitmapFG = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mCanvasFG = new Canvas(mBitmapFG);
    }

    private boolean isCanvasNotReady() {
        return mBitmapBG == null || mCanvasBG == null || mBitmapFG == null || mCanvasFG == null;
    }

    private int targetIndex = 0;

    private LyricsLineDrawerHelper mReuseLyricsLineDrawerHelper; // reuse when draw highlight part(performance optimization)

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isCanvasNotReady()) { // Fail fast
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
                    mPaintNoLyricsFG,
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
            mBitmapBG.eraseColor(0);
            mBitmapFG.eraseColor(0);
            mPaintBG.setColor(mCurrentLineTextColor);

            LyricsLineDrawerHelper lyricsLineDrawerHelper;
            float y = 0;
            float yReal;
            for (int i = 0; i < lyricsModel.lines.size(); i++) {
                if (i == mIndexOfCurrentLine) {
                    mPaintBG.setTextSize(mCurrentLineTextSize);
                } else if (i < mIndexOfCurrentLine) {
                    mPaintBG.setColor(mPreviousLineTextColor);
                    mPaintBG.setTextSize(mTextSize);
                } else {
                    mPaintBG.setColor(mUpcomingLineTextColor);
                    mPaintBG.setTextSize(mTextSize);
                }

                LyricsLineModel lyricsTargetLineModel = lyricsModel.lines.get(i);
                lyricsLineDrawerHelper = new LyricsLineDrawerHelper(lyricsTargetLineModel, mPaintFG, mPaintBG, getViewportWidth(),
                        mTextGravity);

                yReal = y + mOffset;
                if (i == 0 && yReal > (centerY - getPaddingTop() - (lyricsLineDrawerHelper.getHeight() / 2F))) {
                    // 顶部限制
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
                        if (dr.left == dr.right)
                            continue;

                        mLastRightOfRectClip = mRectClip.right;

                        mRectClip.left = dr.left;
                        mRectClip.top = (int) (dr.top + yReal);
                        mRectClip.right = dr.right;
                        mRectClip.bottom = (int) (dr.bottom + yReal);

                        mCanvasFG.save();
                        mCanvasFG.clipRect(mRectClip);

                        checkIfXTranslationShouldApply(lyricsLineDrawerHelper);

                        mCanvasFG.translate(mCurrentLineTranslateX, yReal);

                        lyricsLineDrawerHelper.drawFG(mCanvasFG);
                        mCanvasFG.restore();
                    }
                }

                // Draw all background lines
                mCanvasBG.save();
                mCanvasBG.translate(i == mIndexOfCurrentLine ? mCurrentLineTranslateX : 0, yReal);
                lyricsLineDrawerHelper.draw(mCanvasBG);
                mCanvasBG.restore();

                if ((y - mLineSpacing + getPaddingTop() + mOffset) <= centerY
                        && centerY <= (y + lyricsLineDrawerHelper.getHeight() + getPaddingTop() + mOffset)) {
                    targetIndex = i;
                }

                y = y + lyricsLineDrawerHelper.getHeight() + mLineSpacing;
                if (y + mOffset > getViewportHeight()) {
                    break;
                }
            }

            canvas.drawBitmap(mBitmapBG, mRectSrc, mRectDst, null);
            canvas.drawBitmap(mBitmapFG, mRectSrc, mRectDst, null);

            canvas.drawLine(0, centerY, getWidth(), centerY + 1, mPaintFG);
        } else {
            LyricsLineModel currentLine = lyricsModel.lines.get(mIndexOfCurrentLine);
            float fraction = 1.0F; // NORMAL UPDATE
            if ((mForceUpdateUI & UpdateUIType.UpdateUIType_NORMAL) == UpdateUIType.UpdateUIType_NORMAL) {
                doConfigCanvasAndTexts(fraction);
                mReuseLyricsLineDrawerHelper = new LyricsLineDrawerHelper(currentLine, mPaintFG, mPaintBG, getViewportWidth(),
                        mTextGravity);

                LyricsLineDrawerHelper finalRefLyricsLineDrawerHelper = mReuseLyricsLineDrawerHelper;
                if ((mForceUpdateUI & UpdateUIType.UpdateUIType_WITH_ANIMATION) == UpdateUIType.UpdateUIType_WITH_ANIMATION) {

                    doScrollWithAnimationTo(mIndexOfCurrentLine, new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
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
                } else if ((mForceUpdateUI & UpdateUIType.UpdateUIType_NORMAL) == UpdateUIType.UpdateUIType_NORMAL) {
                    drawCurrent(finalRefLyricsLineDrawerHelper, fraction);
                    drawPrevious(lyricsModel, finalRefLyricsLineDrawerHelper, fraction);
                    drawUpcoming(lyricsModel, finalRefLyricsLineDrawerHelper, fraction);
                }

                mForceUpdateUI = UpdateUIType.UpdateUIType_NO_NEED;
            }

            if (isLyricsLineOverlong(mReuseLyricsLineDrawerHelper) && !mContentScrolling) {
                mForceUpdateUI = UpdateUIType.UpdateUIType_NORMAL;
            }

            canvas.drawBitmap(mBitmapBG, mRectSrc, mRectDst, null);

            if (lyricsModel.type != LyricType.LRC && !mContentScrolling) {
                drawHighLight(mReuseLyricsLineDrawerHelper, fraction); // Continually to draw the rest part of highlight when scrolling stops
            }

            canvas.drawBitmap(mBitmapFG, mRectSrc, mRectDst, null);
        }

        drawFirstToneIndicator(canvas);
    }

    private void doConfigCanvasAndTexts(float fraction) {
        float textSize = (mCurrentLineTextSize - mTextSize) * fraction;
        if (textSize < 0) {
            textSize = mCurrentLineTextSize; // Supposedly we think `mCurrentLineTextSize` is larger than `mTextSize`
        } else {
            textSize += mTextSize;
        }

        mPaintBG.setColor(mCurrentLineTextColor);
        mPaintBG.setTextSize(textSize);

        mPaintFG.setColor(mCurrentLineHighlightedTextColor);
        mPaintFG.setTextSize(textSize);

        if (mBitmapBG != null) {
            mBitmapBG.eraseColor(0);
        }
    }

    private boolean mContentScrolling = false;
    private static final long MAX_SMOOTH_SCROLL_DURATION = 300; // ms
    private ValueAnimator mScrollingAnimator;

    private boolean doScrollWithAnimationTo(final int position, ValueAnimator.AnimatorUpdateListener updateListener) {
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

        return mContentScrolling;
    }

    private void drawFirstToneIndicator(Canvas canvas) {
        if (!mEnablePreludeEndPositionIndicator) {
            return;
        }
        LyricMachine machine = this.mLyricMachine;
        LyricModel lyricsModel = this.mLyricsModel;
        if (uninitializedOrNoLyrics(machine)) {
            return;
        }

        double countDown = Math.ceil((lyricsModel.preludeEndPosition - mCurrentTime) / 1000.f); // to make start-of-verse
        // indicator animation more
        // smooth
        if (countDown <= 0) {
            return;
        }

        float sY = mPreludeEndPositionIndicatorPaddingTop + mPreludeEndPositionIndicatorRadius; // central of circle
        float sX = getWidth() / 2.f; // central of circle

        canvas.drawCircle(sX - mPreludeEndPositionIndicatorRadius * 3, sY, mPreludeEndPositionIndicatorRadius,
                mPreludeEndPositionIndicator); // Indicator 1

        if ((countDown >= 2 & countDown < 3)) {
            canvas.drawCircle(sX, sY, mPreludeEndPositionIndicatorRadius, mPreludeEndPositionIndicator); // Indicator 2
        } else if ((countDown >= 3)) {
            canvas.drawCircle(sX, sY, mPreludeEndPositionIndicatorRadius, mPreludeEndPositionIndicator); // Indicator 2

            if ((countDown % 2 == 1) || mCurrentTime < 2000L) { // After shown for a little time, then begin to blink
                canvas.drawCircle(sX + 3 * mPreludeEndPositionIndicatorRadius, sY, mPreludeEndPositionIndicatorRadius,
                        mPreludeEndPositionIndicator); // Indicator 3
            }
        }
    }

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
        mPaintBG.setTextSize(mTextSize);
        mPaintBG.setColor(mPreviousLineTextColor);

        mCanvasBG.save();
        mCanvasBG.translate(0, yOfTargetLine);

        for (int i = mIndexOfCurrentLine - 1; i >= 0; i--) {
            lyricsTargetLineModel = lyricsModel.lines.get(i);
            lyricsTargetLineDrawerHelper = new LyricsLineDrawerHelper(lyricsTargetLineModel, mPaintBG, getViewportWidth(), mTextGravity);

            mOffset = mOffset - lyricsTargetLineDrawerHelper.getHeight() - mLineSpacing;

            if (yOfTargetLine - mLineSpacing - lyricsTargetLineDrawerHelper.getHeight() < 0)
                continue;

            float height = mLineSpacing + lyricsTargetLineDrawerHelper.getHeight();
            mCanvasBG.translate(0, -height);
            lyricsTargetLineDrawerHelper.draw(mCanvasBG);
            yOfTargetLine = yOfTargetLine - height;
        }
        mCanvasBG.restore();
    }

    private float mCurrentLineTranslateX = 0;

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

    private void drawCurrent(LyricsLineDrawerHelper currentLineDrawHelper, float fraction) {
        if (currentLineDrawHelper == null) {
            return;
        }

        float yOfTargetLine = (getViewportHeight() - currentLineDrawHelper.getHeight() * fraction) / 2F + mPaddingTop;

        mCanvasBG.save();

        checkIfXTranslationShouldApply(currentLineDrawHelper);
        mCanvasBG.translate(mCurrentLineTranslateX, yOfTargetLine);
        currentLineDrawHelper.draw(mCanvasBG);
        mCanvasBG.restore();

        mOffset = yOfTargetLine;

        mLastRightOfRectClip = mRectClip.right;
    }

    private void drawUpcoming(LyricModel lyricsModel, LyricsLineDrawerHelper currentLineDrawHelper, float fraction) {
        if (!mEnableUpcomingLines) {
            return;
        }

        if (currentLineDrawHelper == null || lyricsModel == null) {
            return;
        }

        float yOfTargetLine = (getViewportHeight() + currentLineDrawHelper.getHeight() * (1 /** from far to near **/ + (1 - fraction))) / 2F + mLineSpacing + mPaddingTop;

        LyricsLineModel lyricsTargetLineModel;
        LyricsLineDrawerHelper lyricsTargetLineDrawerHelper;
        mPaintBG.setTextSize(mTextSize);
        mPaintBG.setColor(mUpcomingLineTextColor);

        mCanvasBG.save();
        mCanvasBG.translate(0, yOfTargetLine);

        for (int i = mIndexOfCurrentLine + 1; i < lyricsModel.lines.size(); i++) {
            lyricsTargetLineModel = lyricsModel.lines.get(i);
            lyricsTargetLineDrawerHelper = new LyricsLineDrawerHelper(lyricsTargetLineModel, mPaintBG, getViewportWidth(), mTextGravity);

            if (yOfTargetLine + lyricsTargetLineDrawerHelper.getHeight() > getViewportHeight())
                break;

            lyricsTargetLineDrawerHelper.draw(mCanvasBG);
            float height = lyricsTargetLineDrawerHelper.getHeight() + mLineSpacing;
            mCanvasBG.translate(0, height);
            yOfTargetLine = yOfTargetLine + height;
        }
        mCanvasBG.restore();
    }

    private void drawHighLight(LyricsLineDrawerHelper currentLineDrawHelper, float fraction) {
        if (currentLineDrawHelper == null) {
            return;
        }

        mBitmapFG.eraseColor(0);

        Rect[] drawRects = currentLineDrawHelper.getDrawRectByTime(mCurrentTime);
        float yOfTargetLine = (getViewportHeight() - currentLineDrawHelper.getHeight() * fraction) / 2F + mPaddingTop;

        for (Rect dr : drawRects) {
            if (dr.left == dr.right)
                continue;

            mRectClip.left = dr.left;
            mRectClip.top = (int) (dr.top + yOfTargetLine);
            mRectClip.right = dr.right;
            mRectClip.bottom = (int) (dr.bottom + yOfTargetLine);

            mCanvasFG.save();
            mCanvasFG.clipRect(mRectClip);
            mCanvasFG.translate(mCurrentLineTranslateX, yOfTargetLine);
            currentLineDrawHelper.drawFG(mCanvasFG);
            mCanvasFG.restore();
        }
    }

    public void attachToLyricMachine(LyricMachine machine) {
        if (!machine.isReady()) {
            throw new IllegalStateException("Must call ScoringMachine.prepare before attaching");
        }
        this.mLyricMachine = machine;
        this.mLyricsModel = machine.getLyricsModel();

        // Update values from UI view if necessary
    }

    /**
     * 重置内部状态，清空已经加载的歌词
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

    private void performInvalidateIfNecessary() {
        long delta = System.currentTimeMillis() - mLastViewInvalidateTs;
        if (delta <= 16) {
            return;
        }

        invalidateForSureAndMarkTheTimestamp();
    }

    private void invalidateForSureAndMarkTheTimestamp() {
        // Try to avoid too many `invalidate` operations, it is expensive
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            postInvalidate();
        } else {
            invalidate();
        }

        mLastViewInvalidateTs = System.currentTimeMillis();
    }

    private void resetInternal() {
        mLyricMachine = null;
        mLyricsModel = null;
        mIndexOfCurrentLine = 0;
        mForceUpdateUI = UpdateUIType.UpdateUIType_NORMAL;
        mCurrentTime = 0;
        mOffset = 0;
        targetIndex = 0;
        mDuration = 0;
        mRectClip.setEmpty();
        mLastRightOfRectClip = mRectClip.right;
        mCurrentLineTranslateX = 0;
    }

    /**
     * 二分法查找当前时间应该显示的行数（最后一个 <= time 的行数）
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

    private int getViewportWidth() {
        return Math.max(getWidth() - getPaddingStart() - getPaddingEnd(), 0);
    }

    private int getViewportHeight() {
        return Math.max((getHeight() - getPaddingTop() - getPaddingBottom()), 0);
    }

    private boolean isLyricsLineOverlong(LyricsLineDrawerHelper lyricsLineDrawerHelper) {
        return lyricsLineDrawerHelper != null && lyricsLineDrawerHelper.getWidth() > getViewportWidth();
    }

    @MainThread
    public interface OnLyricsSeekListener {
        void onProgressChanged(long progress);

        void onStartTrackingTouch();

        void onStopTrackingTouch();
    }
}
