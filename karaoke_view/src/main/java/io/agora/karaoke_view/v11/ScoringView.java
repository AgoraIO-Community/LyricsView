package io.agora.karaoke_view.v11;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.plattysoft.leonids.ParticleSystem;

import java.util.List;

import io.agora.karaoke_view.R;
import io.agora.karaoke_view.v11.internal.ScoringMachine;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;

/**
 * 激励，互动视图
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/08/04
 */
public class ScoringView extends View {

    private static final boolean DEBUG = false;

    private static final float START_PERCENT = 0.4F;

    private Handler mHandler;

    private float movedPixelsPerMs = 0.4F; // 1ms 对应像素 px

    private float pitchStickHeight; // 每一项高度 px
    private int pitchStickSpace = 4; // 间距 px

    // 音调指示器的半径
    private float mLocalPitchIndicatorRadius;

    // 分数阈值 大于此值计分 小于不计分
    // TODO(Hai_Guo) rename minimumScorePerTone
    public float minimumScorePerTone;

    private final Paint mLocalPitchLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mLocalPitchPivotColor;

    private final Paint mLocalPitchPivotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint mLinearGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient mOverpastLinearGradient;

    private final Paint mPitchStickLinearGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mDefaultRefPitchStickColor;
    private final Paint mHighlightPitchStickLinearGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mHighlightPitchStickColor;

    private float dotPointX = 0F; // 亮点坐标

    private ParticleSystem mParticleSystem;

    private float mInitialScore;

    private float mThresholdOfHitScore;

    private long mThresholdOfOffPitchTime;

    //<editor-fold desc="Init Related">
    public ScoringView(Context context) {
        this(context, null);
    }

