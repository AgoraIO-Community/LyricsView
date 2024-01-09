package io.agora.examples.karaoke_view;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import io.agora.examples.karaoke_view.databinding.ActivitySettingsBinding;
import io.agora.karaoke_view.v11.downloader.LyricsFileDownloader;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadPreferences();

        binding.settingsDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        loadPreferencesScoringAlgo(prefs, editor);

        loadPreferencesLyricsUI(prefs, editor);

        loadPreferencesScoringUI(prefs, editor);

        loadPreferencesDownloaderUI(prefs, editor);

        loadPreferencesOtherSettingsUI(prefs, editor);
    }

    private void loadPreferencesScoringAlgo(SharedPreferences prefs, SharedPreferences.Editor editor) {
        binding.scoringLevelTune.setProgress(prefs.getInt(getString(R.string.prefs_key_scoring_level), 15)); // 0...100
        binding.scoringLevelTuneValue.setText(String.valueOf(prefs.getInt(getString(R.string.prefs_key_scoring_level), 15)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.scoringLevelTune.setMin(0);
        }
        binding.scoringLevelTune.setMax(100);
        binding.scoringLevelTune.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    editor.putInt(getString(R.string.prefs_key_scoring_level), progress);
                    editor.apply();
                    binding.scoringLevelTuneValue.setText(String.valueOf(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        binding.compensationOffsetTune.setProgress(prefs.getInt(getString(R.string.prefs_key_scoring_compensation_offset), 0)); // -100...100
        binding.compensationOffsetTuneValue.setText(String.valueOf(prefs.getInt(getString(R.string.prefs_key_scoring_compensation_offset), 0)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.compensationOffsetTune.setMin(-100);
        }
        binding.compensationOffsetTune.setMax(100);

        binding.compensationOffsetTune.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    editor.putInt(getString(R.string.prefs_key_scoring_compensation_offset), progress);
                    editor.apply();
                    binding.compensationOffsetTuneValue.setText(String.valueOf(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void loadPreferencesLyricsUI(SharedPreferences prefs, SharedPreferences.Editor editor) {
        boolean indicatorSwitch = prefs.getBoolean(getString(R.string.prefs_key_start_of_verse_indicator_switch), true);
        binding.lyricsStartOfVerseIndicatorSwitch.setChecked(indicatorSwitch);
        binding.lyricsStartOfVerseIndicatorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean(getString(R.string.prefs_key_start_of_verse_indicator_switch), isChecked);
            editor.apply();
        });

        String[] availableColorsOfIndicator = getResources().getStringArray(R.array.available_indicator_colors);
        String indicatorColor = prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_color), "Default");
        for (int idx = 0; idx < availableColorsOfIndicator.length; idx++) {
            if (indicatorColor.equals(availableColorsOfIndicator[idx])) {
                binding.startOfVerseIndicatorColorSelector.setSelection(idx, false);
                break;
            }
        }
        binding.startOfVerseIndicatorColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_start_of_verse_indicator_color), availableColorsOfIndicator[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableRadiusOfIndicator = getResources().getStringArray(R.array.available_indicator_radius);
        String indicatorRadius = prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_radius), "6dp");
        for (int idx = 0; idx < availableRadiusOfIndicator.length; idx++) {
            if (indicatorRadius.equals(availableRadiusOfIndicator[idx])) {
                binding.startOfVerseIndicatorRadiusSelector.setSelection(idx, false);
                break;
            }
        }
        binding.startOfVerseIndicatorRadiusSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_start_of_verse_indicator_radius), availableRadiusOfIndicator[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        binding.marginTopOfIndicatorTune.setProgress(prefs.getInt(getString(R.string.prefs_key_start_of_verse_indicator_padding_top), 6)); // 2...20
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.marginTopOfIndicatorTune.setMin(2);
        }
        binding.marginTopOfIndicatorTune.setMax(20);
        binding.marginTopOfIndicatorTune.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    editor.putInt(getString(R.string.prefs_key_start_of_verse_indicator_padding_top), progress);
                    editor.apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        String[] availableColorsOfNormalLine = getResources().getStringArray(R.array.available_text_colors);
        String defaultLineColor = prefs.getString(getString(R.string.prefs_key_normal_line_text_color), "Default");
        for (int idx = 0; idx < availableColorsOfNormalLine.length; idx++) {
            if (defaultLineColor.equals(availableColorsOfNormalLine[idx])) {
                binding.normalLineColorSelector.setSelection(idx, false);
                break;
            }
        }
        binding.normalLineColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_normal_line_text_color), availableColorsOfNormalLine[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableSizeOfNormalLineText = getResources().getStringArray(R.array.available_text_size);
        int sizeOfNormalLineText = prefs.getInt(getString(R.string.prefs_key_normal_line_text_size), 12);
        for (int idx = 0; idx < availableSizeOfNormalLineText.length; idx++) {
            if (sizeOfNormalLineText == Integer.parseInt(availableSizeOfNormalLineText[idx])) {
                binding.sizeOfNormalTextSelector.setSelection(idx, false);
                break;
            }
        }
        binding.sizeOfNormalTextSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putInt(getString(R.string.prefs_key_normal_line_text_size), Integer.parseInt(availableSizeOfNormalLineText[position]));
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableColorsOfLineHighlighted = getResources().getStringArray(R.array.available_highlighted_text_colors);
        String highlightedLineColor = prefs.getString(getString(R.string.prefs_key_current_line_highlighted_text_color), "Default");
        for (int idx = 0; idx < availableColorsOfLineHighlighted.length; idx++) {
            if (highlightedLineColor.equals(availableColorsOfLineHighlighted[idx])) {
                binding.lineHighlightedColorSelector.setSelection(idx, false);
                break;
            }
        }
        binding.lineHighlightedColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_current_line_highlighted_text_color), availableColorsOfLineHighlighted[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableColorsOfCurrentLine = getResources().getStringArray(R.array.available_text_colors);
        String currentLineColor = prefs.getString(getString(R.string.prefs_key_current_line_text_color), "White");
        for (int idx = 0; idx < availableColorsOfCurrentLine.length; idx++) {
            if (currentLineColor.equals(availableColorsOfCurrentLine[idx])) {
                binding.currentLineColorSelector.setSelection(idx, false);
                break;
            }
        }
        binding.currentLineColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_current_line_text_color), availableColorsOfCurrentLine[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableSizeOfCurrentLineText = getResources().getStringArray(R.array.available_text_size);
        int sizeOfCurrentLineText = prefs.getInt(getString(R.string.prefs_key_current_line_text_size), 16);
        for (int idx = 0; idx < availableSizeOfCurrentLineText.length; idx++) {
            if (sizeOfCurrentLineText == Integer.parseInt(availableSizeOfCurrentLineText[idx])) {
                binding.sizeOfCurrentTextSelector.setSelection(idx, false);
                break;
            }
        }
        binding.sizeOfCurrentTextSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putInt(getString(R.string.prefs_key_current_line_text_size), Integer.parseInt(availableSizeOfCurrentLineText[position]));
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableSpacingBetweenLines = getResources().getStringArray(R.array.available_spacing_lines);
        String spacingBetweenLines = prefs.getString(getString(R.string.prefs_key_line_spacing), "6dp");
        for (int idx = 0; idx < availableSpacingBetweenLines.length; idx++) {
            if (spacingBetweenLines.equals(availableSpacingBetweenLines[idx])) {
                binding.spacingBetweenLinesSelector.setSelection(idx, false);
                break;
            }
        }
        binding.spacingBetweenLinesSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_line_spacing), availableSpacingBetweenLines[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        boolean lyricsDraggingSwitch = prefs.getBoolean(getString(R.string.prefs_key_lyrics_dragging_switch), true);
        binding.draggingSwitch.setChecked(lyricsDraggingSwitch);
        binding.draggingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean(getString(R.string.prefs_key_lyrics_dragging_switch), isChecked);
            editor.apply();
        });

        String[] availableTipsWhenNoLyricsAvailable = getResources().getStringArray(R.array.text_when_no_lyrics_available);
        String textWhenNoLyricsAvailable = prefs.getString(getString(R.string.prefs_key_lyrics_not_available_text), "");
        for (int idx = 0; idx < availableTipsWhenNoLyricsAvailable.length; idx++) {
            if (textWhenNoLyricsAvailable.equals(availableTipsWhenNoLyricsAvailable[idx])) {
                binding.lyricsNotAvailableSelector.setSelection(idx, false);
                break;
            }
        }
        binding.lyricsNotAvailableSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_lyrics_not_available_text), availableTipsWhenNoLyricsAvailable[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String tipColorWhenNoLyricsAvailable = prefs.getString(getString(R.string.prefs_key_lyrics_not_available_text_color), "Default");
        String[] availableColorsOfTipsWhenNoLyricsAvailable = getResources().getStringArray(R.array.available_text_colors);
        for (int idx = 0; idx < availableColorsOfTipsWhenNoLyricsAvailable.length; idx++) {
            if (tipColorWhenNoLyricsAvailable.equals(availableColorsOfTipsWhenNoLyricsAvailable[idx])) {
                binding.lyricsNotAvailableColorSelector.setSelection(idx, false);
                break;
            }
        }
        binding.lyricsNotAvailableColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_lyrics_not_available_text_color), availableColorsOfTipsWhenNoLyricsAvailable[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableSizeOfTips = getResources().getStringArray(R.array.available_text_size);
        int sizeOfDefaultLineTextWhenNoLyricsAvailable = prefs.getInt(getString(R.string.prefs_key_lyrics_not_available_text_size), 26);
        for (int idx = 0; idx < availableSizeOfTips.length; idx++) {
            if (sizeOfDefaultLineTextWhenNoLyricsAvailable == Integer.parseInt(availableSizeOfTips[idx])) {
                binding.lyricsNotAvailableSizeSelector.setSelection(idx, false);
                break;
            }
        }
        binding.lyricsNotAvailableSizeSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putInt(getString(R.string.prefs_key_lyrics_not_available_text_size), Integer.parseInt(availableSizeOfTips[position]));
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void loadPreferencesScoringUI(SharedPreferences prefs, SharedPreferences.Editor editor) {
        String[] availableHeightOfRefPitchStick = getResources().getStringArray(R.array.available_spacing_lines);
        String refPitchStickHeight = prefs.getString(getString(R.string.prefs_key_ref_pitch_stick_height), "6dp");
        for (int idx = 0; idx < availableHeightOfRefPitchStick.length; idx++) {
            if (refPitchStickHeight.equals(availableHeightOfRefPitchStick[idx])) {
                binding.refPitchStickHeightSelector.setSelection(idx, false);
                break;
            }
        }
        binding.refPitchStickHeightSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_ref_pitch_stick_height), availableHeightOfRefPitchStick[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableColorsOfDefaultRefPitchStick = getResources().getStringArray(R.array.available_text_colors);
        String defaultRefPitchStickColor = prefs.getString(getString(R.string.prefs_key_default_ref_pitch_stick_color), "Default");
        for (int idx = 0; idx < availableColorsOfDefaultRefPitchStick.length; idx++) {
            if (defaultRefPitchStickColor.equals(availableColorsOfDefaultRefPitchStick[idx])) {
                binding.colorOfDefaultRefPitchStickSelector.setSelection(idx, false);
                break;
            }
        }
        binding.colorOfDefaultRefPitchStickSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_default_ref_pitch_stick_color), availableColorsOfDefaultRefPitchStick[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableColorsOfHighlightedRefPitchStick = getResources().getStringArray(R.array.available_highlighted_text_colors);
        String highlightedRefPitchStickColor = prefs.getString(getString(R.string.prefs_key_highlighted_ref_pitch_stick_color), "Default");
        for (int idx = 0; idx < availableColorsOfHighlightedRefPitchStick.length; idx++) {
            if (highlightedRefPitchStickColor.equals(availableColorsOfHighlightedRefPitchStick[idx])) {
                binding.colorOfHighlightedRefPitchStickSelector.setSelection(idx, false);
                break;
            }
        }
        binding.colorOfHighlightedRefPitchStickSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_highlighted_ref_pitch_stick_color), availableColorsOfHighlightedRefPitchStick[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        boolean particleEffectSwitch = prefs.getBoolean(getString(R.string.prefs_key_particle_effect_switch), true);
        binding.particleEffectSwitch.setChecked(particleEffectSwitch);
        binding.particleEffectSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean(getString(R.string.prefs_key_particle_effect_switch), isChecked);
            editor.apply();
        });

        boolean customizedIndicatorAndParticleSwitch = prefs.getBoolean(getString(R.string.prefs_key_customized_indicator_and_particle_switch), false);
        binding.customizedIndicatorAndParticleSwitch.setChecked(customizedIndicatorAndParticleSwitch);
        binding.customizedIndicatorAndParticleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean(getString(R.string.prefs_key_customized_indicator_and_particle_switch), isChecked);
            editor.apply();
        });

        int particleHitThreshold = (int) (prefs.getFloat(getString(R.string.prefs_key_particle_hit_on_threshold), 0.8f) * 100);
        binding.particleHitOnThresholdTune.setProgress(particleHitThreshold); // 0...100
        binding.particleHitOnThresholdTuneValue.setText(String.valueOf(prefs.getFloat(getString(R.string.prefs_key_particle_hit_on_threshold), 0.8f)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.particleHitOnThresholdTune.setMin(1);
        }
        binding.particleHitOnThresholdTune.setMax(100);
        binding.particleHitOnThresholdTune.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float threshold = (float) progress / 100.f;
                    editor.putFloat(getString(R.string.prefs_key_particle_hit_on_threshold), threshold);
                    editor.apply();
                    binding.particleHitOnThresholdTuneValue.setText(String.valueOf(threshold));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void loadPreferencesDownloaderUI(SharedPreferences prefs, SharedPreferences.Editor editor) {
        binding.downloaderCleanAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LyricsFileDownloader.getInstance(getApplicationContext()).cleanAll();
            }
        });
    }

    private void loadPreferencesOtherSettingsUI(SharedPreferences prefs, SharedPreferences.Editor editor) {
        binding.rtcAudioDumpSwitch.setChecked(prefs.getBoolean(getString(R.string.prefs_key_rtc_audio_dump), false));
        binding.rtcAudioDumpSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean(getString(R.string.prefs_key_rtc_audio_dump), isChecked);
            editor.apply();
        });

        binding.rtcAudioDumpSwitch.setChecked(prefs.getBoolean(getString(R.string.prefs_key_rtc_audio_dump), false));
        binding.rtcAudioDumpSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean(getString(R.string.prefs_key_rtc_audio_dump), isChecked);
            editor.apply();
        });
    }
}