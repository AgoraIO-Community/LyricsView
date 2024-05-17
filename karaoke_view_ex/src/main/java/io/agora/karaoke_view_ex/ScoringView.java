package io.agora.karaoke_view_ex;

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
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.plattysoft.leonids.ParticleSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.internal.LyricMachine;
import io.agora.karaoke_view_ex.internal.config.Config;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsPitchLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * 激励，互动视图
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/08/04
 */
public class ScoringView extends View {
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
    private long mPitchHighlightedTime = -1;

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

    public void setRefPitchStickDefaultColor(@ColorInt int color) {
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

            tryEnableParticleEffect(null);
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
     * @param enable enable or not
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
     * Enable particle effect or not
     * <p>
     * Do not call this regularly, this is expensive
     *
     * @param enable    enable or not
     * @param particles the particles to be used
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
     * set the threshold of hit score
     *
     * @param thresholdOfHitScore the threshold of hit score, must > 0 and <= 1
     */
    public void setThresholdOfHitScore(float thresholdOfHitScore) {
        if (thresholdOfHitScore <= 0 || thresholdOfHitScore > 1.0f) {
            LogUtils.e("Invalid value for hitScoreThreshold, must > 0 and <= 1, current is " + thresholdOfHitScore);
            return;
        }
        mThresholdOfHitScore = thresholdOfHitScore;
    }

    private Object mDelayedTaskToken;

    private void tryEnableParticleEffect(Drawable[] particles) {
        if (mEnableParticleEffect) {
            if (mDelayedTaskToken != null) {
                mHandler.removeCallbacksAndMessages(mDelayedTaskToken);
                mDelayedTaskToken = null;
            }

            mDelayedTaskToken = new Object();
            boolean result = mHandler.postAtTime(() -> {
                // Create a particle system and start emiting
                setParticles(particles);
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

        if (Config.DEBUG) {
            long progress = 0;
            int id = 0;
            LyricMachine machine = this.mLyricMachine;
            if (!uninitializedOrNoLyrics(machine)) {
                progress = machine.getCurrentPitchProgress();
                id = System.identityHashCode(machine);
            }

            mPitchStickHighlightedPaint.setTextSize(28);
            canvas.drawText("id: " + id + ", current=" + System.currentTimeMillis()
                            + ", progress: " + (int) (progress)
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

        LyricMachine machine = this.mLyricMachine;
        if (uninitializedOrNoLyrics(machine)) { // Not initialized
            targetY = getHeight() - this.mLocalPitchIndicatorRadius;
        } else if (this.mLocalPitch >= machine.getMinimumRefPitch() && machine.getMaximumRefPitch() != 0) { // Has value, not the default case
            float realPitchMax = machine.getMaximumRefPitch() + 5;
            float realPitchMin = machine.getMinimumRefPitch() - 5;
            float mItemHeightPerPitchLevel = getHeight() / (realPitchMax - realPitchMin);
            targetY = (realPitchMax - this.mLocalPitch) * mItemHeightPerPitchLevel;
        } else if (this.mLocalPitch < machine.getMinimumRefPitch()) { // minimal local pitch
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


        LyricMachine machine = this.mLyricMachine;
        if (uninitializedOrNoLyrics(machine)) {
            return;
        }

        float realPitchMax = machine.getMaximumRefPitch() + 5;
        float realPitchMin = machine.getMinimumRefPitch() - 5;

        List<LyricsPitchLineModel> lines;
        if (mLyricsModel.type == LyricType.KRC) {
            lines = mLyricMachine.getShowLyricsLines();
        } else {
            lines = new ArrayList<>(0);
        }

        float y;
        float widthOfPitchStick;
        float stickHeightPerPitchLevel = (getHeight() - mPitchStickHeight /** make pitch stick always above bottom line **/) / (realPitchMax - realPitchMin);

        long endTimeOfPreviousLine = 0; // Not used so far

        for (int i = 0; i < lines.size(); i++) {
            LyricsPitchLineModel line = lines.get(i);
            List<LyricsPitchLineModel.Pitch> pitches = line.pitches;
            if (pitches == null || pitches.isEmpty()) {
                return;
            }

            long startTime = line.getStartTime();
            long durationOfCurrentLine = line.getEndTime() - startTime;

            if (machine.getCurrentPitchProgress() - startTime <= -(2 * durationOfCurrentLine)) { // If still to early for current line, we do not draw the sticks
                // If we show the sticks too late, they will appear suddenly in the central of screen, not start from the right side
                break;
            }

            if (i + 1 < lines.size() && line.getStartTime() < machine.getCurrentPitchProgress()) { // Has next line
                // Get next line
                // If start for next is far away than 2 seconds
                // stop the current animation now
                long startTimeOfNextLine = mLyricsModel.lines.get(i + 1).getStartTime();
                if ((startTimeOfNextLine - line.getEndTime() >= 2 * 1000) && machine.getCurrentPitchProgress() > line.getEndTime() && machine.getCurrentPitchProgress() < startTimeOfNextLine) { // Two seconds after this line stops
                    assureAnimationForPitchIndicator(0); // Force stop the animation when there is a too long stop between two lines
                    if (mTimestampForLastAnimationDecrease < 0 || machine.getCurrentPitchProgress() - mTimestampForLastAnimationDecrease > 4 * 1000) {
                        ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, ScoringView.this.mLocalPitch * 1 / 3, 0.0f).setDuration(600).start(); // Decrease the local pitch indicator
                        mTimestampForLastAnimationDecrease = machine.getCurrentPitchProgress();
                    }
                }
            }

            float pixelsAwayFromPilot = (startTime - machine.getCurrentPitchProgress()) * mMovingPixelsPerMs; // For every time, we need to locate the new coordinate
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


                pixelsAwayFromPilot = (pitch.begin - machine.getCurrentPitchProgress()) * mMovingPixelsPerMs; // For every time, we need to locate the new coordinate
                x = mCenterXOfStartPoint + pixelsAwayFromPilot;
                widthOfPitchStick = mMovingPixelsPerMs * pitch.getDuration();
                float endX = x + widthOfPitchStick;

                float pitchXStart = x;
                float pitchXEnd = x + mMovingPixelsPerMs * pitch.getDuration();


                if (endX <= 0) { // when moves out of the view port
                    pitch.resetHighlight();
                    continue;
                }

                if (x >= getWidth()) { // before moves into the view port
                    break;
                }

                y = (realPitchMax - pitch.pitch) * stickHeightPerPitchLevel;
                boolean isCurrentPitch = machine.getCurrentPitchProgress() >= pitch.begin && machine.getCurrentPitchProgress() <= pitch.end;

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
                            mPitchHighlightedTime = machine.getCurrentPitchProgress();
                            if (machine.getCurrentPitchProgress() - pitch.begin < Constants.INTERVAL_AUDIO_PCM) {
                                mPitchHighlightedTime = pitch.begin;
                            }
                            mPreHighlightStatus = mInHighlightStatus;
                        }
                        float highlightStartX = mCenterXOfStartPoint + (mPitchHighlightedTime - machine.getCurrentPitchProgress()) * mMovingPixelsPerMs;
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
                            pitch.highlightPartMap.put(mPitchHighlightedTime, new Pair<>(mPitchHighlightedTime, machine.getCurrentPitchProgress()));
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

                            float highlightStartX = mCenterXOfStartPoint + (highlightStartTime - machine.getCurrentPitchProgress()) * mMovingPixelsPerMs;
                            float highlightEndX = highlightStartX + mMovingPixelsPerMs * (highlightEndTime - highlightStartTime);
                            RectF rHighlight = buildRectF(highlightStartX, y, highlightEndX, y + mPitchStickHeight);
                            canvas.drawRoundRect(rHighlight, 8, 8, mPitchStickHighlightedPaint);
                        } else {
                            if (isCurrentPitch) {
                                long highlightStartTime = entry.getKey();

                                float highlightStartX = mCenterXOfStartPoint + (highlightStartTime - machine.getCurrentPitchProgress()) * mMovingPixelsPerMs;
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

    protected LyricMachine mLyricMachine;

    protected LyricModel mLyricsModel;

    public final void attachToLyricMachine(LyricMachine machine) {
        if (!machine.isReady()) {
            throw new IllegalStateException("Must call ScoringMachine.prepare before attaching");
        }
        this.mLyricMachine = machine;
        this.mLyricsModel = machine.getLyricsModel();
    }

    public final void requestRefreshUi() {
        if (mLyricMachine == null) {
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

    protected final boolean uninitializedOrNoLyrics(LyricMachine machine) {
        return machine == null || mLyricMachine == null || mLyricsModel == null || mLyricsModel.lines == null || mLyricsModel.lines.isEmpty();
    }

    public final void setPitch(float speakerPitch, float refPitch) {
        LyricMachine machine = this.mLyricMachine;
        if (uninitializedOrNoLyrics(machine)) {
            return;
        }
        double scoreAfterNormalization = 0;
        if (refPitch != 0) {
            if (mLyricsModel.type == LyricType.KRC) {
                switch ((int) speakerPitch) {
                    case 1:
                        speakerPitch = refPitch - 2;
                        scoreAfterNormalization = 100;
                        break;

                    case 2:
                        speakerPitch = refPitch - 1;
                        scoreAfterNormalization = 100;
                        break;
                    case 3:
                        scoreAfterNormalization = 100;
                        speakerPitch = refPitch;
                        break;
                    case 4:
                        scoreAfterNormalization = 100;
                        speakerPitch = refPitch + 1;
                        break;
                    case 5:
                        scoreAfterNormalization = 100;
                        speakerPitch = refPitch + 2;
                        break;
                    default:
                }
            }
        }

        mLocalPitch = speakerPitch;

        final float finalScore = (float) scoreAfterNormalization;
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    performIndicatorAnimationIfNecessary(finalScore);
                }
            });
        } else {
            performIndicatorAnimationIfNecessary(scoreAfterNormalization);
        }

    }

    private long lastCurrentTs = 0;

    private void performIndicatorAnimationIfNecessary(final double scoreAfterNormalization) {
        if (System.currentTimeMillis() - lastCurrentTs >= 80) {
            //int duration = (ScoringView.this.mLocalPitch == 0 && pitch > 0) ? 20 : 80;
            //ObjectAnimator.ofFloat(ScoringView.this, "mLocalPitch", ScoringView.this.mLocalPitch, pitch).setDuration(duration).start();
            lastCurrentTs = System.currentTimeMillis();
            assureAnimationForPitchIndicator(scoreAfterNormalization);
        }
    }

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
    private boolean mPreHighlightStatus = false;

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

    private void resetInternal() {
        mLyricMachine = null;
        mLyricsModel = null;
        mInHighlightStatus = false;
        mPreHighlightStatus = false;
        mPitchHighlightedTime = -1;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
}
