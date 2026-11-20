package io.agora.examples.lyricsscore

import android.content.Context
import android.os.Bundle
import io.agora.examples.utils.ServiceType
import io.agora.lyricsscore.internal.constants.LyricType

/** Compatibility entry for existing supplied-session intents; uses the shared MCC UI. */
class TmeSingingActivity : MainActivity() {
    override fun applyInitialServiceSelection(savedInstanceState: Bundle?) {
        // A restored activity must honor a later service change made in Settings.
        if (savedInstanceState == null) {
            getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE).edit()
                .putInt(getString(R.string.prefs_key_service_type), ServiceType.TME.type)
                .putInt(getString(R.string.prefs_key_lyric_type), LyricType.LRC.ordinal).apply()
        }
        super.applyInitialServiceSelection(savedInstanceState)
    }
}
