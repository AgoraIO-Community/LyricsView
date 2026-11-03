package io.agora.examples.karaoke_view_ex

import android.content.Context
import io.agora.examples.utils.ServiceType
import io.agora.karaoke_view_ex.internal.constants.LyricType

/** Only the Demo's two supported choices; the underlying service enum remains compatible. */
object DemoServicePreferences {
    @JvmStatic fun normalize(context: Context): ServiceType {
        val prefs = context.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
        val serviceKey = context.getString(R.string.prefs_key_service_type)
        val lyricKey = context.getString(R.string.prefs_key_lyric_type)
        val stored = prefs.getInt(serviceKey, -1)
        val service = if (stored == ServiceType.TME.type) ServiceType.TME else ServiceType.MCC
        val lyric = if (service == ServiceType.TME) LyricType.LRC else LyricType.XML
        if (stored != service.type || prefs.getInt(lyricKey, -1) != lyric.ordinal) {
            prefs.edit().putInt(serviceKey, service.type).putInt(lyricKey, lyric.ordinal).apply()
        }
        return service
    }
}
