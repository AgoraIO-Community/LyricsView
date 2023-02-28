package io.agora.karaoke_view.v11;

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

import io.agora.karaoke_view.R;
import io.agora.karaoke_view.v11.internal.ScoringMachine;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;

/**
 * 歌词视图
 * 主要负责歌词的显示，支持上下拖动调整进度。
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/7/6
 */
@SuppressLint("StaticFieldLeak")
public class LyricsView extends View {
    private static final String TAG = "LrcView";

    private volatile LyricsModel mLyricsModel;

    private ScoringMachine mScoringMachine;

    private final TextPaint mPaintNoLyricsFG = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mPaintFG = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mPaintBG = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private int mPastTextColor;
    private int mUpcomingTextColor;
    private float mDefaultTextSize;

    private int mCurrentLineTextColor;
    private float mCurrentTextSize;
    private int mCurrentHighlightedTextColor;

    private float mLineSpacing;
    private float mPaddingTop;
    private String mNoLyricsLabel;
    private int mCurrentLine = 0;

    private boolean mEnableStartOfVerseIndicator = true;
    private float mStartOfVerseIndicatorPaddingTop;
    private float mStartOfVerseIndicatorRadius;
    private int mStartOfVerseIndicatorColor;
    private final TextPaint mStartOfVerseIndicator = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /**
     * 歌词显示位置，靠左/居中/靠右
     */
    private LyricsLineDrawerHelper.Gravity mTextGravity;

    private boolean mNewLine = true;

    private final Rect mRectClip = new Rect();
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
    private volatile boolean isUnderDragging = false;
    private GestureDetector mGestureDetector;
    private float mOffset;
    private final GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            isUnderDragging = true;

            if (mOnSeekActionListener != null) {
                mOnSeekActionListener.onStartTrackingTouch();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mOffset += -distanceY;
            tryInvalidate();
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
        mDefaultTextSize = ta.getDimension(R.styleable.LyricsView_defaultTextSize, getResources().getDimension(R.dimen.default_text_size));
        mCurrentTextSize = ta.getDimension(R.styleable.LyricsView_currentTextSize, getResources().getDimension(R.dimen.current_text_size));
        if (mCurrentTextSize == 0) {
            mCurrentTextSize = mDefaultTextSize;
        }

        mLineSpacing = ta.getDimension(R.styleable.LyricsView_lineSpacing, getResources().getDimension(R.dimen.line_spacing));
        mPaddingTop = ta.getDimension(R.styleable.LyricsView_paddingTop, getResources().getDimension(R.dimen.padding_top));

        mCurrentLineTextColor = ta.getColor(R.styleable.LyricsView_currentTextColor, Color.WHITE);
        mPastTextColor = ta.getColor(R.styleable.LyricsView_pastTextColor, getResources().getColor(R.color.past_text_color));
        mUpcomingTextColor = ta.getColor(R.styleable.LyricsView_upcomingTextColor, getResources().getColor(R.color.upcoming_text_color));
        mCurrentHighlightedTextColor = ta.getColor(R.styleable.LyricsView_currentHighlightedTextColor, getResources().getColor(R.color.highlighted_text_color));

        mNoLyricsLabel = ta.getString(R.styleable.LyricsView_labelWhenNoLyrics);
        mNoLyricsLabel = TextUtils.isEmpty(mNoLyricsLabel) ? getContext().getString(R.string.no_lyrics_label) : mNoLyricsLabel;
        int textGravity = ta.getInteger(R.styleable.LyricsView_textGravity, 0);
        mTextGravity = LyricsLineDrawerHelper.Gravity.parse(textGravity);
        mEnableDragging = ta.getBoolean(R.styleable.LyricsView_enableDragging, false);

        mStartOfVerseIndicatorPaddingTop = ta.getDimension(R.styleable.LyricsView_startOfVerseIndicatorPaddingTop, getResources().getDimension(R.dimen.start_of_verse_indicator_padding_top));
        mStartOfVerseIndicatorRadius = ta.getDimension(R.styleable.LyricsView_startOfVerseIndicatorRadius, getResources().getDimension(R.dimen.start_of_verse_indicator_radius));
        mStartOfVerseIndicatorColor = ta.getColor(R.styleable.LyricsView_startOfVerseIndicatorColor, getResources().getColor(R.color.default_popular_color));

        ta.recycle();

        mStartOfVerseIndicator.setColor(mStartOfVerseIndicatorColor);
        mStartOfVerseIndicator.setAntiAlias(true);

        mPaintNoLyricsFG.setTextSize(mCurrentTextSize);
        mPaintNoLyricsFG.setColor(mCurrentHighlightedTextColor);
        mPaintNoLyricsFG.setAntiAlias(true);
        mPaintNoLyricsFG.setTextAlign(Paint.Align.LEFT);

        mPaintFG.setTextSize(mCurrentTextSize);
        mPaintFG.setColor(mCurrentHighlightedTextColor);
        mPaintFG.setAntiAlias(true);
        mPaintFG.setTextAlign(Paint.Align.LEFT);

        mPaintBG.setTextSize(mCurrentTextSize);
        mPaintBG.setColor(mCurrentLineTextColor);
        mPaintBG.setAntiAlias(true);
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

        if (!hasLrc()) {
            return super.onTouchEvent(event);
        }
        LyricsModel lyricsModel = mLyricsModel;

        if (targetIndex < 0 || lyricsModel.lines.size() <= targetIndex) {
            return super.onTouchEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            isUnderDragging = false;
            mNewLine = true;
            mRectClip.setEmpty();

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

    public void setPastTextColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.past_text_color);
        }

