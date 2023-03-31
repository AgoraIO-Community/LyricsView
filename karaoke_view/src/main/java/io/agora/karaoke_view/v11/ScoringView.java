package io.agora.karaoke_view.v11;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
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

    private float mStartPointHorizontalBias = 0.4f;

    private Handler mHandler;

    private float mMovingPixelsPerMs;

    protected float mPitchStickHeight;

    protected float mLocalPitchIndicatorRadius;
    protected int mLocalPitchIndicatorColor;

    protected final Paint mLocalPitchIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final CornerPathEffect mLocalPitchEffect = new CornerPathEffect(8);

    protected final Paint mStartLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected final Paint mOverpastLinearGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected LinearGradient mOverpastLinearGradient;

    private final Paint mPitchStickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected int mRefPitchStickDefaultColor;
    private final Paint mPitchStickHighlightedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mPitchStickHighlightedColor;

    protected float mCenterXOfStartPoint = 0f; // centerX of start point(portrait divider), same as local pitch indicator

    protected final Paint mLeadingLinesPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected ParticleSystem mParticleSystem;
    protected int mParticlesPerSecond = 16;

    private float mInitialScore;

    private float mThresholdOfHitScore;

    private boolean mEnableParticleEffect;

    private long mThresholdOfOffProgressTime;

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

    public void setRefPitchStickDefaultColor(int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.default_popular_color);
        }
        this.mRefPitchStickDefaultColor = color;

        performInvalidateIfNecessary();
    }

    public void setRefPitchStickHighlightedColor(int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.pitch_stick_highlighted_color);
        }
        this.mPitchStickHighlightedColor = color;

        performInvalidateIfNecessary();
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
        mLocalPitchIndicatorColor = ta.getColor(R.styleable.ScoringView_pitchIndicatorColor, getResources().getColor(R.color.local_pitch_indicator_color));
        mInitialScore = ta.getFloat(R.styleable.ScoringView_initialScore, 0f);

        if (mInitialScore < 0) {
            throw new IllegalArgumentException("Invalid value for initialScore, must >= 0, current is " + mInitialScore);
        }

        mRefPitchStickDefaultColor = getResources().getColor(R.color.default_popular_color);
        mPitchStickHighlightedColor = ta.getColor(R.styleable.ScoringView_pitchStickHighlightedColor, getResources().getColor(R.color.pitch_stick_highlighted_color));

        mPitchStickHeight = ta.getDimension(R.styleable.ScoringView_pitchStickHeight, getResources().getDimension(R.dimen.pitch_stick_height));

        mEnableParticleEffect = ta.getBoolean(R.styleable.ScoringView_enableParticleEffect, true);

        mThresholdOfHitScore = ta.getFloat(R.styleable.ScoringView_hitScoreThreshold, 0.7f);
        if (mThresholdOfHitScore <= 0 || mThresholdOfHitScore > 1.0f) {
            throw new IllegalArgumentException("Invalid value for hitScoreThreshold, must > 0 and <= 1, current is " + mThresholdOfHitScore);
        }

        mMovingPixelsPerMs = ta.getFloat(R.styleable.ScoringView_movingPixelsPerMs, 0.4F);
        if (mMovingPixelsPerMs <= 0 || mMovingPixelsPerMs > 20f) {
            throw new IllegalArgumentException("Invalid value for mMovingPixelsPerMs, must > 0 and <= 20, current is " + mMovingPixelsPerMs);
        }

        mStartPointHorizontalBias = ta.getFloat(R.styleable.ScoringView_startPointHorizontalBias, 0.4f);
        if (mStartPointHorizontalBias <= 0 || mStartPointHorizontalBias > 1f) {
            throw new IllegalArgumentException("Invalid value for mStartPointHorizontalBias, must > 0 and <= 1, current is " + mStartPointHorizontalBias);
        }

        mThresholdOfOffProgressTime = ta.getInt(R.styleable.ScoringView_offProgressTimeThreshold, 1000);
        if (mThresholdOfOffProgressTime <= 0 || mThresholdOfOffProgressTime > 5000f) {
            throw new IllegalArgumentException("Invalid value for offProgressTimeThreshold(time of off/no progress), must > 0 and <= 5000, current is " + mThresholdOfOffProgressTime);
        }

        ta.recycle();

        mOverpastLinearGradient = new LinearGradient(mCenterXOfStartPoint, 0, 0, 0,
                getResources().getColor(R.color.overpast_wall_start_color),
                getResources().getColor(R.color.overpast_wall_end_color), Shader.TileMode.CLAMP);

        mLeadingLinesPaint.setColor(getResources().getColor(R.color.leading_lines_color));
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
            mCenterXOfStartPoint = w * mStartPointHorizontalBias;

            mOverpastLinearGradient = new LinearGradient(mCenterXOfStartPoint, 0, 0, 0,
                    getResources().getColor(R.color.overpast_wall_start_color),
                    getResources().getColor(R.color.overpast_wall_end_color), Shader.TileMode.CLAMP);

            performInvalidateIfNecessary();

            tryEnableParticleEffect();
        }
    }

    protected final boolean isCanvasNotReady() {
        return mCenterXOfStartPoint <= 0;
    }

    /**
     * Enable particle effect or not
     * <p>
     * Do not call this regularly, this is expensive
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
     * Set particles if do not use the default ones
     * <p>
     * Do not call this regularly, this is expensive
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

        drawLeadingLines(canvas);
        drawOverpastWallAndStartLine(canvas);
        drawPitchSticks(canvas);
        drawLocalPitchIndicator(canvas);

        if (DEBUG) {
            mPitchStickHighlightedPaint.setTextSize(28);
            canvas.drawText("id:" + System.identityHashCode(this.mScoringMachine) + ", current=" + System.currentTimeMillis()
                            + ", progress: " + (int) (mScoringMachine.getCurrentProgress())
                            + ", y: " + (int) (getYForPitchIndicator()) + ", pitch: " + (int) (mLocalPitch),
                    20, getHeight() - 30, mPitchStickHighlightedPaint);
        }
    }

    protected Bitmap mCustomizedLocalPitchIndicator;

    public void setLocalPitchIndicator(Bitmap bitmap) {
        if (bitmap == null) {
            if (mCustomizedLocalPitchIndicator != null) {
                mCustomizedLocalPitchIndicator.recycle();
            }
            this.mCustomizedLocalPitchIndicator = null;
        } else {
            this.mCustomizedLocalPitchIndicator = bitmap;
        }
    }

    protected void drawLocalPitchIndicator(Canvas canvas) {
        float value = getYForPitchIndicator();
        if (value >= 0) {
            mLocalPitchIndicatorPaint.setColor(mLocalPitchIndicatorColor);
            mLocalPitchIndicatorPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mLocalPitchIndicatorPaint.setAntiAlias(true);

            if (mCustomizedLocalPitchIndicator != null) {
                int height = mCustomizedLocalPitchIndicator.getHeight() / 2;
                int width = mCustomizedLocalPitchIndicator.getWidth() / 2;
                canvas.drawBitmap(mCustomizedLocalPitchIndicator, mCenterXOfStartPoint - width, value - height, mLocalPitchIndicatorPaint);
            } else {
                mLocalPitchIndicatorPaint.setPathEffect(mLocalPitchEffect);
                Path path = new Path();
                path.moveTo(mCenterXOfStartPoint, value);
                path.lineTo(mCenterXOfStartPoint - mLocalPitchIndicatorRadius, value - mLocalPitchIndicatorRadius * 2 / 3);
                path.lineTo(mCenterXOfStartPoint - mLocalPitchIndicatorRadius, value + mLocalPitchIndicatorRadius * 2 / 3);
                path.close();
                canvas.drawPath(path, mLocalPitchIndicatorPaint);
            }
        }
    }

    protected float getYForPitchIndicator() {
        float targetY = 0;

        if (uninitializedOrNoLyrics()) { // Not initialized
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

    protected void drawLeadingLines(Canvas canvas) {
        int height = getHeight();
        int width = getWidth();

        int heightOfLine0 = height * 0 / 5;
        int heightOfLine1 = height * 1 / 5;
        int heightOfLine2 = height * 2 / 5;
        int heightOfLine3 = height * 3 / 5;
        int heightOfLine4 = height * 4 / 5;
        int heightOfLine5 = height * 5 / 5;

        mLeadingLinesPaint.setShader(null);
        mLeadingLinesPaint.setAntiAlias(true);

        canvas.drawLine(0, heightOfLine0, width, heightOfLine0, mLeadingLinesPaint);
        canvas.drawLine(0, heightOfLine1, width, heightOfLine1, mLeadingLinesPaint);
        canvas.drawLine(0, heightOfLine2, width, heightOfLine2, mLeadingLinesPaint);
        canvas.drawLine(0, heightOfLine3, width, heightOfLine3, mLeadingLinesPaint);
        canvas.drawLine(0, heightOfLine4, width, heightOfLine4, mLeadingLinesPaint);
        canvas.drawLine(0, heightOfLine5, width, heightOfLine5, mLeadingLinesPaint);
    }

    protected void drawOverpastWallAndStartLine(Canvas canvas) {
        drawOverpastWall(canvas);

        drawStartLine(canvas);
    }

    protected final void drawOverpastWall(Canvas canvas) {
        mOverpastLinearGradientPaint.setShader(null);
        mOverpastLinearGradientPaint.setShader(mOverpastLinearGradient);
        mOverpastLinearGradientPaint.setAntiAlias(true);
        canvas.drawRect(0, 0, mCenterXOfStartPoint, getHeight(), mOverpastLinearGradientPaint);
    }

    protected final void drawStartLine(Canvas canvas) {
        mStartLinePaint.setShader(null);
        mStartLinePaint.setColor(mLocalPitchIndicatorColor);
        mStartLinePaint.setAntiAlias(true);
        mStartLinePaint.setStrokeWidth(3);
        canvas.drawLine(mCenterXOfStartPoint, 0, mCenterXOfStartPoint, getHeight(), mStartLinePaint);
    }

    private void drawPitchSticks(Canvas canvas) {
        mPitchStickPaint.setShader(null);
        mPitchStickPaint.setColor(mRefPitchStickDefaultColor);
        mPitchStickPaint.setAntiAlias(true);

        mPitchStickHighlightedPaint.setShader(null);
        mPitchStickHighlightedPaint.setColor(mPitchStickHighlightedColor);
        mPitchStickHighlightedPaint.setAntiAlias(true);

        if (uninitializedOrNoLyrics()) {
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
                    assureAnimationForPitchIndicator(0); // Force stop the animation when there is a too long stop between two lines
                    if (mTimestampForLastAnimationDecrease < 0 || this.mScoringMachine.getCurrentProgress() - mTimestampForLastAnimationDecrease > 4 * 1000) {
                        ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(600).start(); // Decrease the local pitch indicator
                        mTimestampForLastAnimationDecrease = this.mScoringMachine.getCurrentProgress();
                    }
                }
            }

            float pixelsAwayFromPilot = (startTime - this.mScoringMachine.getCurrentProgress()) * mMovingPixelsPerMs; // For every time, we need to locate the new coordinate
            float x = mCenterXOfStartPoint + pixelsAwayFromPilot;

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
                x = mCenterXOfStartPoint + pixelsAwayFromPilot;
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

                if (Math.abs(x - mCenterXOfStartPoint) <= 2 * mLocalPitchIndicatorRadius || Math.abs(endX - mCenterXOfStartPoint) <= 2 * mLocalPitchIndicatorRadius) { // Only mark item around local pitch indicator
                    boolean isJustHighlightTriggered = (!tone.highlight) && mInHighlightStatus;
                    if (isJustHighlightTriggered && Math.abs(x - mCenterXOfStartPoint) <= 400 * mMovingPixelsPerMs) {
                        tone.highlightOffset = Math.abs(mCenterXOfStartPoint - x);
                        if (tone.highlightOffset >= widthOfPitchStick) {
                            tone.highlightOffset = 0.5f * widthOfPitchStick;
                        }
                    }
                    boolean isJustDeHighlightTriggered = (tone.highlight) && !mInHighlightStatus;
                    if (isJustDeHighlightTriggered && tone.highlightWidth < 0) {
                        tone.highlightWidth = Math.abs(mCenterXOfStartPoint - x - tone.highlightOffset);
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

                    if (x >= mCenterXOfStartPoint) {
                        RectF rNormal = buildRectF(x, y, endX, y + mPitchStickHeight);
                        canvas.drawRoundRect(rNormal, 8, 8, mPitchStickPaint);
                    } else if (x < mCenterXOfStartPoint && endX > mCenterXOfStartPoint) {
                        RectF rNormalRightHalf = buildRectF(mCenterXOfStartPoint, y, endX, y + mPitchStickHeight);
                        canvas.drawRoundRect(rNormalRightHalf, 8, 8, mPitchStickPaint);

                        RectF rNormalLeftHalf = buildRectF(x, y, mCenterXOfStartPoint, y + mPitchStickHeight);
                        canvas.drawRoundRect(rNormalLeftHalf, 8, 8, mPitchStickPaint);

                        if (tone.highlightOffset >= 0) { // Partially draw
                            float highlightStartX = x + tone.highlightOffset;
                            float highlightEndX = mCenterXOfStartPoint;
                            if (tone.highlightWidth >= 0) {
                                highlightEndX = x + tone.highlightOffset + tone.highlightWidth;
                                if (highlightEndX > mCenterXOfStartPoint) {
                                    highlightEndX = mCenterXOfStartPoint;
                                }
                            }

                            RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + mPitchStickHeight);
                            if (tone.highlightOffset <= 0 || highlightEndX == mCenterXOfStartPoint) {
                                canvas.drawRoundRect(rHighlight, 8, 8, mPitchStickHighlightedPaint);
                            } else {
                                // Should be drawRect
                                canvas.drawRoundRect(rHighlight, 8, 8, mPitchStickHighlightedPaint);
                            }
                        }
                    } else if (endX <= mCenterXOfStartPoint) {
                        RectF rNormal = buildRectF(x, y, endX, y + mPitchStickHeight);
                        canvas.drawRoundRect(rNormal, 8, 8, mPitchStickPaint);

                        if (tone.highlightOffset >= 0) { // Partially draw
                            float highlightStartX = x + tone.highlightOffset;
                            float highlightEndX = endX;
                            if (tone.highlightWidth >= 0) {
                                highlightEndX = x + tone.highlightOffset + tone.highlightWidth;
                            }

                            RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + mPitchStickHeight);
                            if (tone.highlightOffset <= 0 && tone.highlightWidth <= 0 || highlightEndX == endX) {
                                canvas.drawRoundRect(rHighlight, 8, 8, mPitchStickHighlightedPaint);
                            } else {
                                // Should be drawRect
                                canvas.drawRoundRect(rHighlight, 8, 8, mPitchStickHighlightedPaint);
                            }
                        }
                    }
                    tweakTheHighlightAnimation(endX);
                } else {
                    RectF rNormal = buildRectF(x, y, endX, y + mPitchStickHeight);
                    canvas.drawRoundRect(rNormal, 8, 8, mPitchStickPaint);
                }

                if (DEBUG) {
                    mPitchStickHighlightedPaint.setTextSize(28);
                    canvas.drawText(tone.word , x, 30, mPitchStickHighlightedPaint);
                    canvas.drawText((int) (x) + "_" + (int) (endX), x, 60, mPitchStickHighlightedPaint);
                    canvas.drawText((int) (tone.begin) + "", x, 90, mPitchStickHighlightedPaint);
                    canvas.drawText((int) (tone.end) + "", x, 120, mPitchStickHighlightedPaint);
                }
            }
        }
    }
    //</editor-fold>

    private volatile boolean mEmittingInitialized = false;

    private void tweakTheHighlightAnimation(float endX) {
        if (!mEnableParticleEffect) {
            return;
        }
        if (endX > mCenterXOfStartPoint) { // Animation Enhancement, If still to far from end, just keep the animation ongoing and update the coordinate
            float value = getYForPitchIndicator();
            // It works with an emision range
            int[] location = new int[2];
            this.getLocationInWindow(location);
            if (mEmittingInitialized && mParticleSystem != null) {
                mParticleSystem.updateEmitPoint((int) (location[0] + mCenterXOfStartPoint), location[1] + (int) (value));
            }
        }
    }

    protected volatile float mLocalPitch = 0.0F;

    /**
     * Called automatically when animation of local indicator triggered
     * <p>
     * Mark it protected for implicit accessing from subclass
     *
     * @param pitch
     */
    protected final void setMLocalPitch(float pitch) {
        this.mLocalPitch = pitch;
    }

    private long mTimestampForLastAnimationDecrease = -1;

    private long mLastViewInvalidateTs;

    protected ScoringMachine mScoringMachine;

    protected LyricsModel mLyricsModel;

    public final void attachToScoringMachine(ScoringMachine machine) {
        if (!machine.isReady()) {
            throw new IllegalStateException("Must call ScoringMachine.prepare before attaching");
        }
        this.mScoringMachine = machine;
        this.mLyricsModel = machine.getLyricsModel();

        // Update values from UI view
        this.mScoringMachine.setInitialScore(mInitialScore);
    }

    public final void requestRefreshUi() {
        if (mScoringMachine == null) {
            return;
        }

        mHandler.removeCallbacks(mRemoveNoPitchAnimationCallback);
        mHandler.postDelayed(mRemoveNoPitchAnimationCallback, mThresholdOfOffProgressTime);

        performInvalidateIfNecessary();
    }

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

    private final Runnable mRemoveNoPitchAnimationCallback = new Runnable() { // No pitch
        @Override
        public void run() {
            assureAnimationForPitchIndicator(0); // Force stop the animation when there is no pitch
            int duration = 600;
            ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(duration).start(); // Decrease the local pitch indicator

            int tryN = 20;
            while (tryN-- > 0) { // Workaround: when no `setProgress` any more, start self-triggering
                postInvalidateDelayed((20 - tryN) * (duration / 20L));
            }
        }
    };

    protected final boolean uninitializedOrNoLyrics() {
        return mLyricsModel == null || mLyricsModel.lines == null || mLyricsModel.lines.isEmpty();
    }

    public final void updatePitchAndScore(final float pitch, final double scoreAfterNormalization, final boolean betweenCurrentPitch) {
        if (uninitializedOrNoLyrics()) {
            return;
        }

        if (pitch <= 0 || pitch < this.mScoringMachine.getMinimumRefPitch() || pitch > this.mScoringMachine.getMaximumRefPitch()) {
            // Actually no pitch <= 0 comes here
            assureAnimationForPitchIndicator(0); // When invalid pitches coming, we just stop animation
            return;
        }

        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    performIndicatorAnimationIfNecessary(pitch, scoreAfterNormalization);
                }
            });
        } else {
            performIndicatorAnimationIfNecessary(pitch, scoreAfterNormalization);
        }
    }

    private long lastCurrentTs = 0;

    private void performIndicatorAnimationIfNecessary(float pitch, double scoreAfterNormalization) {
        if (System.currentTimeMillis() - lastCurrentTs > 200) {
            int duration = (ScoringView.this.mLocalPitch == 0 && pitch > 0) ? 20 : 80;
            ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, pitch).setDuration(duration).start();
            lastCurrentTs = System.currentTimeMillis();
            assureAnimationForPitchIndicator(scoreAfterNormalization);
        }
    }

    protected final void assureAnimationForPitchIndicator(double scoreAfterNormalization) {
        // Be very careful if you wanna add condition case
        // Should not related with lyrics or other status
        if (!mEnableParticleEffect) {
            return;
        }

        // Animation for particle
        if (scoreAfterNormalization >= mThresholdOfHitScore) {
            if (mParticleSystem != null) {
                float value = getYForPitchIndicator();
                // It works with an emision range
                int[] location = new int[2];
                this.getLocationInWindow(location);
                if (!mEmittingInitialized) {
                    mEmittingInitialized = true;
                    mParticleSystem.emit((int) (location[0] + mCenterXOfStartPoint), location[1] + (int) (value), mParticlesPerSecond);
                } else {
                    mParticleSystem.updateEmitPoint((int) (location[0] + mCenterXOfStartPoint), location[1] + (int) (value));
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

    public final void resetPitchIndicatorAndAnimation() {
        mInHighlightStatus = false;
        if (this.mLocalPitch != 0) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    assureAnimationForPitchIndicator(0);
                    ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(200).start(); // Decrease the local pitch indicator
                }
            });
        }
    }

    public final void resetPitchIndicatorAndAnimationWhenFullLineFinished(double score) {
        if (score == 0 && this.mLocalPitch != 0) {
            mInHighlightStatus = false;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", 0.0f).setDuration(10).start(); // Decrease the local pitch indicator
                }
            });
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                assureAnimationForPitchIndicator(0); // Force stop the animation when line just finished
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
                assureAnimationForPitchIndicator(0); // Force stop the animation when resetting
                ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", 0.0f).setDuration(10).start(); // Decrease the local pitch indicator
                invalidateForSureAndMarkTheTimestamp();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
}
