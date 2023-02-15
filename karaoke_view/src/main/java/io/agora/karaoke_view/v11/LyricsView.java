package io.agora.karaoke_view.v11;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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

import java.util.List;

import io.agora.karaoke_view.R;
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

    private static volatile LyricsModel lrcData;

    private final TextPaint mPaintFG = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mPaintBG = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private int mNormalTextColor;
    private int mPastTextColor;
    private int mFutureTextColor;
    private float mNormalTextSize;
    private int mCurrentTextColor;
    private float mCurrentTextSize;
    private float mLineSpacing;
    private float mMarginTop;
    private String mDefaultLabel;
    private int mCurrentLine = 0;

    private float mFirstToneStartIndicatorPaddingTop;
    private float mFirstToneStartIndicatorRadius;

    /**
     * 歌词显示位置，靠左/居中/靠右
     */
    private LyricsLineDrawerHelper.Gravity mTextGravity;

    private boolean mNewLine = true;

    private final Rect mRectClip = new Rect();
    private final Rect mRectSrc = new Rect();
    private final Rect mRectDst = new Rect();

    private long mCurrentTime = 0;
    private Long mTotalDuration;

    private Bitmap mBitmapBG;
    private Canvas mCanvasBG;

    private Bitmap mBitmapFG;
    private Canvas mCanvasFG;

    private OnLyricsSeekListener mOnSeekActionListener;
    private boolean enableDrag = true;
    private volatile boolean isInDrag = false;
    private GestureDetector mGestureDetector;
    private float mOffset;
    private final GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            isInDrag = true;

            if (mOnSeekActionListener != null) {
                mOnSeekActionListener.onStartTrackingTouch();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mOffset += -distanceY;
            invalidate();
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

    private void init(AttributeSet attrs) {
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.LyricsView);
        mCurrentTextSize = ta.getDimension(R.styleable.LyricsView_lrcTextSize, getResources().getDimension(R.dimen.lrc_text_size));
        mNormalTextSize = ta.getDimension(R.styleable.LyricsView_lrcNormalTextSize, getResources().getDimension(R.dimen.lrc_text_size));
        if (mNormalTextSize == 0) {
            mNormalTextSize = mCurrentTextSize;
        }

        mLineSpacing = ta.getDimension(R.styleable.LyricsView_lineSpacing, getResources().getDimension(R.dimen.line_spacing));
        mMarginTop = ta.getDimension(R.styleable.LyricsView_lrcMarginTop, getResources().getDimension(R.dimen.lrc_margin_top));
        mNormalTextColor = ta.getColor(R.styleable.LyricsView_lrcNormalTextColor, getResources().getColor(R.color.lrc_normal_text_color));
        mPastTextColor = ta.getColor(R.styleable.LyricsView_lrcPastTextColor, getResources().getColor(R.color.lrc_normal_text_color));
        mFutureTextColor = ta.getColor(R.styleable.LyricsView_lrcFutureTextColor, getResources().getColor(R.color.lrc_normal_text_color));
        mCurrentTextColor = ta.getColor(R.styleable.LyricsView_lrcCurrentTextColor, getResources().getColor(R.color.lrc_current_text_color));
        mDefaultLabel = ta.getString(R.styleable.LyricsView_lrcLabel);
        mDefaultLabel = TextUtils.isEmpty(mDefaultLabel) ? getContext().getString(R.string.lrc_label) : mDefaultLabel;
        int lrcTextGravity = ta.getInteger(R.styleable.LyricsView_lrcTextGravity, 0);
        mTextGravity = LyricsLineDrawerHelper.Gravity.parse(lrcTextGravity);
        enableDrag = ta.getBoolean(R.styleable.LyricsView_lrcEnableDrag, true);

        mFirstToneStartIndicatorPaddingTop = ta.getDimension(R.styleable.LyricsView_firstToneStartIndicatorPaddingTop, getResources().getDimension(R.dimen.first_tone_start_indicator_padding_top));
        mFirstToneStartIndicatorRadius = ta.getDimension(R.styleable.LyricsView_firstToneStartIndicatorRadius, getResources().getDimension(R.dimen.first_tone_start_indicator_radius));

        ta.recycle();

        mPaintFG.setTextSize(mCurrentTextSize);
        mPaintFG.setColor(mCurrentTextColor);
        mPaintFG.setAntiAlias(true);
        mPaintFG.setTextAlign(Paint.Align.LEFT);

        mPaintBG.setTextSize(mNormalTextSize);
        mPaintBG.setColor(mNormalTextColor);
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
        if (!enableDrag) {
            return super.onTouchEvent(event);
        }

        if (lrcData == null || lrcData.lines == null || lrcData.lines.isEmpty()) {
            return super.onTouchEvent(event);
        }

        if (targetIndex < 0 || lrcData.lines.size() <= targetIndex) {
            return super.onTouchEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            isInDrag = false;
            mNewLine = true;
            mRectClip.setEmpty();

            LyricsLineModel mIEntry = lrcData.lines.get(targetIndex);
            updateTime(mIEntry.getStartTime());

            if (mOnSeekActionListener != null) {
                mOnSeekActionListener.onProgressChanged(mIEntry.getStartTime());
                mOnSeekActionListener.onStopTrackingTouch();
            }
        }
        return mGestureDetector.onTouchEvent(event);
    }

    /**
     * 设置是否允许上下滑动
     *
     * @param enableDrag
     */
    public void setEnableDrag(boolean enableDrag) {
        this.enableDrag = enableDrag;
    }

    /**
     * 设置音乐总长度，单位毫秒
     *
     * @param duration 时间，单位毫秒
     */
    public synchronized void setTotalDuration(long duration) {
        mTotalDuration = duration;

        if (lrcData != null && lrcData.lines != null && !lrcData.lines.isEmpty()) {
            List<LyricsLineModel.Tone> tones = lrcData.lines.get(lrcData.lines.size() - 1).tones; // Last line

            tones = lrcData.lines.get(0).tones; // First line
            if (tones != null && !tones.isEmpty()) {
                mTimestampForFirstTone = tones.get(0).begin; // find the first tone timestamp
            }
        }
    }

    private long mTimestampForFirstTone = -1;

    /**
     * setLrcData(LrcData data) 以及 setTotalDuration(long duration) 调用完毕之后可以通过该方法查询
     *
     * @return mTimestampForFirstTone <= 0 通常表示歌词设置失败或者歌词内容不正确没有解析出来
     * mTimestampForFirstTone > 0 表示歌曲第一句开始时间
     */
    public double getFirstToneBeginPosition() {
        return mTimestampForFirstTone;
    }

    /**
     * 设置非当前行歌词字体颜色
     */
    public void setNormalColor(@ColorInt int normalColor) {
        mNormalTextColor = normalColor;
        mPaintBG.setColor(mNormalTextColor);
        mNewLine = true;
        invalidate();
    }

    /**
     * 普通歌词文本字体大小
     */
    public void setNormalTextSize(float size) {
        mNormalTextSize = size;
        mNewLine = true;
        invalidate();
    }

    /**
     * 当前歌词文本字体大小
     */
    public void setCurrentTextSize(float size) {
        mCurrentTextSize = size;
        mNewLine = true;
        invalidate();
    }

    /**
     * 设置当前行歌词的字体颜色
     */
    public void setCurrentColor(@ColorInt int currentColor) {
        mCurrentTextColor = currentColor;
        mPaintFG.setColor(mCurrentTextColor);
        mNewLine = true;
        invalidate();
    }

    /**
     * 设置歌词为空时屏幕中央显示的文字，如 “暂无歌词”
     */
    public void setLabel(String label) {
        mDefaultLabel = label;
        invalidate();
    }

    public void setLineSpacing(float lineSpacing) {
        this.mLineSpacing = lineSpacing;
    }

    /**
     * 歌词是否有效
     *
     * @return true，如果歌词有效，否则 false
     */
    private boolean hasLrc() {
        return lrcData != null && lrcData.lines != null && !lrcData.lines.isEmpty();
    }

    /**
     * 更新进度，单位毫秒
     *
     * @param time 当前播放时间，毫秒
     */
    public void updateTime(long time) {
        if (!hasLrc()) {
            return;
        }

        mCurrentTime = time;

        int line = findShowLine(time);
        if (line != mCurrentLine) {
            mNewLine = true;
            mCurrentLine = line;
        }

        invalidate();
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

            invalidate();
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

    private LyricsLineDrawerHelper curLrcEntry;
    private int targetIndex = 0;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 无歌词文件
        if (!hasLrc()) {
            int width = getLrcWidth();
            int height = getLrcHeight();
            if (width == 0 || height == 0) {
                return;
            }
            @SuppressLint("DrawAllocation")
            StaticLayout staticLayout = new StaticLayout(
                    mDefaultLabel,
                    mPaintFG,
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

        float centerY = getLrcHeight() / 2F + getPaddingTop() + mMarginTop;
        if (isInDrag) {
            // 拖动状态下
            mBitmapBG.eraseColor(0);
            mBitmapFG.eraseColor(0);
            mPaintBG.setColor(mNormalTextColor);

            LyricsLineDrawerHelper mLyricsLineDrawerHelper;
            float y = 0;
            float yReal;
            for (int i = 0; i < lrcData.lines.size(); i++) {
                if (i == mCurrentLine) {
                    mPaintBG.setTextSize(mCurrentTextSize);
                } else if (i < mCurrentLine) {
                    mPaintBG.setColor(mPastTextColor);
                    mPaintBG.setTextSize(mNormalTextSize);
                } else {
                    mPaintBG.setColor(mFutureTextColor);
                    mPaintBG.setTextSize(mNormalTextSize);
                }

                LyricsLineModel mIEntry = lrcData.lines.get(i);
                mLyricsLineDrawerHelper = new LyricsLineDrawerHelper(mIEntry, mPaintFG, mPaintBG, getLrcWidth(), mTextGravity);

                yReal = y + mOffset;
                if (i == 0 && yReal > (centerY - getPaddingTop() - (mLyricsLineDrawerHelper.getHeight() / 2F))) {
                    // 顶部限制
                    mOffset = centerY - getPaddingTop() - (mLyricsLineDrawerHelper.getHeight() / 2F);
                    yReal = y + mOffset;
                }

                if (yReal + mLyricsLineDrawerHelper.getHeight() < 0) {
                    y = y + mLyricsLineDrawerHelper.getHeight() + mLineSpacing;
                    continue;
                }

                mCanvasBG.save();
                mCanvasBG.translate(0, yReal);
                mLyricsLineDrawerHelper.draw(mCanvasBG);
                mCanvasBG.restore();

                if (i == mCurrentLine) {
                    Rect[] drawRects = mLyricsLineDrawerHelper.getDrawRectByTime(mCurrentTime);

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
                        mLyricsLineDrawerHelper.drawFG(mCanvasFG);
                        mCanvasFG.restore();
                    }
                }

                if ((y - mLineSpacing + getPaddingTop() + mOffset) <= centerY
                        && centerY <= (y + mLyricsLineDrawerHelper.getHeight() + getPaddingTop() + mOffset)) {
                    targetIndex = i;
                }

                y = y + mLyricsLineDrawerHelper.getHeight() + mLineSpacing;
                if (y + mOffset > getLrcHeight()) {
                    break;
                }
            }

            canvas.drawBitmap(mBitmapBG, mRectSrc, mRectDst, null);
            canvas.drawBitmap(mBitmapFG, mRectSrc, mRectDst, null);

            canvas.drawLine(0, centerY, getWidth(), centerY + 1, mPaintFG);
        } else {
            LyricsLineModel cur = lrcData.lines.get(mCurrentLine);
            if (mNewLine) {
                mPaintBG.setColor(mNormalTextColor);
                mPaintBG.setTextSize(mCurrentTextSize);

                curLrcEntry = new LyricsLineDrawerHelper(cur, mPaintFG, mPaintBG, getLrcWidth(), mTextGravity);

                // clear bitmap
                if (mBitmapBG != null) {
                    mBitmapBG.eraseColor(0);
                }

                if (mCurrentLine < 0 || mCurrentLine >= lrcData.lines.size()) {
                    mNewLine = false;
                    return;
                }

                drawCurrent();
                drawTop();
                drawBottom();

                mNewLine = false;
            }

            canvas.drawBitmap(mBitmapBG, mRectSrc, mRectDst, null);

            drawHighLight();
            canvas.drawBitmap(mBitmapFG, mRectSrc, mRectDst, null);
        }

        drawFirstToneIndicator(canvas);
    }

    private void drawFirstToneIndicator(Canvas canvas) {
        double countDown = Math.ceil((mTimestampForFirstTone - mCurrentTime) / 1000.f); // to make first tone indicator animation more smooth
        if (countDown <= 0) {
            return;
        }

        float sY = mFirstToneStartIndicatorPaddingTop + mFirstToneStartIndicatorRadius; // central of circle
        float sX = getWidth() / 2.f; // central of circle

        canvas.drawCircle(sX - mFirstToneStartIndicatorRadius * 3, sY, mFirstToneStartIndicatorRadius, mPaintBG); // Indicator 1

        if ((countDown >= 2 & countDown < 3)) {
            canvas.drawCircle(sX, sY, mFirstToneStartIndicatorRadius, mPaintBG); // Indicator 2
        } else if ((countDown >= 3)) {
            canvas.drawCircle(sX, sY, mFirstToneStartIndicatorRadius, mPaintBG); // Indicator 2

            if ((countDown % 2 == 1) || mCurrentTime < 2000L) { // After shown for a little time, then begin to blink
                canvas.drawCircle(sX + 3 * mFirstToneStartIndicatorRadius, sY, mFirstToneStartIndicatorRadius, mPaintBG); // Indicator 3
            }
        }
    }

    private void drawTop() {
        if (curLrcEntry == null) {
            return;
        }

        float curPointY = (getLrcHeight() - curLrcEntry.getHeight()) / 2F + mMarginTop;
        float y;
        LyricsLineModel line;
        LyricsLineDrawerHelper mLrcEntry;
        mPaintBG.setTextSize(mNormalTextSize);
        mPaintBG.setColor(mPastTextColor);

        mCanvasBG.save();
        mCanvasBG.translate(0, curPointY);

        for (int i = mCurrentLine - 1; i >= 0; i--) {
            line = lrcData.lines.get(i);
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
        if (curLrcEntry == null) {
            return;
        }

        float y = (getLrcHeight() - curLrcEntry.getHeight()) / 2F + mMarginTop;
        mCanvasBG.save();
        mCanvasBG.translate(0, y);
        curLrcEntry.draw(mCanvasBG);
        mCanvasBG.restore();

        mOffset = y;
    }

    private void drawBottom() {
        if (curLrcEntry == null) {
            return;
        }

        float curPointY = (getLrcHeight() + curLrcEntry.getHeight()) / 2F + mLineSpacing + mMarginTop;
        float y;
        LyricsLineModel data;
        LyricsLineDrawerHelper mLrcEntry;
        mPaintBG.setTextSize(mNormalTextSize);
        mPaintBG.setColor(mFutureTextColor);

        mCanvasBG.save();
        mCanvasBG.translate(0, curPointY);

        for (int i = mCurrentLine + 1; i < lrcData.lines.size(); i++) {
            data = lrcData.lines.get(i);
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
        if (curLrcEntry == null) {
            return;
        }

        mBitmapFG.eraseColor(0);

        Rect[] drawRects = curLrcEntry.getDrawRectByTime(mCurrentTime);
        float y = (getLrcHeight() - curLrcEntry.getHeight()) / 2F + mMarginTop;

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
            curLrcEntry.drawFG(mCanvasFG);
            mCanvasFG.restore();
        }
    }

    public void setLrcData(LyricsModel data) {
        resetInternal();

        lrcData = data;

        invalidate();
    }

    /**
     * 重置内部状态，清空已经加载的歌词
     */
    public void reset() {
        resetInternal();

        invalidate();
    }

    private void resetInternal() {
        lrcData = null;
        mCurrentLine = 0;
        mNewLine = true;
        mCurrentTime = 0;
        mOffset = 0;
        targetIndex = 0;
        mTotalDuration = null;
    }

    /**
     * 二分法查找当前时间应该显示的行数（最后一个 <= time 的行数）
     */
    private int findShowLine(long time) {
        int left = 0;
        int right = lrcData.lines.size();
        while (left <= right) {
            int middle = (left + right) / 2;
            long middleTime = lrcData.lines.get(middle).getStartTime();

            if (time < middleTime) {
                right = middle - 1;
            } else {
                if (middle + 1 >= lrcData.lines.size() || time < lrcData.lines.get(middle + 1).getStartTime()) {
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
