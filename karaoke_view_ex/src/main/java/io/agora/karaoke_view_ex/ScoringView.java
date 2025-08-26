package io.agora.karaoke_view_ex;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
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
import android.util.Pair;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.plattysoft.leonids.ParticleSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.internal.ScoringMachine;
import io.agora.karaoke_view_ex.internal.config.Config;
import io.agora.karaoke_view_ex.internal.model.LyricsPitchLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;

/**
 * View component for displaying karaoke scoring information.
 * Handles pitch visualization, scoring indicators, and animations.
 */
public class ScoringView extends View {
    /**
     * Horizontal bias for the start point position
     */
    private float mStartPointHorizontalBias = 0.4f;

    /**
     * Handler for managing delayed tasks and animations
     */
    private Handler mHandler;

    /**
     * Pixels moved per millisecond for animation
     */
    private float mMovingPixelsPerMs;

    /**
     * Height of pitch stick visualization
     */
    protected float mPitchStickHeight;

    /**
     * Radius of local pitch indicator
     */
    protected float mLocalPitchIndicatorRadius;

    /**
     * Color of local pitch indicator
     */
    protected int mLocalPitchIndicatorColor;

    /**
     * Paint for drawing local pitch indicator
     */
    protected final Paint mLocalPitchIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Corner effect for local pitch indicator
     */
    protected final CornerPathEffect mLocalPitchEffect = new CornerPathEffect(8);

    /**
     * Paint for drawing start line
     */
    protected final Paint mStartLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Paint for drawing overpast gradient
     */
    protected final Paint mOverpastLinearGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Linear gradient for overpast effect
     */
    protected LinearGradient mOverpastLinearGradient;

    /**
     * Paint for drawing pitch sticks
     */
    private final Paint mPitchStickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Default color for reference pitch stick
     */
    protected int mRefPitchStickDefaultColor;

    /**
     * Paint for highlighted pitch sticks
     */
    private final Paint mPitchStickHighlightedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Color for highlighted pitch sticks
     */
    private int mPitchStickHighlightedColor;

    /**
     * Center X coordinate of start point
     */
    protected float mCenterXOfStartPoint = 0f;

    /**
     * Paint for drawing leading lines
     */
    protected final Paint mLeadingLinesPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Particle system for visual effects
     */
    protected ParticleSystem mParticleSystem;

    /**
     * Particles emitted per second
     */
    protected int mParticlesPerSecond = 16;

    /**
     * Initial score value
     */
    private float mInitialScore;

    /**
     * Threshold score for hit detection
     */
    private float mThresholdOfHitScore;

    /**
     * Whether particle effects are enabled
     */
    private boolean mEnableParticleEffect;

    /**
     * Threshold time for off-progress detection
     */
    private long mThresholdOfOffProgressTime;

    /**
     * Timestamp of pitch highlight
     */
    private long mPitchHighlightedTime = -1;

    /**
     * Current local pitch value
     */
    protected volatile float mLocalPitch = 0.0F;

    /**
     * Timestamp of last animation decrease
     */
    private long mTimestampForLastAnimationDecrease = -1;

    /**
     * Timestamp of last view invalidation
     */
    private long mLastViewInvalidateTs;

    /**
     * Scoring machine instance
     */
    protected ScoringMachine mScoringMachine;

    /**
     * Token for delayed tasks
     */
    private Object mDelayedTaskToken;

    /**
     * Custom bitmap for local pitch indicator
     */
    protected Bitmap mCustomizedLocalPitchIndicator;

    /**
     * Current highlight status
     */
    private boolean mInHighlightStatus;

    /**
     * Previous highlight status
     */
    private boolean mPreHighlightStatus = false;

    /**
     * Whether emitting is initialized
     */
    private volatile boolean mEmittingInitialized = false;


    /**
     * RectF object for avoiding new object creation
     */
    private final RectF mRectFAvoidingNewObject = new RectF(0, 0, 0, 0);

    /**
     * Whether the scoring machine is uninitialized or has no lyrics
     */
    private long lastCurrentTs = 0;

    /**
     * Smooth animated pitch following mLocalPitch
     */
    private float mAnimatedPitch = 0.0f;

    /**
     * Smooth animated progress for rendering X positions
     */
    private double mAnimatedProgressMs = 0.0;

