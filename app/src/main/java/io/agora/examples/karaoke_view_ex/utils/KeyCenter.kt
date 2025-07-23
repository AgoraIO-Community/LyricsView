package io.agora.examples.karaoke_view_ex.utils

import io.agora.examples.karaoke_view_ex.BuildConfig
import io.agora.media.RtcTokenBuilder
import io.agora.rtm.RtmTokenBuilder
import io.agora.rtm.RtmTokenBuilder2
import java.util.Random

object KeyCenter {
    private const val USER_MAX_UID: Int = 10000
    const val CHANNEL_NAME: String = "Karaoke-Example-Test-Android"
    private const val APP_ID: String = BuildConfig.APP_ID
    private var USER_RTC_UID = -1

    val userUid: Int
        get() {
            if (-1 == USER_RTC_UID) {
                USER_RTC_UID = Random().nextInt(USER_MAX_UID)
            }
            return USER_RTC_UID
        }

    fun getRtcToken(channelId: String?, uid: Int): String {
        if (BuildConfig.APP_CERTIFICATE.isEmpty()) {
            return BuildConfig.APP_ID
        }
        return RtcTokenBuilder().buildTokenWithUid(
            APP_ID,
            BuildConfig.APP_CERTIFICATE,
            channelId,
            uid,
            RtcTokenBuilder.Role.Role_Publisher,
            0
        )
    }

    fun getRtmToken(uid: Int): String? {
        try {
            return RtmTokenBuilder().buildToken(
                APP_ID,
                BuildConfig.APP_CERTIFICATE,
                uid.toString(),
                RtmTokenBuilder.Role.Rtm_User,
                0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun getRtmToken2(uid: Int): String? {
        try {
            return RtmTokenBuilder2().buildToken(
                APP_ID,
                BuildConfig.APP_CERTIFICATE,
                uid.toString(),
                24 * 60 * 60
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
