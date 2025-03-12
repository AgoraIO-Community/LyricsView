package io.agora.karaoke_view_ex.internal.utils;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;

/**
 * Helper class for processing and drawing lyrics lines.
 * Handles text layout, positioning, and drawing of lyrics with different styles.
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/7/6
 */
public class LyricsLineDrawerHelper {
    private static final String TAG = Constants.TAG + "-LyricsLineDrawerHelper";

    /**
     * Layout for background (non-highlighted) lyrics text
     */
    private StaticLayout mLayoutBg;

    /**
     * Layout for foreground (highlighted) lyrics text
     */
    private StaticLayout mLayoutFg;

    /**
     * Array of rectangles for controlling drawing progress
     */
    private Rect[] mDrawRects;

    /**
     * Array of rectangles for each word segment in lyrics
     */
    private Rect[] mTextRectTotalWords;

    /**
     * Array of rectangles for each displayed line of lyrics
     */
    private Rect[] mTextRectDisplayLines;

    /**
     * Current lyrics line data model
     */
    private final LyricsLineModel mLine;

    /**
     * Whether line wrapping is enabled for long lyrics
     */
    private final boolean mEnableLineWrap;

    /**
     * Scale factor for adjusting lyrics width ratio
     */
    private float mWidthRatio = 1.0f;

    /**
     * Enumeration for text alignment options
     */
    public enum Gravity {
        /**
         * Center alignment - lyrics text will be centered horizontally
         */
        CENTER(0),

        /**
         * Left alignment - lyrics text will be aligned to the left edge
         */
        LEFT(1),

        /**
         * Right alignment - lyrics text will be aligned to the right edge
         */
        RIGHT(2);

        /**
         * Internal value representing the gravity type
         */
        int value = 0;

        Gravity(int value) {
            this.value = value;
        }

        /**
         * Parse integer value to Gravity enum
         *
         * @param value Integer value representing gravity
         * @return Corresponding Gravity enum value
         */
        public static Gravity parse(int value) {
            if (value == 0) {
                return CENTER;
            } else if (value == 1) {
                return LEFT;
            } else if (value == 2) {
                return RIGHT;
            } else {
                return CENTER;
            }
        }
    }

    /**
     * Constructor for LyricsLineDrawerHelper
     *
     * @param line           Current lyrics line model
     * @param textPaintFg    Paint for foreground (highlighted) text
     * @param textPaintBg    Paint for background text
     * @param width          Available width for drawing
     * @param gravity        Text alignment
     * @param enableLineWrap Whether to enable line wrapping
     * @param widthRatio     Scale factor for width adjustment
     */
    public LyricsLineDrawerHelper(LyricsLineModel line, @Nullable TextPaint textPaintFg, @NonNull TextPaint textPaintBg,
                                  int width, Gravity gravity, boolean enableLineWrap, float widthRatio) {
        this.mLine = line;
        this.mEnableLineWrap = enableLineWrap;
        this.mWidthRatio = widthRatio > 0 ? widthRatio : 1.0f;
        this.init(textPaintFg, textPaintBg, width, gravity);
    }

    /**
     * Initialize layouts and text measurements
     *
     * @param textPaintFg Paint for foreground text
     * @param textPaintBg Paint for background text
     * @param width       Available width
     * @param gravity     Text alignment
     */
    private void init(@Nullable TextPaint textPaintFg, @NonNull TextPaint textPaintBg, int width, Gravity gravity) {
        Layout.Alignment align;
        switch (gravity) {
            case LEFT:
                align = Layout.Alignment.ALIGN_NORMAL;
                break;
            case CENTER:
                align = Layout.Alignment.ALIGN_CENTER;
                break;
            case RIGHT:
                align = Layout.Alignment.ALIGN_OPPOSITE;
                break;
            default:
                align = Layout.Alignment.ALIGN_CENTER;
                break;
        }

        StringBuilder sb = new StringBuilder();
        List<LyricsLineModel.Tone> tones = mLine.tones;
        mTextRectTotalWords = new Rect[tones.size()];
        String text;
        for (int i = 0; i < tones.size(); i++) {
            LyricsLineModel.Tone tone = tones.get(i);
            Rect rectTotal = new Rect();
            mTextRectTotalWords[i] = rectTotal;
            String s = tone.word;
            // Sometimes, lyrics/sentence contains no word-tag
            if (s == null) {
                s = "";
            }
            if (tone.lang == LyricsLineModel.Lang.English) {
                s = s + " ";
            }
            sb.append(s);

            text = sb.toString();
            textPaintBg.getTextBounds(text, 0, text.length(), rectTotal);
        }

        text = sb.toString();

        // Fix: Correctly handle line wrapping logic
        // When mEnableLineWrap is false, use a large enough width to ensure no wrapping
        int layoutWidth = mEnableLineWrap ? width : Integer.MAX_VALUE / 2;

        if (textPaintFg != null) {
            mLayoutFg = new StaticLayout(text, textPaintFg, layoutWidth, align, 1f, 0f, false);
        }
        mLayoutBg = new StaticLayout(text, textPaintBg, layoutWidth, align, 1f, 0f, false);

        // If line wrapping is not allowed, but the text width exceeds the widget width, adjust the layout width
        if (!mEnableLineWrap) {
            int textWidth = (int) textPaintBg.measureText(text);
            // Use the actual measured text width to ensure it won't be forced to wrap due to insufficient width
            layoutWidth = Math.max(width, textWidth);

            if (textPaintFg != null) {
                mLayoutFg = new StaticLayout(text, textPaintFg, layoutWidth, align, 1f, 0f, false);
            }
            mLayoutBg = new StaticLayout(text, textPaintBg, layoutWidth, align, 1f, 0f, false);
        }

        int totalLine = mLayoutBg.getLineCount();
        mTextRectDisplayLines = new Rect[totalLine];
        mDrawRects = new Rect[totalLine];
        for (int i = 0; i < totalLine; i++) {
            Rect mRect = new Rect();
            mLayoutBg.getLineBounds(i, mRect);
            mRect.left = (int) mLayoutBg.getLineLeft(i);
            mRect.right = (int) mLayoutBg.getLineRight(i);

            mTextRectDisplayLines[i] = mRect;
            mDrawRects[i] = new Rect(mRect);
        }
    }

