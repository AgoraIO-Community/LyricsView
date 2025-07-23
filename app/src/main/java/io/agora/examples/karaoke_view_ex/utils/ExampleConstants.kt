package io.agora.examples.karaoke_view_ex.utils

object ExampleConstants {
    const val TAG = "Lyric"
    const val SP_KEY_VENDOR_2_TOKEN = "vendor2_token"
    const val SP_KEY_VENDOR_2_TOKEN_TIME = "vendor2_token_time"
    const val SP_KEY_VENDOR_2_USER_ID = "vendor2_user_id"

    const val TOKEN_EXPIRE_TIME = 1000 * 60 * 24 * 24L


    enum class Status(var value: Int) {
        IDLE(0), Opened(1), Started(2), Paused(3), Stopped(4);

        fun isAtLeast(state: Status): Boolean {
            return compareTo(state) >= 0
        }
    }

    enum class ServiceType(var value: Int) {
        VENDOR_1(0), VENDOR_2(1);

        companion object {
            @JvmStatic
            fun getServiceVendor(value: Int): ServiceType {
                return when (value) {
                    0 -> VENDOR_1
                    1 -> VENDOR_2
                    else -> {
                        VENDOR_1
                    }
                }
            }
        }
    }

}