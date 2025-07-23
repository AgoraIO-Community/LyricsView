package io.agora.examples.karaoke_view_ex

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.agora.examples.karaoke_view_ex.MusicManager.clearCache
import io.agora.examples.karaoke_view_ex.databinding.ActivitySettingsBinding
import io.agora.examples.karaoke_view_ex.utils.ExampleConstants
import io.agora.karaoke_view_ex.internal.constants.LyricType

class SettingsActivity : AppCompatActivity() {
    private var binding: ActivitySettingsBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(
            layoutInflater
        )
        setContentView(binding?.root)

        loadPreferences()

        binding?.settingsDone?.setOnClickListener(View.OnClickListener {
            setResult(RESULT_OK)
            finish()
        })
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("karaoke_sample_app", MODE_PRIVATE)
        val editor = prefs.edit()

        loadPreferencesKaraoke(prefs, editor)

        loadPreferencesLyricsUI(prefs, editor)

        loadPreferencesScoringUI(prefs, editor)

        loadPreferencesOtherSettingsUI(prefs, editor)
    }

    private fun loadPreferencesKaraoke(prefs: SharedPreferences, editor: SharedPreferences.Editor) {
        binding?.scoringLevelTune?.progress =
            prefs.getInt(getString(R.string.prefs_key_scoring_level), 15) // 0...100
        binding?.scoringLevelTuneValue?.text =
            prefs.getInt(getString(R.string.prefs_key_scoring_level), 15).toString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding?.scoringLevelTune?.min = 0
        }
        binding?.scoringLevelTune?.max = 100
        binding?.scoringLevelTune?.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    editor.putInt(getString(R.string.prefs_key_scoring_level), progress)
                    editor.apply()
                    binding?.scoringLevelTuneValue?.text = progress.toString()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        binding?.compensationOffsetTune?.progress =
            prefs.getInt(getString(R.string.prefs_key_scoring_compensation_offset), 0) // 0...100
        binding?.compensationOffsetTuneValue?.text =
            prefs.getInt(getString(R.string.prefs_key_scoring_compensation_offset), 0).toString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding?.compensationOffsetTune?.min = 0
        }
        binding?.compensationOffsetTune?.max = 100

        binding?.compensationOffsetTune?.setOnSeekBarChangeListener(object :
            OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    editor.putInt(
                        getString(R.string.prefs_key_scoring_compensation_offset),
                        progress
                    )
                    editor.apply()
                    binding?.compensationOffsetTuneValue?.text = progress.toString()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        binding?.setNoLyrics?.isChecked =
            prefs.getBoolean(getString(R.string.prefs_key_set_no_lyric), false)
        binding?.setNoLyrics?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            editor.putBoolean(
                getString(R.string.prefs_key_set_no_lyric),
                isChecked
            )
            editor.apply()
        }

        binding?.radioMcc?.isChecked = prefs.getInt(
            getString(R.string.prefs_key_service_type),
            ExampleConstants.ServiceType.VENDOR_1.value
        ) == ExampleConstants.ServiceType.VENDOR_1.value
        binding?.radioMccEx?.isChecked = prefs.getInt(
            getString(R.string.prefs_key_service_type),
            ExampleConstants.ServiceType.VENDOR_1.value
        ) == ExampleConstants.ServiceType.VENDOR_2.value

        binding?.radioMcc?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                editor.putInt(
                    getString(R.string.prefs_key_service_type),
                    ExampleConstants.ServiceType.VENDOR_1.value
                )
                editor.apply()

                editor.putInt(
                    getString(R.string.prefs_key_lyric_type),
                    LyricType.XML.ordinal
                )
                editor.apply()
                MusicManager.updateMusicServiceType(ExampleConstants.ServiceType.VENDOR_1)
            }
            updateLyricType(prefs)
        }

        binding?.radioMccEx?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                editor.putInt(
                    getString(R.string.prefs_key_service_type),
                    ExampleConstants.ServiceType.VENDOR_2.value
                )
                editor.apply()

                editor.putInt(
                    getString(R.string.prefs_key_lyric_type),
                    LyricType.KRC.ordinal
                )
                editor.apply()
                MusicManager.updateMusicServiceType(ExampleConstants.ServiceType.VENDOR_2)
            }
            updateLyricType(prefs)
        }


        binding?.radioLyricXml?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                editor.putInt(
                    getString(R.string.prefs_key_lyric_type),
                    LyricType.XML.ordinal
                )
                editor.apply()
            }
        }

        binding?.radioLyricLrc?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                editor.putInt(
                    getString(R.string.prefs_key_lyric_type),
                    LyricType.LRC.ordinal
                )
                editor.apply()
            }
        }

        binding?.radioLyricKrc?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                editor.putInt(
                    getString(R.string.prefs_key_lyric_type),
                    LyricType.KRC.ordinal
                )
                editor.apply()
            }
        }

        updateLyricType(prefs)

        binding?.btnClearMusicCache?.setOnClickListener(View.OnClickListener {
            clearCache()
            Toast.makeText(this@SettingsActivity, "Music cache cleared success", Toast.LENGTH_LONG)
                .show()
        })
    }

    private fun updateLyricType(prefs: SharedPreferences) {
        binding?.radioLyricXml?.isEnabled = binding?.radioMcc?.isChecked ?: false
        binding?.radioLyricLrc?.isEnabled = binding?.radioMcc?.isChecked ?: false
        binding?.radioLyricKrc?.isEnabled = binding?.radioMccEx?.isChecked ?: false

        binding?.radioLyricXml?.isChecked = prefs.getInt(
            getString(R.string.prefs_key_lyric_type),
            LyricType.XML.ordinal
        ) == LyricType.XML.ordinal
        binding?.radioLyricLrc?.isChecked = prefs.getInt(
            getString(R.string.prefs_key_lyric_type),
            LyricType.XML.ordinal
        ) == LyricType.LRC.ordinal
        binding?.radioLyricKrc?.isChecked = prefs.getInt(
            getString(R.string.prefs_key_lyric_type),
            LyricType.XML.ordinal
        ) == LyricType.KRC.ordinal
    }

    private fun loadPreferencesLyricsUI(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ) {
        val indicatorSwitch =
            prefs.getBoolean(getString(R.string.prefs_key_start_of_verse_indicator_switch), true)
        binding?.lyricsStartOfVerseIndicatorSwitch?.isChecked = indicatorSwitch
        binding?.lyricsStartOfVerseIndicatorSwitch?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            editor.putBoolean(
                getString(R.string.prefs_key_start_of_verse_indicator_switch),
                isChecked
            )
            editor.apply()
        }

        val availableColorsOfIndicator =
            resources.getStringArray(R.array.available_indicator_colors)
        val indicatorColor = prefs.getString(
            getString(R.string.prefs_key_start_of_verse_indicator_color),
            "Default"
        ) ?: "Default"
        for (idx in availableColorsOfIndicator.indices) {
            if (indicatorColor == availableColorsOfIndicator[idx]) {
                binding?.startOfVerseIndicatorColorSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.startOfVerseIndicatorColorSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_start_of_verse_indicator_color),
                        availableColorsOfIndicator[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val availableRadiusOfIndicator =
            resources.getStringArray(R.array.available_indicator_radius)
        val indicatorRadius =
            prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_radius), "6dp")
                ?: "6dp"
        for (idx in availableRadiusOfIndicator.indices) {
            if (indicatorRadius == availableRadiusOfIndicator[idx]) {
                binding?.startOfVerseIndicatorRadiusSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.startOfVerseIndicatorRadiusSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_start_of_verse_indicator_radius),
                        availableRadiusOfIndicator[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        binding?.marginTopOfIndicatorTune?.progress = prefs.getInt(
            getString(R.string.prefs_key_start_of_verse_indicator_padding_top),
            6
        ) // 2...20
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding?.marginTopOfIndicatorTune?.min = 2
        }
        binding?.marginTopOfIndicatorTune?.max = 20
        binding?.marginTopOfIndicatorTune?.setOnSeekBarChangeListener(object :
            OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    editor.putInt(
                        getString(R.string.prefs_key_start_of_verse_indicator_padding_top),
                        progress
                    )
                    editor.apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        val availableColorsOfNormalLine = resources.getStringArray(R.array.available_text_colors)
        val defaultLineColor =
            prefs.getString(getString(R.string.prefs_key_normal_line_text_color), "Default")
                ?: "Default"
        for (idx in availableColorsOfNormalLine.indices) {
            if (defaultLineColor == availableColorsOfNormalLine[idx]) {
                binding?.normalLineColorSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.normalLineColorSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_normal_line_text_color),
                        availableColorsOfNormalLine[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val availableSizeOfNormalLineText = resources.getStringArray(R.array.available_text_size)
        val sizeOfNormalLineText =
            prefs.getInt(getString(R.string.prefs_key_normal_line_text_size), 12)
        for (idx in availableSizeOfNormalLineText.indices) {
            if (sizeOfNormalLineText == availableSizeOfNormalLineText[idx].toInt()) {
                binding?.sizeOfNormalTextSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.sizeOfNormalTextSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putInt(
                        getString(R.string.prefs_key_normal_line_text_size),
                        availableSizeOfNormalLineText[position].toInt()
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val availableColorsOfLineHighlighted =
            resources.getStringArray(R.array.available_highlighted_text_colors)
        val highlightedLineColor = prefs.getString(
            getString(R.string.prefs_key_current_line_highlighted_text_color),
            "Default"
        ) ?: "Default"
        for (idx in availableColorsOfLineHighlighted.indices) {
            if (highlightedLineColor == availableColorsOfLineHighlighted[idx]) {
                binding?.lineHighlightedColorSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.lineHighlightedColorSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_current_line_highlighted_text_color),
                        availableColorsOfLineHighlighted[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val availableColorsOfCurrentLine = resources.getStringArray(R.array.available_text_colors)
        val currentLineColor =
            prefs.getString(getString(R.string.prefs_key_current_line_text_color), "White")
                ?: "White"
        for (idx in availableColorsOfCurrentLine.indices) {
            if (currentLineColor == availableColorsOfCurrentLine[idx]) {
                binding?.currentLineColorSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.currentLineColorSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_current_line_text_color),
                        availableColorsOfCurrentLine[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val availableSizeOfCurrentLineText = resources.getStringArray(R.array.available_text_size)
        val sizeOfCurrentLineText =
            prefs.getInt(getString(R.string.prefs_key_current_line_text_size), 16)
        for (idx in availableSizeOfCurrentLineText.indices) {
            if (sizeOfCurrentLineText == availableSizeOfCurrentLineText[idx].toInt()) {
                binding?.sizeOfCurrentTextSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.sizeOfCurrentTextSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putInt(
                        getString(R.string.prefs_key_current_line_text_size),
                        availableSizeOfCurrentLineText[position].toInt()
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val availableSpacingBetweenLines = resources.getStringArray(R.array.available_spacing_lines)
        val spacingBetweenLines =
            prefs.getString(getString(R.string.prefs_key_line_spacing), "6dp") ?: "6dp"
        for (idx in availableSpacingBetweenLines.indices) {
            if (spacingBetweenLines == availableSpacingBetweenLines[idx]) {
                binding?.spacingBetweenLinesSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.spacingBetweenLinesSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_line_spacing),
                        availableSpacingBetweenLines[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val lyricsDraggingSwitch =
            prefs.getBoolean(getString(R.string.prefs_key_lyrics_dragging_switch), true)
        binding?.draggingSwitch?.isChecked = lyricsDraggingSwitch
        binding?.draggingSwitch?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            editor.putBoolean(
                getString(R.string.prefs_key_lyrics_dragging_switch),
                isChecked
            )
            editor.apply()
        }

        val availableTipsWhenNoLyricsAvailable =
            resources.getStringArray(R.array.text_when_no_lyrics_available)
        val textWhenNoLyricsAvailable =
            prefs.getString(getString(R.string.prefs_key_lyrics_not_available_text), "") ?: ""
        for (idx in availableTipsWhenNoLyricsAvailable.indices) {
            if (textWhenNoLyricsAvailable == availableTipsWhenNoLyricsAvailable[idx]) {
                binding?.lyricsNotAvailableSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.lyricsNotAvailableSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_lyrics_not_available_text),
                        availableTipsWhenNoLyricsAvailable[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val tipColorWhenNoLyricsAvailable = prefs.getString(
            getString(R.string.prefs_key_lyrics_not_available_text_color),
            "Default"
        ) ?: "Default"
        val availableColorsOfTipsWhenNoLyricsAvailable =
            resources.getStringArray(R.array.available_text_colors)
        for (idx in availableColorsOfTipsWhenNoLyricsAvailable.indices) {
            if (tipColorWhenNoLyricsAvailable == availableColorsOfTipsWhenNoLyricsAvailable[idx]) {
                binding?.lyricsNotAvailableColorSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.lyricsNotAvailableColorSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_lyrics_not_available_text_color),
                        availableColorsOfTipsWhenNoLyricsAvailable[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val availableSizeOfTips = resources.getStringArray(R.array.available_text_size)
        val sizeOfDefaultLineTextWhenNoLyricsAvailable =
            prefs.getInt(getString(R.string.prefs_key_lyrics_not_available_text_size), 26)
        for (idx in availableSizeOfTips.indices) {
            if (sizeOfDefaultLineTextWhenNoLyricsAvailable == availableSizeOfTips[idx].toInt()) {
                binding?.lyricsNotAvailableSizeSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.lyricsNotAvailableSizeSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putInt(
                        getString(R.string.prefs_key_lyrics_not_available_text_size),
                        availableSizeOfTips[position].toInt()
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
    }

    private fun loadPreferencesScoringUI(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ) {
        val availableHeightOfRefPitchStick =
            resources.getStringArray(R.array.available_spacing_lines)
        val refPitchStickHeight =
            prefs.getString(getString(R.string.prefs_key_ref_pitch_stick_height), "6dp") ?: "6dp"
        for (idx in availableHeightOfRefPitchStick.indices) {
            if (refPitchStickHeight == availableHeightOfRefPitchStick[idx]) {
                binding?.refPitchStickHeightSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.refPitchStickHeightSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_ref_pitch_stick_height),
                        availableHeightOfRefPitchStick[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val availableColorsOfDefaultRefPitchStick =
            resources.getStringArray(R.array.available_text_colors)
        val defaultRefPitchStickColor = prefs.getString(
            getString(R.string.prefs_key_default_ref_pitch_stick_color),
            "Default"
        ) ?: "Default"
        for (idx in availableColorsOfDefaultRefPitchStick.indices) {
            if (defaultRefPitchStickColor == availableColorsOfDefaultRefPitchStick[idx]) {
                binding?.colorOfDefaultRefPitchStickSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.colorOfDefaultRefPitchStickSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_default_ref_pitch_stick_color),
                        availableColorsOfDefaultRefPitchStick[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val availableColorsOfHighlightedRefPitchStick =
            resources.getStringArray(R.array.available_highlighted_text_colors)
        val highlightedRefPitchStickColor = prefs.getString(
            getString(R.string.prefs_key_highlighted_ref_pitch_stick_color),
            "Default"
        ) ?: "Default"
        for (idx in availableColorsOfHighlightedRefPitchStick.indices) {
            if (highlightedRefPitchStickColor == availableColorsOfHighlightedRefPitchStick[idx]) {
                binding?.colorOfHighlightedRefPitchStickSelector?.setSelection(idx, false)
                break
            }
        }
        binding?.colorOfHighlightedRefPitchStickSelector?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    editor.putString(
                        getString(R.string.prefs_key_highlighted_ref_pitch_stick_color),
                        availableColorsOfHighlightedRefPitchStick[position]
                    )
                    editor.apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val particleEffectSwitch =
            prefs.getBoolean(getString(R.string.prefs_key_particle_effect_switch), true)
        binding?.particleEffectSwitch?.isChecked = particleEffectSwitch
        binding?.particleEffectSwitch?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            editor.putBoolean(
                getString(R.string.prefs_key_particle_effect_switch),
                isChecked
            )
            editor.apply()
        }

        val customizedIndicatorAndParticleSwitch = prefs.getBoolean(
            getString(R.string.prefs_key_customized_indicator_and_particle_switch),
            false
        )
        binding?.customizedIndicatorAndParticleSwitch?.isChecked =
            customizedIndicatorAndParticleSwitch
        binding?.customizedIndicatorAndParticleSwitch?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            editor.putBoolean(
                getString(R.string.prefs_key_customized_indicator_and_particle_switch),
                isChecked
            )
            editor.apply()
        }

        val hitScoreThreshold =
            (prefs.getFloat(getString(R.string.prefs_key_hit_score_threshold), 0.8f) * 100).toInt()
        binding?.hitScoreThresholdTune?.progress = hitScoreThreshold // 0...100
        binding?.hitScoreThresholdTuneValue?.text =
            prefs.getFloat(getString(R.string.prefs_key_hit_score_threshold), 0.8f).toString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding?.hitScoreThresholdTune?.min = 1
        }
        binding?.hitScoreThresholdTune?.max = 100
        binding?.hitScoreThresholdTune?.setOnSeekBarChangeListener(object :
            OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val threshold = progress.toFloat() / 100f
                    editor.putFloat(getString(R.string.prefs_key_hit_score_threshold), threshold)
                    editor.apply()
                    binding?.hitScoreThresholdTuneValue?.text = threshold.toString()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })
    }

    private fun loadPreferencesOtherSettingsUI(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ) {
        binding?.rtcAudioDumpSwitch?.isChecked =
            prefs.getBoolean(getString(R.string.prefs_key_rtc_audio_dump), false)
        binding?.rtcAudioDumpSwitch?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            editor.putBoolean(
                getString(R.string.prefs_key_rtc_audio_dump),
                isChecked
            )
            editor.apply()
        }

        binding?.rtcAudioDumpSwitch?.isChecked =
            prefs.getBoolean(getString(R.string.prefs_key_rtc_audio_dump), false)
        binding?.rtcAudioDumpSwitch?.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            editor.putBoolean(
                getString(R.string.prefs_key_rtc_audio_dump),
                isChecked
            )
            editor.apply()
        }
    }
}