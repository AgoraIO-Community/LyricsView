package io.agora.examples.karaoke_view_ex

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.agora.examples.utils.ServiceType
import io.agora.karaoke_view_ex.internal.constants.LyricType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoServicePreferencesInstrumentedTest {
    @Test fun nativeMccIsTheDefaultAndMigratesAllNonTmePreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
        val serviceKey = context.getString(R.string.prefs_key_service_type)
        val lyricKey = context.getString(R.string.prefs_key_lyric_type)
        val previousService = if (prefs.contains(serviceKey)) prefs.getInt(serviceKey, -1) else null
        val previousLyric = if (prefs.contains(lyricKey)) prefs.getInt(lyricKey, -1) else null
        try {
            for (stored in listOf(null, ServiceType.MCC_EX.type, ServiceType.MCC.type, -99, ServiceType.TME.type)) {
                val editor = prefs.edit().putInt(lyricKey, LyricType.KRC.ordinal)
                if (stored == null) editor.remove(serviceKey) else editor.putInt(serviceKey, stored)
                editor.commit()
                val expected = if (stored == ServiceType.TME.type) ServiceType.TME else ServiceType.MCC
                val lyric = if (expected == ServiceType.TME) LyricType.LRC else LyricType.XML
                assertEquals("stored service=$stored", expected, DemoServicePreferences.normalize(context))
                assertEquals(expected.type, prefs.getInt(serviceKey, -1))
                assertEquals(lyric.ordinal, prefs.getInt(lyricKey, -1))
                assertEquals(expected, DemoServicePreferences.normalize(context))
            }
        } finally {
            val editor = prefs.edit()
            if (previousService == null) editor.remove(serviceKey) else editor.putInt(serviceKey, previousService)
            if (previousLyric == null) editor.remove(lyricKey) else editor.putInt(lyricKey, previousLyric)
            editor.commit()
        }
    }
}