        mPastTextColor = color;

        tryInvalidate();
    }

    public void setUpcomingTextColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.upcoming_text_color);
        }

        mUpcomingTextColor = color;

        tryInvalidate();
    }

    /**
     * 设置普通歌词文本字体大小
     */
    public void setDefaultTextSize(float size) {
        mDefaultTextSize = size;
        mNewLine = true;
        tryInvalidate();
    }

    /**
     * 设置当前行歌词文本字体大小
     */
    public void setCurrentTextSize(float size) {
        mCurrentTextSize = size;
        mNewLine = true;
        tryInvalidate();
    }

    /**
     * 设置当前行歌词字体颜色
     */
    public void setCurrentTextColor(@ColorInt int color) {
        if (color == 0) {
            color = Color.WHITE;
        }
        mCurrentLineTextColor = color;
        mPaintBG.setColor(mCurrentLineTextColor);
        mNewLine = true;
        tryInvalidate();
    }

    /**
     * 设置当前行高亮歌词字体颜色
     */
    public void setCurrentHighlightedTextColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.highlighted_text_color);
        }
        mCurrentHighlightedTextColor = color;
        mPaintFG.setColor(mCurrentHighlightedTextColor);
        mNewLine = true;
        tryInvalidate();
    }

    /**
     * 设置歌词为空时屏幕中央显示的文字，如 “暂无歌词”
     */
    public void setLabelShownWhenNoLyrics(String label) {
        mNoLyricsLabel = label;
        tryInvalidate();
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

    public void enableStartOfVerseIndicator(boolean enable) {
        this.mEnableStartOfVerseIndicator = enable;
    }

    public void setStartOfVerseIndicatorPaddingTop(float paddingTop) {
        this.mStartOfVerseIndicatorPaddingTop = paddingTop;
    }

    public void setStartOfVerseIndicatorRadius(float radius) {
        this.mStartOfVerseIndicatorRadius = radius;
    }

    public void setStartOfVerseIndicatorColor(int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.default_popular_color);
        }
        this.mStartOfVerseIndicatorColor = color;
        this.mStartOfVerseIndicator.setColor(color);
    }

    /**
     * 歌词是否有效
     *
     * @return true，如果歌词有效，否则 false
     */
    private boolean hasLrc() {
        return mLyricsModel != null && mLyricsModel.lines != null && !mLyricsModel.lines.isEmpty();
    }

    public void requestRefreshUi() {
        if (mScoringMachine == null) {
            return;
        }

        updateByProgress(mScoringMachine.getCurrentTimestamp());
    }

    private void updateByProgress(long timestamp) {
        mCurrentTime = timestamp;

        if (!hasLrc()) {
            if ((mCurrentTime / 1000) % 2 == 0) {
                tryInvalidate();
            }
            return;
        }

        int line = findShowLine(mCurrentTime);
        if (line != mCurrentLine) {
            mNewLine = true;
            mCurrentLine = line;
        }

        tryInvalidate();
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
            mRectSrc.right = getLrcWidth();
            mRectSrc.bottom = getLrcHeight();

            mRectDst.left = getPaddingStart();
            mRectDst.top = getPaddingTop();
            mRectDst.right = getPaddingStart() + getLrcWidth();
            mRectDst.bottom = getPaddingTop() + getLrcHeight();

            tryInvalidate();
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

    private LyricsLineDrawerHelper mLyricsLineDrawerHelper;
    private int targetIndex = 0;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isCanvasNotReady()) { // Fail fast
            return;
        }

        // 无歌词文件
        if (!hasLrc()) {
            int width = getLrcWidth();
            int height = getLrcHeight();
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

        LyricsModel lyricsModel = mLyricsModel;

        float centerY = getLrcHeight() / 2F + getPaddingTop() + mPaddingTop;
        if (isUnderDragging) {
            // 拖动状态下
            mBitmapBG.eraseColor(0);
            mBitmapFG.eraseColor(0);
            mPaintBG.setColor(mCurrentLineTextColor);

            LyricsLineDrawerHelper lyricsLineDrawerHelper;
            float y = 0;
            float yReal;
            for (int i = 0; i < lyricsModel.lines.size(); i++) {
                if (i == mCurrentLine) {
                    mPaintBG.setTextSize(mCurrentTextSize);
                } else if (i < mCurrentLine) {
                    mPaintBG.setColor(mPastTextColor);
                    mPaintBG.setTextSize(mDefaultTextSize);
                } else {
                    mPaintBG.setColor(mUpcomingTextColor);
                    mPaintBG.setTextSize(mDefaultTextSize);
                }

                LyricsLineModel mIEntry = lyricsModel.lines.get(i);
                lyricsLineDrawerHelper = new LyricsLineDrawerHelper(mIEntry, mPaintFG, mPaintBG, getLrcWidth(), mTextGravity);

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

                mCanvasBG.save();
                mCanvasBG.translate(0, yReal);
                lyricsLineDrawerHelper.draw(mCanvasBG);
                mCanvasBG.restore();

                if (i == mCurrentLine) {
                    Rect[] drawRects = lyricsLineDrawerHelper.getDrawRectByTime(mCurrentTime);

                    for (Rect dr : drawRects) {
                        if (dr.left == dr.right)
                            continue;

                        mRectClip.left = dr.left;
                        mRectClip.top = (int) (dr.top + yReal);
                        mRectClip.right = dr.right;
                        mRectClip.bottom = (int) (dr.bottom + yReal);

                        mCanvasFG.save();
                        mCanvasFG.clipRect(mRectClip);
                        mCanvasFG.translate(0, yReal);
                        lyricsLineDrawerHelper.drawFG(mCanvasFG);
                        mCanvasFG.restore();
                    }
                }

                if ((y - mLineSpacing + getPaddingTop() + mOffset) <= centerY
                        && centerY <= (y + lyricsLineDrawerHelper.getHeight() + getPaddingTop() + mOffset)) {
                    targetIndex = i;
                }

                y = y + lyricsLineDrawerHelper.getHeight() + mLineSpacing;
                if (y + mOffset > getLrcHeight()) {
                    break;
                }
            }

            canvas.drawBitmap(mBitmapBG, mRectSrc, mRectDst, null);
            canvas.drawBitmap(mBitmapFG, mRectSrc, mRectDst, null);

            canvas.drawLine(0, centerY, getWidth(), centerY + 1, mPaintFG);
        } else {
            LyricsLineModel currentLine = lyricsModel.lines.get(mCurrentLine);
            if (mNewLine) {
                mPaintBG.setColor(mCurrentLineTextColor);
                mPaintBG.setTextSize(mCurrentTextSize);

                mPaintFG.setColor(mCurrentHighlightedTextColor);
                mPaintFG.setTextSize(mCurrentTextSize);

                mLyricsLineDrawerHelper = new LyricsLineDrawerHelper(currentLine, mPaintFG, mPaintBG, getLrcWidth(), mTextGravity);

                // clear bitmap
                if (mBitmapBG != null) {
                    mBitmapBG.eraseColor(0);
                }

                if (mCurrentLine < 0 || mCurrentLine >= lyricsModel.lines.size()) {
                    mNewLine = false;
                    return;
                }

                drawCurrent();
                drawTop(lyricsModel);
                drawBottom(lyricsModel);

                mNewLine = false;
            }

            canvas.drawBitmap(mBitmapBG, mRectSrc, mRectDst, null);

            drawHighLight();
            canvas.drawBitmap(mBitmapFG, mRectSrc, mRectDst, null);
        }

        drawFirstToneIndicator(canvas);
    }

    private void drawFirstToneIndicator(Canvas canvas) {
        if (!mEnableStartOfVerseIndicator) {
            return;
        }
        if (!hasLrc()) {
            return;
        }
        LyricsModel lyricsModel = mLyricsModel;

        double countDown = Math.ceil((lyricsModel.startOfVerse - mCurrentTime) / 1000.f); // to make start-of-verse indicator animation more smooth
        if (countDown <= 0) {
            return;
        }

        float sY = mStartOfVerseIndicatorPaddingTop + mStartOfVerseIndicatorRadius; // central of circle
        float sX = getWidth() / 2.f; // central of circle

        canvas.drawCircle(sX - mStartOfVerseIndicatorRadius * 3, sY, mStartOfVerseIndicatorRadius, mStartOfVerseIndicator); // Indicator 1

        if ((countDown >= 2 & countDown < 3)) {
            canvas.drawCircle(sX, sY, mStartOfVerseIndicatorRadius, mStartOfVerseIndicator); // Indicator 2
        } else if ((countDown >= 3)) {
            canvas.drawCircle(sX, sY, mStartOfVerseIndicatorRadius, mStartOfVerseIndicator); // Indicator 2

            if ((countDown % 2 == 1) || mCurrentTime < 2000L) { // After shown for a little time, then begin to blink
                canvas.drawCircle(sX + 3 * mStartOfVerseIndicatorRadius, sY, mStartOfVerseIndicatorRadius, mStartOfVerseIndicator); // Indicator 3
            }
        }
    }

    private void drawTop(LyricsModel lyricsModel) {
        if (mLyricsLineDrawerHelper == null || lyricsModel == null) {
            return;
        }

        float curPointY = (getLrcHeight() - mLyricsLineDrawerHelper.getHeight()) / 2F + mPaddingTop;
        float y;
        LyricsLineModel line;
        LyricsLineDrawerHelper mLrcEntry;
        mPaintBG.setTextSize(mDefaultTextSize);
        mPaintBG.setColor(mPastTextColor);

        mCanvasBG.save();
        mCanvasBG.translate(0, curPointY);

        for (int i = mCurrentLine - 1; i >= 0; i--) {
            line = lyricsModel.lines.get(i);
            mLrcEntry = new LyricsLineDrawerHelper(line, mPaintBG, getLrcWidth(), mTextGravity);

            mOffset = mOffset - mLrcEntry.getHeight() - mLineSpacing;

            if (curPointY - mLineSpacing - mLrcEntry.getHeight() < 0)
                continue;

            y = mLineSpacing + mLrcEntry.getHeight();
            mCanvasBG.translate(0, -y);
            mLrcEntry.draw(mCanvasBG);
            curPointY = curPointY - y;
        }
        mCanvasBG.restore();
    }

    private void drawCurrent() {
        if (mLyricsLineDrawerHelper == null) {
            return;
        }

        float y = (getLrcHeight() - mLyricsLineDrawerHelper.getHeight()) / 2F + mPaddingTop;
        mCanvasBG.save();
        mCanvasBG.translate(0, y);
        mLyricsLineDrawerHelper.draw(mCanvasBG);
        mCanvasBG.restore();

        mOffset = y;
    }

    private void drawBottom(LyricsModel lyricsModel) {
        if (mLyricsLineDrawerHelper == null || lyricsModel == null) {
            return;
        }

        float curPointY = (getLrcHeight() + mLyricsLineDrawerHelper.getHeight()) / 2F + mLineSpacing + mPaddingTop;
        float y;
        LyricsLineModel data;
        LyricsLineDrawerHelper mLrcEntry;
        mPaintBG.setTextSize(mDefaultTextSize);
        mPaintBG.setColor(mUpcomingTextColor);

        mCanvasBG.save();
        mCanvasBG.translate(0, curPointY);

        for (int i = mCurrentLine + 1; i < lyricsModel.lines.size(); i++) {
            data = lyricsModel.lines.get(i);
            mLrcEntry = new LyricsLineDrawerHelper(data, mPaintBG, getLrcWidth(), mTextGravity);

            if (curPointY + mLrcEntry.getHeight() > getLrcHeight())
                break;

            mLrcEntry.draw(mCanvasBG);
            y = mLrcEntry.getHeight() + mLineSpacing;
            mCanvasBG.translate(0, y);
            curPointY = curPointY + y;
        }
        mCanvasBG.restore();
    }

    private void drawHighLight() {
        if (mLyricsLineDrawerHelper == null) {
            return;
        }

        mBitmapFG.eraseColor(0);

        Rect[] drawRects = mLyricsLineDrawerHelper.getDrawRectByTime(mCurrentTime);
        float y = (getLrcHeight() - mLyricsLineDrawerHelper.getHeight()) / 2F + mPaddingTop;

        for (Rect dr : drawRects) {
            if (dr.left == dr.right)
                continue;

            mRectClip.left = dr.left;
            mRectClip.top = (int) (dr.top + y);
            mRectClip.right = dr.right;
            mRectClip.bottom = (int) (dr.bottom + y);

            mCanvasFG.save();
            mCanvasFG.clipRect(mRectClip);
            mCanvasFG.translate(0, y);
            mLyricsLineDrawerHelper.drawFG(mCanvasFG);
            mCanvasFG.restore();
        }
    }

    public void attachToScoringMachine(ScoringMachine machine) {
        if (!machine.isReady()) {
            throw new IllegalStateException("Must call ScoringMachine.prepare before attaching");
        }
        this.mScoringMachine = machine;
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

    private void tryInvalidate() {
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
        mLyricsModel = null;
        mCurrentLine = 0;
        mNewLine = true;
        mCurrentTime = 0;
        mOffset = 0;
        targetIndex = 0;
        mDuration = 0;
    }

    /**
     * 二分法查找当前时间应该显示的行数（最后一个 <= time 的行数）
     */
    private int findShowLine(long time) {
        if (!hasLrc()) {
            return 0;
        }

        int left = 0;
        int right = mLyricsModel.lines.size();
        while (left <= right) {
            int middle = (left + right) / 2;
            long middleTime = mLyricsModel.lines.get(middle).getStartTime();

            if (time < middleTime) {
                right = middle - 1;
            } else {
                if (middle + 1 >= mLyricsModel.lines.size() || time < mLyricsModel.lines.get(middle + 1).getStartTime()) {
                    return middle;
                }

                left = middle + 1;
            }
        }

        return 0;
    }

    private int getLrcWidth() {
        return Math.max(getWidth() - getPaddingStart() - getPaddingEnd(), 0);
    }

    private int getLrcHeight() {
        return Math.max((getHeight() - getPaddingTop() - getPaddingBottom()), 0);
    }

    @MainThread
    public interface OnLyricsSeekListener {
        /**
         * 进度条改变回调
         *
         * @param time 毫秒
         */
        void onProgressChanged(long time);

        /**
         * 开始拖动
         */
        void onStartTrackingTouch();

        /**
         * 结束拖动
         */
        void onStopTrackingTouch();
    }
}
