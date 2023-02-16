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
    }

    private void loadPreferencesScoringAlgo(SharedPreferences prefs, SharedPreferences.Editor editor) {
        binding.scoringLevelTune.setProgress(prefs.getInt(getString(R.string.prefs_key_scoring_level), 10)); // 0...100
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.scoringLevelTune.setMin(-100);
        }
        binding.scoringLevelTune.setMax(100);

        binding.compensationOffsetTune.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    editor.putInt(getString(R.string.prefs_key_scoring_compensation_offset), progress);
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

        String[] availableColorsOfDefaultLine = getResources().getStringArray(R.array.available_default_line_text_colors);
        String defaultLineColor = prefs.getString(getString(R.string.prefs_key_default_line_text_color), "White");
        for (int idx = 0; idx < availableColorsOfDefaultLine.length; idx++) {
            if (defaultLineColor.equals(availableColorsOfDefaultLine[idx])) {
                binding.defaultLineColorSelector.setSelection(idx, false);
                break;
            }
        }
        binding.defaultLineColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_default_line_text_color), availableColorsOfDefaultLine[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableSizeOfDefaultLineText = getResources().getStringArray(R.array.available_text_size);
        int sizeOfDefaultLineText = prefs.getInt(getString(R.string.prefs_key_default_line_text_size), 12);
        for (int idx = 0; idx < availableSizeOfDefaultLineText.length; idx++) {
            if (sizeOfDefaultLineText == Integer.parseInt(availableSizeOfDefaultLineText[idx])) {
                binding.sizeOfDefaultTextSelector.setSelection(idx, false);
                break;
            }
        }
        binding.sizeOfDefaultTextSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putInt(getString(R.string.prefs_key_default_line_text_size), Integer.parseInt(availableSizeOfDefaultLineText[position]));
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableColorsOfHighlightedLine = getResources().getStringArray(R.array.available_highlighted_line_text_colors);
        String highlightedLineColor = prefs.getString(getString(R.string.prefs_key_highlighted_line_text_color), "Default");
        for (int idx = 0; idx < availableColorsOfHighlightedLine.length; idx++) {
            if (highlightedLineColor.equals(availableColorsOfHighlightedLine[idx])) {
                binding.highlightedLineColorSelector.setSelection(idx, false);
                break;
            }
        }
        binding.highlightedLineColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putString(getString(R.string.prefs_key_highlighted_line_text_color), availableColorsOfHighlightedLine[position]);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        String[] availableSizeOfHighlightedLineText = getResources().getStringArray(R.array.available_text_size);
        int sizeOfHighlightedLineText = prefs.getInt(getString(R.string.prefs_key_highlighted_line_text_size), 16);
        for (int idx = 0; idx < availableSizeOfHighlightedLineText.length; idx++) {
            if (sizeOfHighlightedLineText == Integer.parseInt(availableSizeOfHighlightedLineText[idx])) {
                binding.sizeOfHighlightedTextSelector.setSelection(idx, false);
                break;
            }
        }
        binding.sizeOfHighlightedTextSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editor.putInt(getString(R.string.prefs_key_highlighted_line_text_size), Integer.parseInt(availableSizeOfHighlightedLineText[position]));
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
        String[] availableColorsOfTipsWhenNoLyricsAvailable = getResources().getStringArray(R.array.available_default_line_text_colors);
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
        int sizeOfDefaultLineTextWhenNoLyricsAvailable = prefs.getInt(getString(R.string.prefs_key_lyrics_not_available_text_size), 18);
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
        String[] availableColorsOfDefaultRefPitchStick = getResources().getStringArray(R.array.available_default_line_text_colors);
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

        String[] availableColorsOfHighlightedRefPitchStick = getResources().getStringArray(R.array.available_highlighted_line_text_colors);
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
    }
}