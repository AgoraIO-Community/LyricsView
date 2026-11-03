package io.agora.karaoke_view_ex.tme

/** All callbacks are posted asynchronously to the Android main thread. */
interface TMEParserDelegate {
    fun onSongs(requestId: String, result: TMESongsResult) {}
    fun onSearchSongs(requestId: String, result: TMESearchSongsResult) {}
    fun onSongInfo(requestId: String, result: TMESongInfoResult) {}
    fun onSongUrl(requestId: String, result: TMESongUrlResult) {}
    fun onSonglistPage(requestId: String, result: TMEPageResult) {}
    fun onSonglistDetail(requestId: String, result: TMEDetailResult) {}
    fun onRanklistPage(requestId: String, result: TMEPageResult) {}
    fun onRanklistDetail(requestId: String, result: TMEDetailResult) {}
    fun onParseError(requestId: String, jsonOption: String, responseBody: String, error: TMEParseError) {}
}

/** Java callers may extend this class and override only the callbacks they need. */
open class TMEParserAdapter : TMEParserDelegate