    /**
     * Frame loop state for vsync-driven updates
     */
    private boolean mFrameLoopRunning = false;
    private long mLastFrameTimeNanos = 0L;
    private final Choreographer.FrameCallback mFrameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            stepFrame(frameTimeNanos);
        }
    };

    /**
     * Called automatically when animation of local indicator triggered
     * <p>
     * Mark it protected for implicit accessing from subclass
     *
     * @param pitch New pitch value
     */
    protected final void setMLocalPitch(float pitch) {
        this.mLocalPitch = pitch;
    }

    /**
     * Default constructor
     *
     * @param context Application context
     */
    public ScoringView(Context context) {
        this(context, null);
    }

    /**
     * Constructor with attributes
     *
     * @param context Application context
     * @param attrs   Attribute set from XML
     */
    public ScoringView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Constructor with style attribute
     *
     * @param context      Application context
     * @param attrs        Attribute set from XML
     * @param defStyleAttr Default style attribute
     */
    public ScoringView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ScoringView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    /**
     * Set default color for reference pitch stick
     *
     * @param color Color value to set
     */
    public void setRefPitchStickDefaultColor(@ColorInt int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.default_popular_color);
        }
        this.mRefPitchStickDefaultColor = color;

        performInvalidateIfNecessary();
    }

    /**
     * Set color for highlighted pitch stick
     *
     * @param color Color value to set
     */
    public void setRefPitchStickHighlightedColor(int color) {
        if (color == 0) {
            color = getResources().getColor(R.color.pitch_stick_highlighted_color);
        }
        this.mPitchStickHighlightedColor = color;

        performInvalidateIfNecessary();
    }

    /**
     * Set height for reference pitch stick
     *
     * @param height Height value to set
     */
    public void setRefPitchStickHeight(float height) {
        this.mPitchStickHeight = height;
    }

    /**
     * Initialize view with attributes
     *
     * @param attrs Attribute set from XML
     */
    private void init(@Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        this.mHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
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

    /**
     * Handle view measurement
     *
     * @param widthMeasureSpec  Width measurement specifications
     * @param heightMeasureSpec Height measurement specifications
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Handle view layout changes
     *
     * @param changed Whether the layout has changed
     * @param left    Left position
     * @param top     Top position
     * @param right   Right position
     * @param bottom  Bottom position
     */
    @SuppressLint("DrawAllocation")
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

            tryEnableParticleEffect(null);
        }
    }

    /**
     * Check if canvas is ready for drawing
     *
     * @return true if canvas is not ready
     */
    protected final boolean isCanvasNotReady() {
        return mCenterXOfStartPoint <= 0;
    }

    /**
     * Enable or disable particle effect
     *
     * @param enable Whether to enable particle effect
     */
    public void enableParticleEffect(boolean enable) {
        this.mEnableParticleEffect = enable;

        if (enable) {
            tryEnableParticleEffect(null);
        } else {
            tryDisableParticleEffect();
        }
    }

    /**
     * Enable particle effect with custom particles
     *
     * @param enable    Whether to enable particle effect
     * @param particles Custom particle drawables
     */
    public void enableParticleEffect(boolean enable, Drawable[] particles) {
        this.mEnableParticleEffect = enable;

        if (enable) {
            tryEnableParticleEffect(particles);
        } else {
            tryDisableParticleEffect();
        }
    }

    /**
     * Set threshold for hit score detection
     *
     * @param thresholdOfHitScore Score threshold value (0-1)
     */
    public void setThresholdOfHitScore(float thresholdOfHitScore) {
        if (thresholdOfHitScore <= 0 || thresholdOfHitScore > 1.0f) {
            LogUtils.e("Invalid value for hitScoreThreshold, must > 0 and <= 1, current is " + thresholdOfHitScore);
            return;
        }
        mThresholdOfHitScore = thresholdOfHitScore;
    }

    /**
     * Try to enable particle effect with optional custom particles
     *
     * @param particles Custom particle drawables (optional)
     */
    private void tryEnableParticleEffect(Drawable[] particles) {
        if (mEnableParticleEffect) {
            if (mDelayedTaskToken != null) {
                mHandler.removeCallbacksAndMessages(mDelayedTaskToken);
                mDelayedTaskToken = null;
            }

            mDelayedTaskToken = new Object();
            mHandler.postAtTime(() -> {
                setParticles(particles);
            }, mDelayedTaskToken, SystemClock.uptimeMillis() + 1000);
        }
    }

    /**
     * Try to disable particle effect
     */
    private void tryDisableParticleEffect() {
        if (!mEnableParticleEffect) {
            if (mDelayedTaskToken != null) {
                mHandler.removeCallbacksAndMessages(mDelayedTaskToken);
                mDelayedTaskToken = null;
            }

            mDelayedTaskToken = new Object();
            mHandler.postAtTime(() -> {
                if (mParticleSystem != null) {
                    mParticleSystem.cancel();
                }
            }, mDelayedTaskToken, SystemClock.uptimeMillis() + 30);
        }
    }

    /**
     * Set custom particles for the particle system
     *
     * @param particles Array of particle drawables
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

    /**
     * Build default particle drawables list
     *
     * @return Array of default particle drawables
     */
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

    /**
     * Initialize particle system with custom particles
     *
     * @param particles Array of particle drawables
     */
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
     * Build a RectF object with given coordinates
     *
     * @param left   Left coordinate
     * @param top    Top coordinate
     * @param right  Right coordinate
     * @param bottom Bottom coordinate
     * @return RectF object
     */
    private RectF buildRectF(float left, float top, float right, float bottom) {
        mRectFAvoidingNewObject.left = left;
        mRectFAvoidingNewObject.top = top;
        mRectFAvoidingNewObject.right = right;
        mRectFAvoidingNewObject.bottom = bottom;
        return mRectFAvoidingNewObject;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // Fail fast if canvas is not ready
        if (isCanvasNotReady()) {
            return;
        }

        drawLeadingLines(canvas);
        drawOverPastWallAndStartLine(canvas);
        drawPitchSticks(canvas);
        drawLocalPitchIndicator(canvas);

        if (Config.DEBUG) {
            long progress = 0;
            int id = 0;
            if (!uninitializedOrNoLyrics(mScoringMachine)) {
                progress = mScoringMachine.getCurrentPitchProgress();
                id = System.identityHashCode(mScoringMachine);
            }

            mPitchStickHighlightedPaint.setTextSize(28);
            canvas.drawText("id: " + id + ", current=" + System.currentTimeMillis()
                            + ", progress: " + (int) (progress)
                            + ", y: " + (int) (getYForPitchIndicator()) + ", pitch: " + (int) (mLocalPitch),
                    20, getHeight() - 30, mPitchStickHighlightedPaint);
        }
    }

    /**
     * Set the local pitch indicator bitmap
     *
     * @param bitmap Custom bitmap for pitch indicator
     */
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

    /**
     * Draw the local pitch indicator
     *
     * @param canvas Canvas to draw on
     */
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

    /**
     * Calculate Y coordinate for pitch indicator
     *
     * @return Y coordinate value
     */
    protected float getYForPitchIndicator() {
        float targetY = 0;

        if (uninitializedOrNoLyrics(mScoringMachine)) { // Not initialized
            targetY = getHeight() - this.mLocalPitchIndicatorRadius;
        } else if (this.mAnimatedPitch >= mScoringMachine.getMinimumRefPitch() && mScoringMachine.getMaximumRefPitch() != 0) { // Has value, not the default case
            float realPitchMax = mScoringMachine.getMaximumRefPitch() + 5;
            float realPitchMin = mScoringMachine.getMinimumRefPitch() - 5;
            float mItemHeightPerPitchLevel = getHeight() / (realPitchMax - realPitchMin);
            targetY = (realPitchMax - this.mAnimatedPitch) * mItemHeightPerPitchLevel;
        } else if (this.mAnimatedPitch < mScoringMachine.getMinimumRefPitch()) { // minimal local pitch
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

    /**
     * Draw leading lines on canvas
     *
     * @param canvas Canvas to draw on
     */
    protected void drawLeadingLines(Canvas canvas) {
        int height = getHeight();
        int width = getWidth();

        int heightOfLine0 = 0 / 5;
        int heightOfLine1 = height / 5;
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

    /**
     * Draw overpass wall and start line
     *
     * @param canvas Canvas to draw on
     */
    protected void drawOverPastWallAndStartLine(Canvas canvas) {
        drawOverPastWall(canvas);

        drawStartLine(canvas);
    }

    /**
     * Draw the overpass wall effect
     *
     * @param canvas Canvas to draw on
     */
    protected final void drawOverPastWall(Canvas canvas) {
        mOverpastLinearGradientPaint.setShader(null);
        mOverpastLinearGradientPaint.setShader(mOverpastLinearGradient);
        mOverpastLinearGradientPaint.setAntiAlias(true);
        canvas.drawRect(0, 0, mCenterXOfStartPoint, getHeight(), mOverpastLinearGradientPaint);
    }

    /**
     * Draw the start line
     *
     * @param canvas Canvas to draw on
     */
    protected final void drawStartLine(Canvas canvas) {
        mStartLinePaint.setShader(null);
        mStartLinePaint.setColor(mLocalPitchIndicatorColor);
        mStartLinePaint.setAntiAlias(true);
        mStartLinePaint.setStrokeWidth(3);
        canvas.drawLine(mCenterXOfStartPoint, 0, mCenterXOfStartPoint, getHeight(), mStartLinePaint);
    }

    /**
     * Draw pitch sticks on canvas
     *
     * @param canvas Canvas to draw on
     */
    private void drawPitchSticks(Canvas canvas) {
        mPitchStickPaint.setShader(null);
        mPitchStickPaint.setColor(mRefPitchStickDefaultColor);
        mPitchStickPaint.setAntiAlias(true);

        mPitchStickHighlightedPaint.setShader(null);
        mPitchStickHighlightedPaint.setColor(mPitchStickHighlightedColor);
        mPitchStickHighlightedPaint.setAntiAlias(true);

        if (uninitializedOrNoLyrics(mScoringMachine)) {
            return;
        }

        float realPitchMax = mScoringMachine.getMaximumRefPitch() + 5;
        float realPitchMin = mScoringMachine.getMinimumRefPitch() - 5;

        List<LyricsPitchLineModel> lines;
        if (mScoringMachine.hasPitchData()) {
            lines = mScoringMachine.getPitchLines();
        } else {
            lines = new ArrayList<>(0);
        }

        if (null == lines || lines.isEmpty()) {
            return;
        }

        float y;
        float widthOfPitchStick;
        // make pitch stick always above bottom line
        float stickHeightPerPitchLevel = (getHeight() - mPitchStickHeight) / (realPitchMax - realPitchMin);

        // Not used so far
        long endTimeOfPreviousLine = 0;

        for (int i = 0; i < lines.size(); i++) {
            LyricsPitchLineModel line = lines.get(i);
            List<LyricsPitchLineModel.Pitch> pitches = line.pitches;
            if (pitches == null || pitches.isEmpty()) {
                continue;
            }

            long startTime = line.getStartTime();
            long durationOfCurrentLine = line.getEndTime() - startTime;

            long renderProgress = getRenderProgressMs();
            if (Math.abs(renderProgress - line.getEndTime()) * mMovingPixelsPerMs >= getWidth() &&
                    Math.abs(renderProgress - line.getStartTime()) * mMovingPixelsPerMs >= getWidth() &&
                    !(renderProgress >= line.getStartTime() && renderProgress <= line.getEndTime())) { // If still too early for current line, we do not draw the sticks
                continue;
            }

            if (i + 1 < lines.size() && line.getStartTime() < renderProgress) { // Has next line
                // Get next line
                // If start for next is far away than 2 seconds
                // stop the current animation now
                long startTimeOfNextLine = mScoringMachine.getLineStartTime(i + 1);
                if ((startTimeOfNextLine - line.getEndTime() >= 2 * 1000) && renderProgress > line.getEndTime() && renderProgress < startTimeOfNextLine) { // Two seconds after this line stops
                    assureAnimationForPitchIndicator(0); // Force stop the animation when there is a too long stop between two lines
                    if (mTimestampForLastAnimationDecrease < 0 || renderProgress - mTimestampForLastAnimationDecrease > 4 * 1000) {
                        ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(600).start(); // Decrease the local pitch indicator
                        mTimestampForLastAnimationDecrease = renderProgress;
                    }
                }
            }

            float pixelsAwayFromPilot = (startTime - renderProgress) * mMovingPixelsPerMs; // For every time, we need to locate the new coordinate
            float x = mCenterXOfStartPoint + pixelsAwayFromPilot;

            if (endTimeOfPreviousLine != 0) { // If has empty divider before
                // Not used so far
                int emptyDividerWidth = (int) (mMovingPixelsPerMs * (startTime - endTimeOfPreviousLine));
                x = x + emptyDividerWidth;
            }

            endTimeOfPreviousLine = line.getEndTime();

            if (x + 2 * durationOfCurrentLine * mMovingPixelsPerMs < 0) { // Already past for long time enough
                continue;
            }

            for (int pitchIndex = 0; pitchIndex < pitches.size(); pitchIndex++) {
                LyricsPitchLineModel.Pitch pitch = pitches.get(pitchIndex);

                // For every time, we need to locate the new coordinate
                pixelsAwayFromPilot = (pitch.begin - renderProgress) * mMovingPixelsPerMs;
                x = mCenterXOfStartPoint + pixelsAwayFromPilot;
                widthOfPitchStick = mMovingPixelsPerMs * pitch.getDuration();
                float endX = x + widthOfPitchStick;

                float pitchXStart = x;
                float pitchXEnd = x + mMovingPixelsPerMs * pitch.getDuration();

                // when moves out of the view port
                if (endX <= 0) {
                    pitch.resetHighlight();
                    continue;
                }

                // before moves into the view port
                if (x >= getWidth()) {
                    break;
                }

                y = (realPitchMax - pitch.pitch) * stickHeightPerPitchLevel;
                boolean isCurrentPitch = renderProgress >= pitch.begin && renderProgress <= pitch.end;

                if (!isCurrentPitch) {
                    for (Map.Entry<Long, Pair<Long, Long>> entry : pitch.highlightPartMap.entrySet()) {
                        long key = entry.getKey();
                        Pair<Long, Long> value = entry.getValue();
                        if (null == value) {
                            entry.setValue(new Pair<>(key, pitch.end));
                        }
                    }
                }

                RectF rNormal = buildRectF(pitchXStart, y, pitchXEnd, y + mPitchStickHeight);
                canvas.drawRoundRect(rNormal, 8, 8, mPitchStickPaint);

                if (isCurrentPitch) {
                    if (mInHighlightStatus) {
                        if (!mPreHighlightStatus) {
                            mPitchHighlightedTime = renderProgress;
                            if (renderProgress - pitch.begin < Constants.INTERVAL_AUDIO_PCM) {
                                mPitchHighlightedTime = pitch.begin;
                            }
                            mPreHighlightStatus = mInHighlightStatus;
                        }
                        float highlightStartX = mCenterXOfStartPoint + (mPitchHighlightedTime - renderProgress) * mMovingPixelsPerMs;
                        //着色 40ms一段
                        float highlightEndX = mCenterXOfStartPoint - mMovingPixelsPerMs * Constants.INTERVAL_AUDIO_PCM;
                        RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + mPitchStickHeight);
                        canvas.drawRoundRect(rHighlight, 8, 8, mPitchStickHighlightedPaint);
                        if (!pitch.highlightPartMap.containsKey(mPitchHighlightedTime)) {
                            pitch.highlightPartMap.put(mPitchHighlightedTime, null);
                        }
                    } else {
                        mPreHighlightStatus = false;
                        if (-1 != mPitchHighlightedTime) {
                            pitch.highlightPartMap.put(mPitchHighlightedTime, new Pair<>(mPitchHighlightedTime, renderProgress));
                            mPitchHighlightedTime = -1;
                        }
                    }
                } else {
                    if (mPreHighlightStatus) {
                        mPreHighlightStatus = false;
                    }
                    if (-1 != mPitchHighlightedTime) {
                        mPitchHighlightedTime = -1;
                    }
                }
                if (x < mCenterXOfStartPoint) {
                    for (Map.Entry<Long, Pair<Long, Long>> entry : pitch.highlightPartMap.entrySet()) {
                        Pair<Long, Long> value = entry.getValue();
                        if (null != value) {
                            long highlightStartTime = value.first;
                            long highlightEndTime = value.second;

                            float highlightStartX = mCenterXOfStartPoint + (highlightStartTime - renderProgress) * mMovingPixelsPerMs;
                            float highlightEndX = highlightStartX + mMovingPixelsPerMs * (highlightEndTime - highlightStartTime);
                            RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + mPitchStickHeight);
                            canvas.drawRoundRect(rHighlight, 8, 8, mPitchStickHighlightedPaint);
                        } else {
                            if (isCurrentPitch) {
                                long highlightStartTime = entry.getKey();

                                float highlightStartX = mCenterXOfStartPoint + (highlightStartTime - renderProgress) * mMovingPixelsPerMs;
                                float highlightEndX = mCenterXOfStartPoint;
                                RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + mPitchStickHeight);
                                canvas.drawRoundRect(rHighlight, 8, 8, mPitchStickHighlightedPaint);
                            }
                        }
                    }

                }
            }
        }
    }

    /**
     * Tweak highlight animation
     *
     * @param endX End X coordinate
     */
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


    /**
     * Attach to a scoring machine
     *
     * @param machine Scoring machine instance
     */
    public final void attachToScoreMachine(ScoringMachine machine) {
        if (!machine.isReady()) {
            throw new IllegalStateException("Must call ScoringMachine.prepare before attaching");
        }
        this.mScoringMachine = machine;

        // Update values from UI view
        this.mScoringMachine.setInitialScore(mInitialScore);

        startFrameLoopIfNeeded();

        // Initialize animated progress to avoid initial jump
        mAnimatedProgressMs = mScoringMachine.getCurrentPitchProgress();
    }

    /**
     * Detach from the scoring machine
     */
    public final void requestRefreshUi() {
        if (mScoringMachine == null) {
            return;
        }

        mHandler.removeCallbacks(mRemoveNoPitchAnimationCallback);
        mHandler.postDelayed(mRemoveNoPitchAnimationCallback, mThresholdOfOffProgressTime);

        performInvalidateIfNecessary();
    }

    /**
     * Invalidate the view if necessary
     */
    private void performInvalidateIfNecessary() {
        long delta = System.currentTimeMillis() - mLastViewInvalidateTs;
        if (delta <= 16) {
            return;
        }

        invalidateForSureAndMarkTheTimestamp();
    }

    /**
     * Invalidate the view for sure and mark the timestamp
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
     * Remove no pitch animation callback
     */
    private final Runnable mRemoveNoPitchAnimationCallback = new Runnable() {
        @Override
        public void run() {
            assureAnimationForPitchIndicator(0); // Force stop the animation when there is no pitch
            int duration = 600;
            ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(duration).start(); // Decrease the local pitch indicator

            int tryN = 20;
            // Workaround: when no `setProgress` any more, start self-triggering animation
            while (tryN-- > 0) {
                postInvalidateDelayed((20 - tryN) * (duration / 20L));
            }
        }
    };

    /**
     * Check if scoring machine is uninitialized or no lyrics
     *
     * @param machine Scoring machine instance
     * @return true if uninitialized or no lyrics
     */
    protected final boolean uninitializedOrNoLyrics(ScoringMachine machine) {
        return machine == null || mScoringMachine == null;
    }

    /**
     * Update pitch and score
     *
     * @param speakerPitch Speaker pitch value
     * @param score        Score value
     */
    public void updatePitchAndScore(float speakerPitch, final float score) {
        if (uninitializedOrNoLyrics(mScoringMachine)) {
            return;
        }

        mLocalPitch = speakerPitch;

        startFrameLoopIfNeeded();

        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    performIndicatorAnimationIfNecessary(score);
                }
            });
        } else {
            performIndicatorAnimationIfNecessary(score);
        }

    }


    /**
     * Perform indicator animation if necessary
     *
     * @param scoreAfterNormalization Score value after normalization
     */
    private void performIndicatorAnimationIfNecessary(final double scoreAfterNormalization) {
        if (System.currentTimeMillis() - lastCurrentTs >= 80) {
            //int duration = (ScoringView.this.mLocalPitch == 0 && pitch > 0) ? 20 : 80;
            //ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, pitch).setDuration(duration).start();
            lastCurrentTs = System.currentTimeMillis();
            assureAnimationForPitchIndicator(scoreAfterNormalization);
        }
    }

    /**
     * Assure animation for pitch indicator
     *
     * @param scoreAfterNormalization Score value after normalization
     */
    protected final void assureAnimationForPitchIndicator(double scoreAfterNormalization) {
        // Be very careful if you wanna add condition case
        // Should not related with lyrics or other status

        // Animation for particle
        if (scoreAfterNormalization >= mThresholdOfHitScore * 100) {
            mInHighlightStatus = true;
            if (mEnableParticleEffect && mParticleSystem != null) {
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
        } else {
            mInHighlightStatus = false;
            if (mEnableParticleEffect && mParticleSystem != null) {
                mParticleSystem.stopEmitting();
            }
        }
    }

    /**
     * Reset pitch indicator and animation
     */
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

    /**
     * Reset pitch indicator and animation when full line finished
     *
     * @param score Score value
     */
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

    /**
     * Reset the view
     */
    public void reset() {
        resetInternal();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                assureAnimationForPitchIndicator(0); // Force stop the animation when resetting
                ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", 0.0f).setDuration(10).start(); // Decrease the local pitch indicator
                invalidateForSureAndMarkTheTimestamp();
            }
        });
    }

    /**
     * Reset internal state of the view
     */
    private void resetInternal() {
        mInHighlightStatus = false;
        mPreHighlightStatus = false;
        mPitchHighlightedTime = -1;
        mScoringMachine = null;
        mAnimatedProgressMs = 0.0;
        mAnimatedPitch = 0.0f;
    }

    /**
     * Handle view detachment from window
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopFrameLoop();
        if (mParticleSystem != null) {
            mParticleSystem.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startFrameLoopIfNeeded();
    }

    private void startFrameLoopIfNeeded() {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            post(new Runnable() {
                @Override
                public void run() {
                    startFrameLoopIfNeeded();
                }
            });
            return;
        }
        if (mFrameLoopRunning) {
            return;
        }
        mFrameLoopRunning = true;
        mLastFrameTimeNanos = 0L;
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    private void stopFrameLoop() {
        if (!mFrameLoopRunning) {
            return;
        }
        mFrameLoopRunning = false;
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
        mLastFrameTimeNanos = 0L;
    }

    private void stepFrame(long frameTimeNanos) {
        if (!mFrameLoopRunning) {
            return;
        }
        if (mLastFrameTimeNanos == 0L) {
            mLastFrameTimeNanos = frameTimeNanos;
        }
        long dtNanos = frameTimeNanos - mLastFrameTimeNanos;
        mLastFrameTimeNanos = frameTimeNanos;
        float dtMs = dtNanos / 1_000_000f;
        if (dtMs < 0) dtMs = 0;
        if (dtMs > 100) dtMs = 100; // clamp to avoid jumps after pauses

        // Exponential smoothing towards target pitch
        float tauMs = 120f; // smoothing constant (~response time)
        float alpha = 1f - (float) Math.exp(-dtMs / tauMs);
        mAnimatedPitch = mAnimatedPitch + (mLocalPitch - mAnimatedPitch) * alpha;

        // Exponential smoothing for progress to remove 40/80ms stepping
        if (!uninitializedOrNoLyrics(mScoringMachine)) {
            double machineProgress = mScoringMachine.getCurrentPitchProgress();
            float tauProgMs = 60f; // faster response for X movement
            float alphaProg = 1f - (float) Math.exp(-dtMs / tauProgMs);
            // If large jump (seek/reset), snap to target to avoid long lag
            if (Math.abs(mAnimatedProgressMs - machineProgress) > 2000) {
                mAnimatedProgressMs = machineProgress;
            } else {
                mAnimatedProgressMs = mAnimatedProgressMs + (machineProgress - mAnimatedProgressMs) * alphaProg;
            }
        }

        // Smoothly update particle emission point while emitting
        if (mEnableParticleEffect && mParticleSystem != null && mInHighlightStatus) {
            float value = getYForPitchIndicator();
            int[] location = new int[2];
            this.getLocationInWindow(location);
            mParticleSystem.updateEmitPoint((int) (location[0] + mCenterXOfStartPoint), location[1] + (int) (value));
        }

        performInvalidateIfNecessary();
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    private long getRenderProgressMs() {
        return (long) (mAnimatedProgressMs);
    }
}
