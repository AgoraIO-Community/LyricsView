package io.agora.examples.karaoke_view;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
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
    }

    private void loadPreferences() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
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
}