    /**
     * Get total height of lyrics layout
     *
     * @return Height in pixels
     */
    public int getHeight() {
        if (mLayoutBg == null) {
            return 0;
        }
        return mLayoutBg.getHeight();
    }

    /**
     * Get total width of lyrics layout
     *
     * @return Width in pixels
     */
    public int getWidth() {
        return mLayoutBg.getWidth();
    }

    /**
     * Draw background lyrics text
     *
     * @param canvas Canvas to draw on
     */
    public void draw(Canvas canvas) {
        mLayoutBg.draw(canvas);
    }

    /**
     * Draw foreground (highlighted) lyrics text
     *
     * @param canvas Canvas to draw on
     */
    public void drawFg(Canvas canvas) {
        mLayoutFg.draw(canvas);
    }

    /**
     * Calculate drawing rectangles based on current time
     *
     * @param time Current timestamp in milliseconds
     * @return Array of rectangles for drawing
     */
    public Rect[] getDrawRectByTime(long time) {
        int doneLen = 0;
        float curLen = 0f;

        List<LyricsLineModel.Tone> tones = mLine.tones;
        for (int i = 0; i < tones.size(); i++) {
            LyricsLineModel.Tone tone = tones.get(i);
            if (time >= tone.end) {
                if (i == tones.size() - 1) {
                    // Last syllable, highlight all
                    doneLen = Integer.MAX_VALUE;
                } else {
                    doneLen = mTextRectTotalWords[i].width();
                }
            } else {
                int wordLen = 0;
                if (i == 0) {
                    wordLen = mTextRectTotalWords[i].width();
                } else {
                    wordLen = mTextRectTotalWords[i].width() - mTextRectTotalWords[i - 1].width();
                }

                if (tone.isFullLine) {
                    //+2 fix the bug that the last word is not displayed for chinese lyric
                    curLen = wordLen + 2;
                } else {
                    float percent = (time - tone.begin) / (float) (tone.end - tone.begin);

                    // Apply scale factor to progress calculation
                    curLen = wordLen * (percent > 0 ? percent : 0) * mWidthRatio;
                }
                break;
            }
        }

        // Apply scale factor to completed length
        int showLen = (int) ((doneLen != Integer.MAX_VALUE ? doneLen * mWidthRatio : doneLen) + curLen);

        // Handle highlighting in multi-line situations
        for (int i = 0; i < mLayoutFg.getLineCount(); i++) {
            if (i >= mTextRectDisplayLines.length) {
                break;
            }

            mDrawRects[i].left = mTextRectDisplayLines[i].left;

            if (doneLen == Integer.MAX_VALUE) {
                // If it's the last syllable and it's finished, highlight the entire line
                mDrawRects[i].right = mTextRectDisplayLines[i].right;
                continue;
            }

            int curLineWidth = mTextRectDisplayLines[i].width();

            if (showLen <= 0) {
                // Current line doesn't need highlighting
                mDrawRects[i].right = mDrawRects[i].left;
            } else if (curLineWidth <= showLen) {
                // Highlight the entire current line
                mDrawRects[i].right = mTextRectDisplayLines[i].right;
                showLen -= curLineWidth;
            } else {
                // Partially highlight the current line
                mDrawRects[i].right = mDrawRects[i].left + showLen;
                showLen = 0;
            }
        }

        return mDrawRects;
    }

    /**
     * Get number of lines in lyrics layout
     *
     * @return Number of lines
     */
    public int getLineCount() {
        if (mLayoutBg == null) {
            return 0;
        }
        return mLayoutBg.getLineCount();
    }
}
