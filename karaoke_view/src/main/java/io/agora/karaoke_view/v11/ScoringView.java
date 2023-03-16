package io.agora.karaoke_view.v11;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
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
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;

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

    private float mPivotHorizontalBias = 0.4f;

    private Handler mHandler;

    private float mMovingPixelsPerMs; // 1ms 对应像素 px

    private float mPitchStickHeight; // 每一项高度 px

    // 音调指示器的半径
    private float mLocalPitchIndicatorRadius;

    private final Paint mLocalPitchLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mLocalPitchPivotColor;

    private final Paint mLocalPitchPivotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint mLinearGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient mOverpastLinearGradient;

    private final Paint mPitchStickLinearGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mDefaultRefPitchStickColor;
    private final Paint mHighlightPitchStickLinearGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mHighlightPitchStickColor;

    private float dotPointX = 0f; // 亮点坐标

    protected ParticleSystem mParticleSystem;
    protected int mParticlesPerSecond = 16;

    private float mInitialScore;

    private float mThresholdOfHitScore;

    private boolean mEnableParticleEffect;

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

    public void setRefPitchStickHeight(float height) {
        this.mPitchStickHeight = height;
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

        mPitchStickHeight = ta.getDimension(R.styleable.ScoringView_pitchStickHeight, getResources().getDimension(R.dimen.pitch_stick_height));

        mEnableParticleEffect = ta.getBoolean(R.styleable.ScoringView_enableParticleEffect, true);

        mThresholdOfHitScore = ta.getFloat(R.styleable.ScoringView_hitScoreThreshold, 0.7f);
        if (mThresholdOfHitScore <= 0 || mThresholdOfHitScore > 1.0f) {
            throw new IllegalArgumentException("Invalid value for hitScoreThreshold, must > 0 and <= 1, current is " + mThresholdOfHitScore);
        }

        mMovingPixelsPerMs = ta.getFloat(R.styleable.ScoringView_movingPixelsPerMs, 0.4F);
        if (mMovingPixelsPerMs <= 0 || mMovingPixelsPerMs > 20f) {
            throw new IllegalArgumentException("Invalid value for mMovingPixelsPerMs, must > 0 and <= 20, current is " + mThresholdOfHitScore);
        }

        mPivotHorizontalBias = ta.getFloat(R.styleable.ScoringView_pivotHorizontalBias, 0.4f);
        if (mPivotHorizontalBias <= 0 || mPivotHorizontalBias > 1f) {
            throw new IllegalArgumentException("Invalid value for mPivotStartWeight, must > 0 and <= 1, current is " + mPivotHorizontalBias);
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
            dotPointX = w * mPivotHorizontalBias;

            int startColor = getResources().getColor(R.color.pitch_start);
            int endColor = getResources().getColor(R.color.pitch_end);
            mOverpastLinearGradient = new LinearGradient(dotPointX, 0, 0, 0, startColor, endColor, Shader.TileMode.CLAMP);

            tryInvalidate();

            tryEnableParticleEffect();
        }
    }

    private boolean isCanvasNotReady() {
        return dotPointX <= 0;
    }

    private Object mDelayedTaskToken;

    private void tryEnableParticleEffect() {
        if (mEnableParticleEffect) {
            if (mDelayedTaskToken != null) {
                mHandler.removeCallbacksAndMessages(mDelayedTaskToken);
                mDelayedTaskToken = null;
            }

            mDelayedTaskToken = new Object();
            boolean result = mHandler.postAtTime(() -> {
                // Create a particle system and start emiting
                ParticleSystem system = mParticleSystem;
                if (mParticleSystem == null) {
                    setParticles(null);
                }
            }, mDelayedTaskToken, SystemClock.uptimeMillis() + 1000);
        }
    }

    private void tryDisableParticleEffect() {
        if (!mEnableParticleEffect) {
            if (mDelayedTaskToken != null) {
                mHandler.removeCallbacksAndMessages(mDelayedTaskToken);
                mDelayedTaskToken = null;
            }

            mDelayedTaskToken = new Object();
            boolean result = mHandler.postAtTime(() -> {
                if (mParticleSystem != null) {
                    mParticleSystem.cancel();
                }
            }, mDelayedTaskToken, SystemClock.uptimeMillis() + 30);
        }
    }

    /**
     * Do not call this regularly, this is very heavy
     *
     * @param particles
     */
    public void setParticles(Drawable[] particles) {
        if (!mEnableParticleEffect) {
            return;
        }

        if (mParticleSystem != null) {
            mParticleSystem.cancel();
        }

        initParticleSystem(particles);

        mEmittingInitialized = false;
    }

    protected final Drawable[] buildDefaultParticleList() {
        Drawable[] particles = new Drawable[8];
        particles[0] = getResources().getDrawable(R.drawable.star1);
        particles[1] = getResources().getDrawable(R.drawable.star2);
        particles[2] = getResources().getDrawable(R.drawable.star3);
        particles[3] = getResources().getDrawable(R.drawable.star4);
        particles[4] = getResources().getDrawable(R.drawable.star5);
        particles[5] = getResources().getDrawable(R.drawable.star6);
        particles[6] = getResources().getDrawable(R.drawable.star7);
        particles[7] = getResources().getDrawable(R.drawable.star8);
        return particles;
    }

    protected void initParticleSystem(Drawable[] particles) {
        mParticlesPerSecond = 12;

        if (particles == null) {
            particles = buildDefaultParticleList();
        }

        mParticleSystem = new ParticleSystem((ViewGroup) this.getParent(), particles.length * 3, particles, 900);
        mParticleSystem.setRotationSpeedRange(90, 180).setScaleRange(0.7f, 1.6f)
                .setSpeedModuleAndAngleRange(0.10f, 0.20f, 130, 230)
                .setFadeOut(300, new AccelerateInterpolator());
    }

    /**
     * enable particle effect or not
     * <p>
     * Do not call this regularly, this is very heavy
     *
     * @param enable
     */
    public void enableParticleEffect(boolean enable) {
        this.mEnableParticleEffect = enable;

        if (enable) {
            tryEnableParticleEffect();
        } else {
            tryDisableParticleEffect();
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

        if (isCanvasNotReady()) { // Fail fast
            return;
        }

        drawStartLine(canvas);
        drawPitchSticks(canvas);
        drawLocalPitchPivot(canvas);
    }
    //</editor-fold>

    //<editor-fold desc="Draw Related">

    private Bitmap mCustomizedLocalPitchPivot;

    public void setLocalPitchPivot(Bitmap bitmap) {
        if (bitmap == null) {
            if (mCustomizedLocalPitchPivot != null) {
                mCustomizedLocalPitchPivot.recycle();
            }
            this.mCustomizedLocalPitchPivot = null;
        } else {
            this.mCustomizedLocalPitchPivot = bitmap;
        }
    }

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

            if (mCustomizedLocalPitchPivot != null) {
                int height = mCustomizedLocalPitchPivot.getHeight() / 2;
                int width = mCustomizedLocalPitchPivot.getWidth() / 2;
                canvas.drawBitmap(mCustomizedLocalPitchPivot, dotPointX - width, value - height, mLocalPitchPivotPaint);
            } else {
                Path path = new Path();
                path.moveTo(dotPointX, value);
                path.lineTo(dotPointX - mLocalPitchIndicatorRadius, value - mLocalPitchIndicatorRadius);
                path.lineTo(dotPointX - mLocalPitchIndicatorRadius, value + mLocalPitchIndicatorRadius);
                path.close();
                canvas.drawPath(path, mLocalPitchPivotPaint);
            }
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
        float mItemHeightPerPitchLevel = (getHeight() - mPitchStickHeight /** make pitch stick always above bottom line **/) / (realPitchMax - realPitchMin);

        long endTimeOfPreviousLine = 0; // Not used so far

        for (int i = 0; i < lines.size(); i++) {
            LyricsLineModel line = mLyricsModel.lines.get(i);
            List<LyricsLineModel.Tone> tones = line.tones;
            if (tones == null || tones.isEmpty()) {
                return;
            }

            long startTime = line.getStartTime();
            long durationOfCurrentEntry = line.getEndTime() - startTime;

            if (this.mScoringMachine.getCurrentProgress() - startTime <= -(2 * durationOfCurrentEntry)) { // If still to early for current entry, we do not draw the sticks
                // If we show the sticks too late, they will appear suddenly in the central of screen, not start from the right side
                break;
            }

            if (i + 1 < lines.size() && line.getStartTime() < this.mScoringMachine.getCurrentProgress()) { // Has next entry
                // Get next entry
                // If start for next is far away than 2 seconds
                // stop the current animation now
                long nextEntryStartTime = mLyricsModel.lines.get(i + 1).getStartTime();
                if ((nextEntryStartTime - line.getEndTime() >= 2 * 1000) && this.mScoringMachine.getCurrentProgress() > line.getEndTime() && this.mScoringMachine.getCurrentProgress() < nextEntryStartTime) { // Two seconds after this entry stop
                    assureAnimationForPitchPivot(0); // Force stop the animation when there is a too long stop between two entrys
                    if (mTimestampForLastAnimationDecrease < 0 || this.mScoringMachine.getCurrentProgress() - mTimestampForLastAnimationDecrease > 4 * 1000) {
                        ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(600).start(); // Decrease the local pitch pivot
                        mTimestampForLastAnimationDecrease = this.mScoringMachine.getCurrentProgress();
                    }
                }
            }

            float pixelsAwayFromPilot = (startTime - this.mScoringMachine.getCurrentProgress()) * mMovingPixelsPerMs; // For every time, we need to locate the new coordinate
            float x = dotPointX + pixelsAwayFromPilot;

            if (endTimeOfPreviousLine != 0) { // If has empty divider before
                // Not used so far
                int emptyDividerWidth = (int) (mMovingPixelsPerMs * (startTime - endTimeOfPreviousLine));
                x = x + emptyDividerWidth;
            }

            endTimeOfPreviousLine = line.getEndTime();

            if (x + 2 * durationOfCurrentEntry * mMovingPixelsPerMs < 0) { // Already past for long time enough
                continue;
            }

            for (int toneIndex = 0; toneIndex < tones.size(); toneIndex++) {
                LyricsLineModel.Tone tone = tones.get(toneIndex);

                pixelsAwayFromPilot = (tone.begin - this.mScoringMachine.getCurrentProgress()) * mMovingPixelsPerMs; // For every time, we need to locate the new coordinate
                x = dotPointX + pixelsAwayFromPilot;
                widthOfPitchStick = mMovingPixelsPerMs * tone.getDuration();
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
                    if (isJustHighlightTriggered && Math.abs(x - dotPointX) <= 400 * mMovingPixelsPerMs) {
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
                        RectF rNormal = buildRectF(x, y, endX, y + mPitchStickHeight);
                        canvas.drawRoundRect(rNormal, 8, 8, mPitchStickLinearGradientPaint);
                    } else if (x < dotPointX && endX > dotPointX) {
                        RectF rNormalRightHalf = buildRectF(dotPointX, y, endX, y + mPitchStickHeight);
                        canvas.drawRoundRect(rNormalRightHalf, 8, 8, mPitchStickLinearGradientPaint);

                        RectF rNormalLeftHalf = buildRectF(x, y, dotPointX, y + mPitchStickHeight);
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

                            RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + mPitchStickHeight);
                            if (tone.highlightOffset <= 0 || highlightEndX == dotPointX) {
                                canvas.drawRoundRect(rHighlight, 8, 8, mHighlightPitchStickLinearGradientPaint);
                            } else {
                                // Should be drawRect
                                canvas.drawRoundRect(rHighlight, 8, 8, mHighlightPitchStickLinearGradientPaint);
                            }
                        }
                    } else if (endX <= dotPointX) {
                        RectF rNormal = buildRectF(x, y, endX, y + mPitchStickHeight);
                        canvas.drawRoundRect(rNormal, 8, 8, mPitchStickLinearGradientPaint);

                        if (tone.highlightOffset >= 0) { // Partially draw
                            float highlightStartX = x + tone.highlightOffset;
                            float highlightEndX = endX;
                            if (tone.highlightWidth >= 0) {
                                highlightEndX = x + tone.highlightOffset + tone.highlightWidth;
                            }

                            RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + mPitchStickHeight);
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
                    RectF rNormal = buildRectF(x, y, endX, y + mPitchStickHeight);
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

    private volatile boolean mEmittingInitialized = false;

    private void fineTuneTheHighlightAnimation(float endX) {
        if (!mEnableParticleEffect) {
            return;
        }
        if (endX > dotPointX) { // Animation Enhancement, If still to far from end, just keep the animation ongoing and update the coordinate
            float value = getYForPitchPivot();
            // It works with an emision range
            int[] location = new int[2];
            this.getLocationInWindow(location);
            if (mEmittingInitialized && mParticleSystem != null) {
                mParticleSystem.updateEmitPoint((int) (location[0] + dotPointX), location[1] + (int) (value));
            }
        }
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

    public void updatePitchAndScore(final float pitch, final double scoreAfterNormalization, final boolean betweenCurrentPitch) {
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
        if (!mEnableParticleEffect) {
            return;
        }

        // Animation for particle
        if (scoreAfterNormalization >= mThresholdOfHitScore) {
            if (mParticleSystem != null) {
                float value = getYForPitchPivot();
                // It works with an emision range
                int[] location = new int[2];
                this.getLocationInWindow(location);
                if (!mEmittingInitialized) {
                    mEmittingInitialized = true;
                    mParticleSystem.emit((int) (location[0] + dotPointX), location[1] + (int) (value), mParticlesPerSecond);
                } else {
                    mParticleSystem.updateEmitPoint((int) (location[0] + dotPointX), location[1] + (int) (value));
                }
                mParticleSystem.resumeEmitting();
            }
            mInHighlightStatus = true;
        } else {
            if (mParticleSystem != null) {
                mParticleSystem.stopEmitting();
            }
            mInHighlightStatus = false;
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
                    ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", 0.0f).setDuration(10).start(); // Decrease the local pitch pivot
                }
            });
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                assureAnimationForPitchPivot(0); // Force stop the animation when line just finished
            }
        });
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
