package io.agora.examples.karaoke_view_ex;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import io.agora.examples.utils.Utils;
import io.agora.karaoke_view_ex.LyricsView;
import io.agora.karaoke_view_ex.ScoringView;

/** Shared lyric and pitch appearance for both music libraries. */
public final class KaraokeViewStyle {
    private KaraokeViewStyle() {}

    public static void apply(Context context, LyricsView lyrics, ScoringView scoring) {
        SharedPreferences prefs = context.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE);
        boolean indicatorOn = prefs.getBoolean(context.getString(R.string.prefs_key_start_of_verse_indicator_switch), true);
        lyrics.enablePreludeEndPositionIndicator(indicatorOn);
        String indicatorColor = prefs.getString(context.getString(R.string.prefs_key_start_of_verse_indicator_color), "Default");
        lyrics.setPreludeEndPositionIndicatorColor(Utils.colorInStringToDex(indicatorColor));
        String indicatorRadius = prefs.getString(context.getString(R.string.prefs_key_start_of_verse_indicator_radius), "6dp");
        lyrics.setPreludeEndPositionIndicatorRadius(Utils.dp2pix(context.getApplicationContext(), Float.parseFloat(indicatorRadius.replace("dp", ""))));
        int indicatorPaddingTop = prefs.getInt(context.getString(R.string.prefs_key_start_of_verse_indicator_padding_top), 6);
        lyrics.setPreludeEndPositionIndicatorPaddingTop(Utils.dp2pix(context.getApplicationContext(), indicatorPaddingTop));

        String defaultTextColor = prefs.getString(context.getString(R.string.prefs_key_normal_line_text_color), "Default");
        lyrics.setPreviousLineTextColor(Utils.colorInStringToDex(defaultTextColor));
        lyrics.setUpcomingLineTextColor(Utils.colorInStringToDex(defaultTextColor));

        int defaultTextSize = prefs.getInt(context.getString(R.string.prefs_key_normal_line_text_size), 13);
        lyrics.setTextSize(Utils.sp2pix(context.getApplicationContext(), defaultTextSize));

        String currentTextColor = prefs.getString(context.getString(R.string.prefs_key_current_line_text_color), "Yellow");
        lyrics.setCurrentLineTextColor(Utils.colorInStringToDex(currentTextColor));

        String highlightedTextColor = prefs.getString(context.getString(R.string.prefs_key_current_line_highlighted_text_color), "Red");
        lyrics.setCurrentLineHighlightedTextColor(Utils.colorInStringToDex(highlightedTextColor));

        int currentTextSize = prefs.getInt(context.getString(R.string.prefs_key_current_line_text_size), 24);
        lyrics.setCurrentLineTextSize(Utils.sp2pix(context.getApplicationContext(), currentTextSize));

        String lineSpacing = prefs.getString(context.getString(R.string.prefs_key_line_spacing), "6dp");
        lyrics.setLineSpacing(Utils.dp2pix(context.getApplicationContext(), Float.parseFloat(lineSpacing.replace("dp", ""))));

        boolean lyricsLineWrapOn = prefs.getBoolean(context.getString(R.string.prefs_key_lyrics_line_wrap_switch), false);
        lyrics.enableLineWrap(lyricsLineWrapOn);

        String labelWhenNoLyrics = prefs.getString(context.getString(R.string.prefs_key_lyrics_not_available_text), context.getString(R.string.no_lyrics_label));
        lyrics.setLabelShownWhenNoLyrics(labelWhenNoLyrics);

        String labelWhenNoLyricsTextColor = prefs.getString(context.getString(R.string.prefs_key_lyrics_not_available_text_color), "Red");
        lyrics.setLabelShownWhenNoLyricsTextColor(Utils.colorInStringToDex(labelWhenNoLyricsTextColor));

        int labelWhenNoLyricsTextSize = prefs.getInt(context.getString(R.string.prefs_key_lyrics_not_available_text_size), 26);
        lyrics.setLabelShownWhenNoLyricsTextSize(Utils.sp2pix(context.getApplicationContext(), labelWhenNoLyricsTextSize));

        String heightOfRefPitch = prefs.getString(context.getString(R.string.prefs_key_ref_pitch_stick_height), "6dp");
        scoring.setRefPitchStickHeight(Utils.dp2pix(context.getApplicationContext(), Float.parseFloat(heightOfRefPitch.replace("dp", ""))));

        String defaultRefPitchStickColor = prefs.getString(context.getString(R.string.prefs_key_default_ref_pitch_stick_color), "Default");
        scoring.setRefPitchStickDefaultColor(Utils.colorInStringToDex(defaultRefPitchStickColor));

        String highlightedRefPitchStickColor = prefs.getString(context.getString(R.string.prefs_key_highlighted_ref_pitch_stick_color), "Default");
        scoring.setRefPitchStickHighlightedColor(Utils.colorInStringToDex(highlightedRefPitchStickColor));

        Drawable[] drawables = null;
        boolean customizedIndicatorAndParticleOn = prefs.getBoolean(context.getString(R.string.prefs_key_customized_indicator_and_particle_switch), false);
        if (customizedIndicatorAndParticleOn) {
            Bitmap bitmap = Utils.drawableToBitmap(context.getDrawable(R.drawable.pitch_indicator));
            scoring.setLocalPitchIndicator(bitmap);
            drawables = new Drawable[]{context.getDrawable(R.drawable.pitch_indicator), context.getDrawable(R.drawable.pitch_indicator_yellow), context.getDrawable(R.drawable.ic_launcher_background), context.getDrawable(R.drawable.star7), context.getDrawable(R.drawable.star8)};
        } else {
            scoring.setLocalPitchIndicator(null);
        }

        boolean particleEffectOn = prefs.getBoolean(context.getString(R.string.prefs_key_particle_effect_switch), true);
        scoring.enableParticleEffect(particleEffectOn, drawables);


        float particleHitOnThreshold = prefs.getFloat(context.getString(R.string.prefs_key_hit_score_threshold), 0.8f);
        scoring.setThresholdOfHitScore(particleHitOnThreshold);
    }
}
