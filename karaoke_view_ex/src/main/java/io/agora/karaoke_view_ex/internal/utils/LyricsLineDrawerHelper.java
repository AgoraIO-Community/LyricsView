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
 * Process each line of lyrics
 *
 * @author chenhengfei(Aslanchen)
 * @date 2021/7/6
 */
public class LyricsLineDrawerHelper {
    private static final String TAG = Constants.TAG + "-LyricsLineDrawerHelper";

    private StaticLayout mLayoutBG; // Background text
    private StaticLayout mLayoutFG; // Foreground highlighted text

    private Rect[] drawRects; // Control progress

    private Rect[] textRectTotalWords; // Each segment of lyrics
    private Rect[] textRectDisplayLines; // Each line of displayed lyrics

    private LyricsLineModel mLine; // Data source
    private boolean mEnableLineWrap; // Whether to enable line wrapping

    // 比例因子，用于调整歌词进度与视图宽度的关系
    // Scale factor used to adjust the relationship between lyrics progress and view width
    private float mWidthRatio = 1.0f;

    public enum Gravity {
        CENTER(0), LEFT(1), RIGHT(2);

        int value = 0;

        Gravity(int value) {
            this.value = value;
        }

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

    public LyricsLineDrawerHelper(LyricsLineModel line, @Nullable TextPaint textPaintFG, @NonNull TextPaint textPaintBG, int width, Gravity gravity, boolean enableLineWrap, float widthRatio) {
        this.mLine = line;
        this.mEnableLineWrap = enableLineWrap;
        this.mWidthRatio = widthRatio > 0 ? widthRatio : 1.0f;
        this.init(textPaintFG, textPaintBG, width, gravity);
    }

    private void init(@Nullable TextPaint textPaintFG, @NonNull TextPaint textPaintBG, int width, Gravity gravity) {
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
        textRectTotalWords = new Rect[tones.size()];
        String text;
        for (int i = 0; i < tones.size(); i++) {
            LyricsLineModel.Tone tone = tones.get(i);
            Rect rectTotal = new Rect();
            textRectTotalWords[i] = rectTotal;
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
            textPaintBG.getTextBounds(text, 0, text.length(), rectTotal);
        }

        text = sb.toString();

        // Fix: Correctly handle line wrapping logic
        // When mEnableLineWrap is false, use a large enough width to ensure no wrapping
        int layoutWidth = mEnableLineWrap ? width : Integer.MAX_VALUE / 2;

        if (textPaintFG != null) {
            mLayoutFG = new StaticLayout(text, textPaintFG, layoutWidth, align, 1f, 0f, false);
        }
        mLayoutBG = new StaticLayout(text, textPaintBG, layoutWidth, align, 1f, 0f, false);

        // If line wrapping is not allowed, but the text width exceeds the widget width, adjust the layout width
        if (!mEnableLineWrap) {
            int textWidth = (int) textPaintBG.measureText(text);
            // Use the actual measured text width to ensure it won't be forced to wrap due to insufficient width
            layoutWidth = Math.max(width, textWidth);

            if (textPaintFG != null) {
                mLayoutFG = new StaticLayout(text, textPaintFG, layoutWidth, align, 1f, 0f, false);
            }
            mLayoutBG = new StaticLayout(text, textPaintBG, layoutWidth, align, 1f, 0f, false);
        }

        int totalLine = mLayoutBG.getLineCount();
        textRectDisplayLines = new Rect[totalLine];
        drawRects = new Rect[totalLine];
        for (int i = 0; i < totalLine; i++) {
            Rect mRect = new Rect();
            mLayoutBG.getLineBounds(i, mRect);
            mRect.left = (int) mLayoutBG.getLineLeft(i);
            mRect.right = (int) mLayoutBG.getLineRight(i);

            textRectDisplayLines[i] = mRect;
            drawRects[i] = new Rect(mRect);
        }
    }

    public int getHeight() {
        if (mLayoutBG == null) {
            return 0;
        }
        return mLayoutBG.getHeight();
    }

    public int getWidth() {
        return mLayoutBG.getWidth();
    }

    public void draw(Canvas canvas) {
        mLayoutBG.draw(canvas);
    }

    public void drawFG(Canvas canvas) {
        mLayoutFG.draw(canvas);
    }

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
                    doneLen = textRectTotalWords[i].width();
                }
            } else {
                int wordLen = 0;
                if (i == 0) {
                    wordLen = textRectTotalWords[i].width();
                } else {
                    wordLen = textRectTotalWords[i].width() - textRectTotalWords[i - 1].width();
                }

                if (tone.isFullLine) {
                    //+2 fix the bug that the last word is not displayed for chinese lyric
                    curLen = wordLen + 2;
                } else {
                    float percent = (time - tone.begin) / (float) (tone.end - tone.begin);
                    // 应用比例因子到进度计算中
                    // Apply scale factor to progress calculation
                    curLen = wordLen * (percent > 0 ? percent : 0) * mWidthRatio;
                }
                break;
            }
        }

        // 应用比例因子到已完成长度
        // Apply scale factor to completed length
        int showLen = (int) ((doneLen != Integer.MAX_VALUE ? doneLen * mWidthRatio : doneLen) + curLen);

        // Handle highlighting in multi-line situations
        for (int i = 0; i < mLayoutFG.getLineCount(); i++) {
            if (i >= textRectDisplayLines.length) {
                break;
            }

            drawRects[i].left = textRectDisplayLines[i].left;

            if (doneLen == Integer.MAX_VALUE) {
                // If it's the last syllable and it's finished, highlight the entire line
                drawRects[i].right = textRectDisplayLines[i].right;
                continue;
            }

            int curLineWidth = textRectDisplayLines[i].width();

            if (showLen <= 0) {
                // Current line doesn't need highlighting
                drawRects[i].right = drawRects[i].left;
            } else if (curLineWidth <= showLen) {
                // Highlight the entire current line
                drawRects[i].right = textRectDisplayLines[i].right;
                showLen -= curLineWidth;
            } else {
                // Partially highlight the current line
                drawRects[i].right = drawRects[i].left + showLen;
                showLen = 0;
            }
        }

        return drawRects;
    }

    /**
     * Get the number of lyrics lines
     *
     * @return Number of lyrics lines
     */
    public int getLineCount() {
        if (mLayoutBG == null) {
            return 0;
        }
        return mLayoutBG.getLineCount();
    }
}
