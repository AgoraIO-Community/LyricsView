package io.agora.karaoke_view_ex.tme

/** Messages intentionally exclude raw payloads, URLs and decoder exception text. */
sealed class TMEParseError(message: String) : Exception(message) {
    class InvalidRequest : TMEParseError("Invalid TME request")
    class UnsupportedAction(val actionType: String) : TMEParseError("Unsupported TME action")
    class HttpError(val httpCode: Int) : TMEParseError("TME HTTP error ($httpCode)")
    class BusinessError(val code: Int, val msg: String?) : TMEParseError("TME business error ($code)")
    class InvalidResponse : TMEParseError("Invalid TME response")
}
