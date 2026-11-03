package io.agora.karaoke_view_ex.tme

data class TMEImagePath(val key: String, val value: String)
data class TMEAlbum(val albumId: String?, val albumName: String?, val imagePathMapList: List<TMEImagePath>?)
data class TMEArtist(val artistId: String?, val artistName: String?)
data class TMELyric(val type: String, val url: String)
data class TMECopyright(val sceneId: String, val terminalIdList: List<String>)

/** Shared by songs, search-song and song-info. Dates and URLs retain their original values. */
data class TMESongDetail(
    val songId: String,
    val songName: String,
    val version: String?,
    val duration: Int?,
    val status: Int?,
    val sequence: Int?,
    val grantStatus: Int?,
    val grantStartTime: String?,
    val publicTime: String?,
    val language: String?,
    val genre: String?,
    val grantedAreaCodes: List<String>?,
    val pitchUrl: String?,
    val chorusStartMS: Int?,
    val chorusEndMS: Int?,
    val copyrightList: List<TMECopyright>?,
    val album: TMEAlbum?,
    val artistList: List<TMEArtist>?,
    val lrcList: List<TMELyric>?
)

data class TMESongsResult(val songList: List<TMESongDetail>, val nextQueryInfo: String?)
data class TMESearchSongsResult(val total: Int, val songList: List<TMESongDetail>)
data class TMESongInfoResult(val songList: List<TMESongDetail>)
data class TMEMedia(val fileType: String, val url: String, val expire: String)
data class TMESongUrlResult(val mediaList: List<TMEMedia>)
data class TMEPageItem(val code: String, val title: String, val description: String?, val url: String?, val status: Int?)
data class TMEPageResult(val total: Int, val list: List<TMEPageItem>)
data class TMESongReference(val songId: String)
data class TMEDetailResult(
    val code: String,
    val title: String,
    val description: String?,
    val status: Int?,
    val imgUrl: String?,
    val songList: List<TMESongReference>
)

internal sealed class TMEParsedResponse {
    data class Songs(val result: TMESongsResult) : TMEParsedResponse()
    data class SearchSongs(val result: TMESearchSongsResult) : TMEParsedResponse()
    data class SongInfo(val result: TMESongInfoResult) : TMEParsedResponse()
    data class SongUrl(val result: TMESongUrlResult) : TMEParsedResponse()
    data class SonglistPage(val result: TMEPageResult) : TMEParsedResponse()
    data class SonglistDetail(val result: TMEDetailResult) : TMEParsedResponse()
    data class RanklistPage(val result: TMEPageResult) : TMEParsedResponse()
    data class RanklistDetail(val result: TMEDetailResult) : TMEParsedResponse()
}