    public ScoringView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScoringView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ScoringView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    public void setDefaultRefPitchStickColor(int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.default_popular_color);
        }
        this.mDefaultRefPitchStickColor = color;

        tryInvalidate();
    }

    public void setHighlightRefPitchStickColor(int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.pitch_stick_highlight_color);
        }
        this.mHighlightPitchStickColor = color;

        tryInvalidate();
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        this.mHandler = new Handler(Looper.myLooper());
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.ScoringView);
        mLocalPitchIndicatorRadius = ta.getDimension(R.styleable.ScoringView_pitchIndicatorRadius, getResources().getDimension(R.dimen.local_pitch_indicator_radius));
        mLocalPitchPivotColor = ta.getColor(R.styleable.ScoringView_pitchIndicatorColor, getResources().getColor(R.color.local_pitch_indicator_color));
        mInitialScore = ta.getFloat(R.styleable.ScoringView_pitchInitialScore, 0f);

        if (mInitialScore < 0) {
            throw new IllegalArgumentException("Invalid value for pitchInitialScore, must >= 0, current is " + mInitialScore);
        }

        mDefaultRefPitchStickColor = getResources().getColor(R.color.default_popular_color);
        mHighlightPitchStickColor = ta.getColor(R.styleable.ScoringView_pitchStickHighlightColor, getResources().getColor(R.color.pitch_stick_highlight_color));

        pitchStickHeight = ta.getDimension(R.styleable.ScoringView_pitchStickHeight, getResources().getDimension(R.dimen.pitch_stick_height));

        minimumScorePerTone = ta.getFloat(R.styleable.ScoringView_minimumScore, 40f) / 100;

        if (minimumScorePerTone < 0 || minimumScorePerTone > 1.0f) {
            throw new IllegalArgumentException("Invalid value for minimumScore, must between 0 and 100, current is " + minimumScorePerTone);
        }

        mThresholdOfHitScore = ta.getFloat(R.styleable.ScoringView_hitScoreThreshold, 70f) / 100;

        if (mThresholdOfHitScore <= 0 || mThresholdOfHitScore > 1.0f) {
            throw new IllegalArgumentException("Invalid value for hitScoreThreshold, must > 0 and <= 100, current is " + mThresholdOfHitScore);
        }

        mThresholdOfOffPitchTime = ta.getInt(R.styleable.ScoringView_offPitchTimeThreshold, 1000);

        if (mThresholdOfOffPitchTime <= 0 || mThresholdOfOffPitchTime > 5000f) {
            throw new IllegalArgumentException("Invalid value for offPitchTimeThreshold(time of off pitch), must > 0 and <= 5000, current is " + mThresholdOfOffPitchTime);
        }

        ta.recycle();

        int startColor = getResources().getColor(R.color.pitch_start);
        int endColor = getResources().getColor(R.color.pitch_end);
        mOverpastLinearGradient = new LinearGradient(dotPointX, 0, 0, 0, startColor, endColor, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            int w = right - left;
            int h = bottom - top;
            dotPointX = w * START_PERCENT;

            int startColor = getResources().getColor(R.color.pitch_start);
            int endColor = getResources().getColor(R.color.pitch_end);
            mOverpastLinearGradient = new LinearGradient(dotPointX, 0, 0, 0, startColor, endColor, Shader.TileMode.CLAMP);

            tryInvalidate();

            mHandler.postDelayed(() -> {
                // Create a particle system and start emiting
                if (mParticleSystem == null) {
                    Drawable[] drawables = new Drawable[4];
                    drawables[0] = getResources().getDrawable(R.drawable.star1);
                    drawables[1] = getResources().getDrawable(R.drawable.star2);
                    drawables[2] = getResources().getDrawable(R.drawable.star3);
                    drawables[3] = getResources().getDrawable(R.drawable.star4);
                    mParticleSystem = new ParticleSystem((ViewGroup) this.getParent(), 4, drawables, 200);
                }

                // It works with an emision range
                int[] location = new int[2];
                this.getLocationInWindow(location);
                mParticleSystem.setRotationSpeedRange(90, 180).setScaleRange(0.7f, 1.3f)
                        .setSpeedModuleAndAngleRange(0.03f, 0.12f, 120, 240)
                        .setFadeOut(200, new AccelerateInterpolator());
            }, 1000);
        }
    }

    private final RectF mRectFAvoidingNewObject = new RectF(0, 0, 0, 0);

    private RectF buildRectF(float left, float top, float right, float bottom) {
        mRectFAvoidingNewObject.left = left;
        mRectFAvoidingNewObject.top = top;
        mRectFAvoidingNewObject.right = right;
        mRectFAvoidingNewObject.bottom = bottom;
        return mRectFAvoidingNewObject;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawStartLine(canvas);
        drawPitchSticks(canvas);
        drawLocalPitchPivot(canvas);
    }
    //</editor-fold>

    //<editor-fold desc="Draw Related">
    private void drawLocalPitchPivot(Canvas canvas) {
        mLocalPitchLinePaint.setShader(null);
        mLocalPitchLinePaint.setColor(mLocalPitchPivotColor);
        mLocalPitchLinePaint.setAntiAlias(true);
        canvas.drawLine(dotPointX, 0, dotPointX, getHeight(), mLocalPitchLinePaint);

        float value = getYForPitchPivot();
        if (value >= 0) {
            mLocalPitchPivotPaint.setColor(mLocalPitchPivotColor);
            mLocalPitchPivotPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mLocalPitchPivotPaint.setAntiAlias(true);

            Path path = new Path();
            path.moveTo(dotPointX, value);
            path.lineTo(dotPointX - mLocalPitchIndicatorRadius, value - mLocalPitchIndicatorRadius);
            path.lineTo(dotPointX - mLocalPitchIndicatorRadius, value + mLocalPitchIndicatorRadius);
            path.close();
            canvas.drawPath(path, mLocalPitchPivotPaint);
        }
    }

    private float getYForPitchPivot() {
        float targetY = 0;

        if (ifNotInitialized()) { // Not initialized
            targetY = getHeight() - this.mLocalPitchIndicatorRadius;
        } else if (this.mLocalPitch >= this.mScoringMachine.getMinimumRefPitch() && this.mScoringMachine.getMaximumRefPitch() != 0) { // Has value, not the default case
            float realPitchMax = this.mScoringMachine.getMaximumRefPitch() + 5;
            float realPitchMin = this.mScoringMachine.getMinimumRefPitch() - 5;
            float mItemHeightPerPitchLevel = getHeight() / (realPitchMax - realPitchMin);
            targetY = (realPitchMax - this.mLocalPitch) * mItemHeightPerPitchLevel;
        } else if (this.mLocalPitch < this.mScoringMachine.getMinimumRefPitch()) { // minimal local pitch
            targetY = getHeight();
        }

        if (targetY < this.mLocalPitchIndicatorRadius) { // clamping it under the line
            targetY += this.mLocalPitchIndicatorRadius;
        }
        if (targetY > getHeight() - this.mLocalPitchIndicatorRadius) { // clamping it above the line
            targetY -= this.mLocalPitchIndicatorRadius;
        }
        return targetY;
    }

    private void drawStartLine(Canvas canvas) {
        mLinearGradientPaint.setShader(null);
        mLinearGradientPaint.setShader(mOverpastLinearGradient);
        mLinearGradientPaint.setAntiAlias(true);
        canvas.drawRect(0, 0, dotPointX, getHeight(), mLinearGradientPaint);

        if (DEBUG) {
            canvas.drawText("" + this.mScoringMachine.toString() + ", y: " + (int) (getYForPitchPivot()) + ", pitch: " + (int) (mLocalPitch), 20, getHeight() - 30, mHighlightPitchStickLinearGradientPaint);
        }
    }

    private void drawPitchSticks(Canvas canvas) {
        mPitchStickLinearGradientPaint.setShader(null);
        mPitchStickLinearGradientPaint.setColor(mDefaultRefPitchStickColor);
        mPitchStickLinearGradientPaint.setAntiAlias(true);

        mHighlightPitchStickLinearGradientPaint.setShader(null);
        mHighlightPitchStickLinearGradientPaint.setColor(mHighlightPitchStickColor);
        mHighlightPitchStickLinearGradientPaint.setAntiAlias(true);

        if (ifNotInitialized()) {
            return;
        }

        float realPitchMax = this.mScoringMachine.getMaximumRefPitch() + 5;
        float realPitchMin = this.mScoringMachine.getMinimumRefPitch() - 5;

        List<LyricsLineModel> lines = mLyricsModel.lines;

        float y;
        float widthOfPitchStick;
        float mItemHeightPerPitchLevel = (getHeight() - pitchStickHeight /** make pitch stick always above bottom line **/) / (realPitchMax - realPitchMin);

        long endTimeOfPreviousLine = 0; // Not used so far

        for (int i = 0; i < lines.size(); i++) {
            LyricsLineModel line = mLyricsModel.lines.get(i);
            List<LyricsLineModel.Tone> tones = line.tones;
            if (tones == null || tones.isEmpty()) {
                return;
            }

            long startTime = line.getStartTime();
            long durationOfCurrentEntry = line.getEndTime() - startTime;

            if (this.mScoringMachine.getCurrentTimestamp() - startTime <= -(2 * durationOfCurrentEntry)) { // If still to early for current entry, we do not draw the sticks
                // If we show the sticks too late, they will appear suddenly in the central of screen, not start from the right side
                break;
            }

            if (i + 1 < lines.size() && line.getStartTime() < this.mScoringMachine.getCurrentTimestamp()) { // Has next entry
                // Get next entry
                // If start for next is far away than 2 seconds
                // stop the current animation now
                long nextEntryStartTime = mLyricsModel.lines.get(i + 1).getStartTime();
                if ((nextEntryStartTime - line.getEndTime() >= 2 * 1000) && this.mScoringMachine.getCurrentTimestamp() > line.getEndTime() && this.mScoringMachine.getCurrentTimestamp() < nextEntryStartTime) { // Two seconds after this entry stop
                    assureAnimationForPitchPivot(0); // Force stop the animation when there is a too long stop between two entrys
                    if (mTimestampForLastAnimationDecrease < 0 || this.mScoringMachine.getCurrentTimestamp() - mTimestampForLastAnimationDecrease > 4 * 1000) {
                        ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(600).start(); // Decrease the local pitch pivot
                        mTimestampForLastAnimationDecrease = this.mScoringMachine.getCurrentTimestamp();
                    }
                }
            }

            float pixelsAwayFromPilot = (startTime - this.mScoringMachine.getCurrentTimestamp()) * movedPixelsPerMs; // For every time, we need to locate the new coordinate
            float x = dotPointX + pixelsAwayFromPilot;

            if (endTimeOfPreviousLine != 0) { // If has empty divider before
                // Not used so far
                int emptyDividerWidth = (int) (movedPixelsPerMs * (startTime - endTimeOfPreviousLine));
                x = x + emptyDividerWidth;
            }

            endTimeOfPreviousLine = line.getEndTime();

            if (x + 2 * durationOfCurrentEntry * movedPixelsPerMs < 0) { // Already past for long time enough
                continue;
            }

            for (int toneIndex = 0; toneIndex < tones.size(); toneIndex++) {
                LyricsLineModel.Tone tone = tones.get(toneIndex);

                pixelsAwayFromPilot = (tone.begin - this.mScoringMachine.getCurrentTimestamp()) * movedPixelsPerMs; // For every time, we need to locate the new coordinate
                x = dotPointX + pixelsAwayFromPilot;
                widthOfPitchStick = movedPixelsPerMs * tone.getDuration();
                float endX = x + widthOfPitchStick;

                if (endX <= 0) {
                    tone.resetHighlight();
                    continue;
                }

                if (x >= getWidth()) {
                    break;
                }

                y = (realPitchMax - tone.pitch) * mItemHeightPerPitchLevel;

                if (Math.abs(x - dotPointX) <= 2 * mLocalPitchIndicatorRadius || Math.abs(endX - dotPointX) <= 2 * mLocalPitchIndicatorRadius) { // Only mark item around local pitch pivot
                    boolean isJustHighlightTriggered = (!tone.highlight) && mInHighlightStatus;
                    if (isJustHighlightTriggered && Math.abs(x - dotPointX) <= 400 * movedPixelsPerMs) {
                        tone.highlightOffset = Math.abs(dotPointX - x);
                        if (tone.highlightOffset >= widthOfPitchStick) {
                            tone.highlightOffset = 0.5f * widthOfPitchStick;
                        }
                    }
                    boolean isJustDeHighlightTriggered = (tone.highlight) && !mInHighlightStatus;
                    if (isJustDeHighlightTriggered && tone.highlightWidth < 0) {
                        tone.highlightWidth = Math.abs(dotPointX - x - tone.highlightOffset);
                        if (tone.highlightWidth >= widthOfPitchStick) {
                            tone.highlightWidth = 0.5f * widthOfPitchStick;
                        }
                    }

                    tone.highlight = tone.highlight || mInHighlightStatus; // Mark this as highlight forever
                }

                if (tone.highlight) {
                    if (toneIndex == 1 && !tones.get(0).highlight) { // Workaround, always mark first tone highlighted if the second is highlighted
                        tones.get(0).highlight = true;
                    }

                    if (x >= dotPointX) {
                        RectF rNormal = buildRectF(x, y, endX, y + pitchStickHeight);
                        canvas.drawRoundRect(rNormal, 8, 8, mPitchStickLinearGradientPaint);
                    } else if (x < dotPointX && endX > dotPointX) {
                        RectF rNormalRightHalf = buildRectF(dotPointX, y, endX, y + pitchStickHeight);
                        canvas.drawRoundRect(rNormalRightHalf, 8, 8, mPitchStickLinearGradientPaint);

                        RectF rNormalLeftHalf = buildRectF(x, y, dotPointX, y + pitchStickHeight);
                        canvas.drawRoundRect(rNormalLeftHalf, 8, 8, mPitchStickLinearGradientPaint);

                        if (tone.highlightOffset >= 0) { // Partially draw
                            float highlightStartX = x + tone.highlightOffset;
                            float highlightEndX = dotPointX;
                            if (tone.highlightWidth >= 0) {
                                highlightEndX = x + tone.highlightOffset + tone.highlightWidth;
                                if (highlightEndX > dotPointX) {
                                    highlightEndX = dotPointX;
                                }
                            }

                            RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + pitchStickHeight);
                            if (tone.highlightOffset <= 0 || highlightEndX == dotPointX) {
                                canvas.drawRoundRect(rHighlight, 8, 8, mHighlightPitchStickLinearGradientPaint);
                            } else {
                                // Should be drawRect
                                canvas.drawRoundRect(rHighlight, 8, 8, mHighlightPitchStickLinearGradientPaint);
                            }
                        }
                    } else if (endX <= dotPointX) {
                        RectF rNormal = buildRectF(x, y, endX, y + pitchStickHeight);
                        canvas.drawRoundRect(rNormal, 8, 8, mPitchStickLinearGradientPaint);

                        if (tone.highlightOffset >= 0) { // Partially draw
                            float highlightStartX = x + tone.highlightOffset;
                            float highlightEndX = endX;
                            if (tone.highlightWidth >= 0) {
                                highlightEndX = x + tone.highlightOffset + tone.highlightWidth;
                            }

                            RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + pitchStickHeight);
                            if (tone.highlightOffset <= 0 && tone.highlightWidth <= 0 || highlightEndX == endX) {
                                canvas.drawRoundRect(rHighlight, 8, 8, mHighlightPitchStickLinearGradientPaint);
                            } else {
                                // Should be drawRect
                                canvas.drawRoundRect(rHighlight, 8, 8, mHighlightPitchStickLinearGradientPaint);
                            }
                        }
                    }
                    fineTuneTheHighlightAnimation(endX);
                } else {
                    RectF rNormal = buildRectF(x, y, endX, y + pitchStickHeight);
                    canvas.drawRoundRect(rNormal, 8, 8, mPitchStickLinearGradientPaint);
                    if (DEBUG) {
                        mHighlightPitchStickLinearGradientPaint.setTextSize(28);
                        canvas.drawText(tone.word, x, 30, mHighlightPitchStickLinearGradientPaint);
                        canvas.drawText((int) (x) + "", x, 60, mHighlightPitchStickLinearGradientPaint);
                        canvas.drawText((int) (endX) + "", x, 90, mHighlightPitchStickLinearGradientPaint);
                    }
                }


            }
        }
    }
    //</editor-fold>

    private volatile boolean emittingEnabled = false;

    private void fineTuneTheHighlightAnimation(float endX) {
        if (endX > dotPointX) { // Animation Enhancement, If still to far from end, just keep the animation ongoing and update the coordinate
            float value = getYForPitchPivot();
            // It works with an emision range
            int[] location = new int[2];
            this.getLocationInWindow(location);
            if (emittingEnabled && mParticleSystem != null) {
                mParticleSystem.updateEmitPoint((int) (location[0] + dotPointX), location[1] + (int) (value));
            }
        }
    }

    private static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    private volatile float mLocalPitch = 0.0F;

    private long mTimestampForLastAnimationDecrease = -1;

    private void setMLocalPitch(float localPitch) {
        this.mLocalPitch = localPitch;
    }

    private long mLastViewInvalidateTs;

    private ScoringMachine mScoringMachine;

    private LyricsModel mLyricsModel;

    public void attachToScoringMachine(ScoringMachine machine) {
        if (!machine.isReady()) {
            throw new IllegalStateException("Must call ScoringMachine.prepare before attaching");
        }
        this.mScoringMachine = machine;
        this.mLyricsModel = machine.getLyricsModel();

        // Update values from UI view
        this.mScoringMachine.setInitialScore(mInitialScore);
        this.mScoringMachine.setMinimumScorePerTone(minimumScorePerTone);
    }

    public void requestRefreshUi() {
        tryInvalidate();
    }

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

    private final Runnable mRemoveAnimationCallback = new Runnable() {
        @Override
        public void run() {
            assureAnimationForPitchPivot(0); // Force stop the animation when there is a too long stop between two entrys
            ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(600).start(); // Decrease the local pitch pivot
        }
    };

    private boolean ifNotInitialized() {
        return mLyricsModel == null || mLyricsModel.lines == null || mLyricsModel.lines.isEmpty();
    }

    public void updatePitchAndScore(final float pitch, final double scoreAfterNormalization, final boolean hit) {
        if (ifNotInitialized()) {
            return;
        }

        if (pitch == 0 || pitch < this.mScoringMachine.getMinimumRefPitch() || pitch > this.mScoringMachine.getMaximumRefPitch()) {
            assureAnimationForPitchPivot(0);
            mHandler.postDelayed(mRemoveAnimationCallback, mThresholdOfOffPitchTime);
            return;
        }

        mHandler.removeCallbacks(mRemoveAnimationCallback);

        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    performPivotAnimationIfNecessary(pitch, scoreAfterNormalization);
                }
            });
        } else {
            performPivotAnimationIfNecessary(pitch, scoreAfterNormalization);
        }

        mInHighlightStatus = hit;
    }

    private long lastCurrentTs = 0;

    private void performPivotAnimationIfNecessary(float pitch, double scoreAfterNormalization) {
        if (System.currentTimeMillis() - lastCurrentTs > 200) {
            int duration = (ScoringView.this.mLocalPitch == 0 && pitch > 0) ? 20 : 80;
            ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, pitch).setDuration(duration).start();
            lastCurrentTs = System.currentTimeMillis();
            assureAnimationForPitchPivot(scoreAfterNormalization);
        }
    }

    private void assureAnimationForPitchPivot(double scoreAfterNormalization) {
        // Animation for particle
        if (scoreAfterNormalization >= mThresholdOfHitScore) {
            if (mParticleSystem != null) {
                float value = getYForPitchPivot();
                // It works with an emision range
                int[] location = new int[2];
                this.getLocationInWindow(location);
                if (!emittingEnabled) {
                    emittingEnabled = true;
                    mParticleSystem.emit((int) (location[0] + dotPointX), location[1] + (int) (value), 6);
                } else {
                    mParticleSystem.updateEmitPoint((int) (location[0] + dotPointX), location[1] + (int) (value));
                }
                mParticleSystem.resumeEmitting();
            }
        } else {
            if (mParticleSystem != null) {
                mParticleSystem.stopEmitting();
            }
        }
    }

    public void forceStopPivotAnimationWhenReachingContinuousZeros() {
        mInHighlightStatus = false;
        if (this.mLocalPitch != 0) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    assureAnimationForPitchPivot(0); // Force stop the animation when reach 8 continuous zeros
                    ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(200).start(); // Decrease the local pitch pivot
                }
            });
        }
    }

    public void forceStopPivotAnimationWhenFullLineFinished(double score) {
        if (score == 0 && this.mLocalPitch != 0) {
            mInHighlightStatus = false;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    assureAnimationForPitchPivot(0); // Force stop the animation when there is no new score for a long time(a full sentence)
                    ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", 0.0f).setDuration(10).start(); // Decrease the local pitch pivot
                }
            });
        }
    }

    private boolean mInHighlightStatus;

    public void reset() {
        mLyricsModel = null;
        mInHighlightStatus = false;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                assureAnimationForPitchPivot(0); // Force stop the animation when reach 8 continuous zeros
                ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(200).start(); // Decrease the local pitch pivot
                invalidateForSureAndMarkTheTimestamp();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
}
