package io.agora.karaoke_view_ex.tme

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/** No SDK or UI dependency. Explicit checks prevent Gson's string/number coercion. */
internal object TMEJsonDecoder {
    private val elementAdapter = Gson().getAdapter(JsonElement::class.java)
    private val actions = setOf("songs", "search-song", "song-info", "song-url",
        "songlist-page", "songlist-detail", "ranklist-page", "ranklist-detail")

    fun decode(jsonOption: String, httpCode: Int, responseBody: String): TMEParsedResponse {
        val action = validate({ TMEParseError.InvalidRequest() }) {
            val request = objectFrom(jsonOption)
            require(integer(request.required("vendorId")) == 2)
            string(request.required("actionType"))
        }
        if (action !in actions) throw TMEParseError.UnsupportedAction(action)
        if (httpCode !in 200..299) throw TMEParseError.HttpError(httpCode)

        return validate({ TMEParseError.InvalidResponse() }) {
            val envelope = objectFrom(responseBody)
            val code = integer(envelope.required("code"))
            val msg = envelope.optional("msg", ::string)
            if (code != 0) throw TMEParseError.BusinessError(code, msg)
            val data = obj(envelope.required("data"))
            when (action) {
                "songs" -> TMEParsedResponse.Songs(TMESongsResult(songs(data), data.optional("nextQueryInfo", ::string)))
                "search-song" -> TMEParsedResponse.SearchSongs(TMESearchSongsResult(integer(data.required("total")), songs(data)))
                "song-info" -> TMEParsedResponse.SongInfo(TMESongInfoResult(songs(data)))
                "song-url" -> TMEParsedResponse.SongUrl(TMESongUrlResult(list(data.required("mediaList")) {
                    val media = obj(it)
                    TMEMedia(string(media.required("fileType")), string(media.required("url")), string(media.required("expire")))
                }))
                "songlist-page" -> TMEParsedResponse.SonglistPage(page(data))
                "ranklist-page" -> TMEParsedResponse.RanklistPage(page(data))
                "songlist-detail" -> TMEParsedResponse.SonglistDetail(detail(data))
                else -> TMEParsedResponse.RanklistDetail(detail(data))
            }
        }
    }

    private fun songs(data: JsonObject): List<TMESongDetail> = list(data.required("songList")) {
        val s = obj(it)
        TMESongDetail(
            songId = string(s.required("songId")),
            songName = string(s.required("songName")),
            version = s.optional("version", ::string),
            duration = s.optional("duration", ::integer),
            status = s.optional("status", ::integer),
            sequence = s.optional("sequence", ::integer),
            grantStatus = s.optional("grantStatus", ::integer),
            grantStartTime = s.optional("grantStartTime", ::string),
            publicTime = s.optional("publicTime", ::string),
            language = s.optional("language", ::string),
            genre = s.optional("genre", ::string),
            grantedAreaCodes = s.optional("grantedAreaCodes") { codes -> list(codes, ::string) },
            pitchUrl = s.optional("pitchUrl", ::string),
            chorusStartMS = s.optional("chorusStartMS", ::integer),
            chorusEndMS = s.optional("chorusEndMS", ::integer),
            copyrightList = s.optional("copyrightList") { copyrights -> list(copyrights) { c ->
                val copyright = obj(c)
                TMECopyright(string(copyright.required("sceneId")), list(copyright.required("terminalIdList"), ::string))
            } },
            album = s.optional("album") { a ->
                val album = obj(a)
                TMEAlbum(album.optional("albumId", ::string), album.optional("albumName", ::string),
                    album.optional("imagePathMapList") { images -> list(images) { image ->
                        val path = obj(image)
                        TMEImagePath(string(path.required("key")), string(path.required("value")))
                    } })
            },
            artistList = s.optional("artistList") { artists -> list(artists) { a ->
                val artist = obj(a)
                TMEArtist(artist.optional("artistId", ::string), artist.optional("artistName", ::string))
            } },
            lrcList = s.optional("lrcList") { lyrics -> list(lyrics) { l ->
                val lyric = obj(l)
                TMELyric(string(lyric.required("type")), string(lyric.required("url")))
            } }
        )
    }

    private fun page(data: JsonObject) = TMEPageResult(integer(data.required("total")), list(data.required("list")) {
        val item = obj(it)
        TMEPageItem(string(item.required("code")), string(item.required("title")),
            item.optional("description", ::string), item.optional("url", ::string), item.optional("status", ::integer))
    })

    private fun detail(data: JsonObject) = TMEDetailResult(
        string(data.required("code")), string(data.required("title")),
        data.optional("description", ::string), data.optional("status", ::integer), data.optional("imgUrl", ::string),
        list(data.required("songList")) { TMESongReference(string(obj(it).required("songId"))) }
    )

    private fun objectFrom(raw: String): JsonObject {
        TMEStrictJson.validate(raw)
        return JsonReader(StringReader(raw)).use { reader ->
            reader.isLenient = false
            val root = elementAdapter.read(reader)
            require(reader.peek() == JsonToken.END_DOCUMENT)
            obj(root)
        }
    }

    private fun obj(value: JsonElement): JsonObject {
        require(value.isJsonObject)
        return value.asJsonObject
    }

    private fun string(value: JsonElement): String {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun integer(value: JsonElement): Int {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asBigDecimal.intValueExact()
    }

    private fun <T> list(value: JsonElement, transform: (JsonElement) -> T): List<T> {
        require(value.isJsonArray)
        return value.asJsonArray.map(transform)
    }

    private fun JsonObject.required(key: String): JsonElement =
        get(key)?.takeUnless { it.isJsonNull } ?: throw IllegalArgumentException("Missing field")

    private fun <T> JsonObject.optional(key: String, transform: (JsonElement) -> T): T? =
        get(key)?.takeUnless { it.isJsonNull }?.let(transform)

    private inline fun <T> validate(error: () -> TMEParseError, decode: () -> T): T {
        try {
            return decode()
        } catch (failure: TMEParseError) {
            throw failure
        } catch (_: Exception) {
            // Never attach a Gson exception: its message can contain response values.
            throw error()
        }
    }
}
