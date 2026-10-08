package io.agora.karaoke_view_ex.tme

import org.junit.Assert.*
import org.junit.Test

class TMEJsonDecoderTest {
    private fun decode(action: String, data: String): TMEParsedResponse =
        TMEJsonDecoder.decode(request(action), 200, """{"code":0,"data":$data}""")

    @Test fun songsRetainEveryEntryAndPagination() {
        val songs = (1..9).joinToString(",") { """{"songId":"$it","songName":"name$it"}""" }
        val result = (decode("songs", """{"songList":[$songs],"nextQueryInfo":"cursor"}""")
            as TMEParsedResponse.Songs).result
        assertEquals(9, result.songList.size)
        assertEquals("9", result.songList.last().songId)
        assertEquals("cursor", result.nextQueryInfo)
        assertNull(result.songList.first().album)
    }

    @Test fun searchAndInfoShareFullSongDecoding() {
        val song = """{"songId":"s","songName":"歌名","version":"v1","duration":220,
          "status":1,"sequence":8,"grantStatus":1,"grantStartTime":"2022-06-15 00:00:00",
          "publicTime":"2004-09-15 00:00:00","language":"普通话","genre":"POP",
          "grantedAreaCodes":["CN"],"pitchUrl":"https://example.test/p?x=a%2Fb&sig=+",
          "chorusStartMS":21923,"chorusEndMS":42983,
          "copyrightList":[{"sceneId":"1","terminalIdList":["1","2"]}],
          "album":{"albumId":"a","albumName":"专辑","imagePathMapList":[{"key":"cover","value":"image"}]},
          "artistList":[{"artistId":"r","artistName":"歌手"}],
          "lrcList":[{"type":"lrc","url":"https://example.test/l?sig=%2F"}]}"""
        val searched = (decode("search-song", """{"total":1,"songList":[$song]}""")
            as TMEParsedResponse.SearchSongs).result
        val info = (decode("song-info", """{"songList":[$song]}""") as TMEParsedResponse.SongInfo).result
        val listed = (decode("songs", """{"songList":[$song]}""") as TMEParsedResponse.Songs).result
        assertEquals(1, searched.total)
        val s = searched.songList.single()
        assertEquals(s, info.songList.single())
        assertEquals(s, listed.songList.single())
        assertEquals("v1", s.version)
        assertEquals(220, s.duration)
        assertEquals(1, s.status)
        assertEquals(8, s.sequence)
        assertEquals(1, s.grantStatus)
        assertEquals("2022-06-15 00:00:00", s.grantStartTime)
        assertEquals("2004-09-15 00:00:00", s.publicTime)
        assertEquals("普通话", s.language)
        assertEquals("POP", s.genre)
        assertEquals(listOf("CN"), s.grantedAreaCodes)
        assertEquals("https://example.test/p?x=a%2Fb&sig=+", s.pitchUrl)
        assertEquals(21923, s.chorusStartMS)
        assertEquals(42983, s.chorusEndMS)
        val copyright = requireNotNull(s.copyrightList).single()
        assertEquals(listOf("1", "2"), copyright.terminalIdList)
        assertEquals("1", copyright.sceneId)
        val album = requireNotNull(s.album)
        assertEquals("a", album.albumId)
        assertEquals("专辑", album.albumName)
        assertEquals(TMEImagePath("cover", "image"), album.imagePathMapList!!.single())
        assertEquals(TMEArtist("r", "歌手"), s.artistList!!.single())
        assertEquals(TMELyric("lrc", "https://example.test/l?sig=%2F"), s.lrcList!!.single())
    }

    @Test fun mediaPreserveStringExpiryAndSignedURLs() {
        val result = (decode("song-url", """{"mediaList":[
          {"fileType":"mp3","url":"https://example.test/u?token=a%2B&x=+","expire":"001234"},
          {"fileType":"aac","url":"second","expire":"999"}]}""") as TMEParsedResponse.SongUrl).result
        assertEquals(2, result.mediaList.size)
        assertEquals("001234", result.mediaList.first().expire)
        assertEquals("https://example.test/u?token=a%2B&x=+", result.mediaList.first().url)
    }

    @Test fun pagesAndDetailsUseDistinctActionsAndURLKeys() {
        val page = """{"total":1,"list":[{"code":"c","title":"title","description":"desc","url":"page","status":1}]}"""
        val songs = (decode("songlist-page", page) as TMEParsedResponse.SonglistPage).result
        val ranks = (decode("ranklist-page", page) as TMEParsedResponse.RanklistPage).result
        assertEquals(songs, ranks)
        assertEquals(1, songs.total)
        assertEquals(TMEPageItem("c", "title", "desc", "page", 1), songs.list.single())
        val detail = """{"code":"c","title":"title","description":"desc","status":2,"imgUrl":"image","songList":[{"songId":"s"}]}"""
        val songDetail = (decode("songlist-detail", detail) as TMEParsedResponse.SonglistDetail).result
        val rankDetail = (decode("ranklist-detail", detail) as TMEParsedResponse.RanklistDetail).result
        assertEquals(songDetail, rankDetail)
        assertEquals("image", songDetail.imgUrl)
        assertEquals("desc", songDetail.description)
        assertEquals(2, songDetail.status)
        assertEquals(listOf(TMESongReference("s")), songDetail.songList)
    }

    @Test fun emptyListsAndOptionalNullMetadataAreValid() {
        for (action in listOf("songs", "search-song", "song-info")) {
            decode(action, """{"total":0,"songList":[],"nextQueryInfo":null}""")
        }
        decode("song-url", """{"mediaList":[]}""")
        for (action in listOf("songlist-page", "ranklist-page")) decode(action, """{"total":0,"list":[]}""")
        for (action in listOf("songlist-detail", "ranklist-detail")) {
            decode(action, """{"code":"c","title":"t","songList":[],"imgUrl":null}""")
        }
        val optional = (decode("song-info", """{"songList":[{"songId":"s","songName":"n","album":null,"artistList":[{}]}]}""")
            as TMEParsedResponse.SongInfo).result.songList.single()
        assertNull(optional.album)
        assertEquals(TMEArtist(null, null), optional.artistList!!.single())
    }

    @Test fun invalidRequestPrecedesHTTPAndUnknownActionsAreDistinct() {
        val requests = listOf("", "[]", "null", "{}", """{"vendorId":"2","actionType":"songs"}""",
            """{"vendorId":1,"actionType":"songs"}""", """{"vendorId":2,"actionType":2}""")
        requests.forEach { assertError<TMEParseError.InvalidRequest>(it, 500, "bad") }
        assertError<TMEParseError.UnsupportedAction>(request("other"), 500, "bad")
        val error = assertError<TMEParseError.HttpError>(request("songs"), 403, "bad")
        assertEquals(403, error.httpCode)
    }

    @Test fun businessErrorPrecedesMalformedData() {
        val error = assertError<TMEParseError.BusinessError>(request("songs"), 200,
            """{"code":7,"msg":"denied","data":{"songList":"bad"}}""")
        assertEquals(7, error.code)
        assertEquals("denied", error.msg)
        val missing = assertError<TMEParseError.BusinessError>(request("songs"), 200, """{"code":9}""")
        assertNull(missing.msg)
    }

    @Test fun strictJSONRejectsMalformedAndTrailingInputs() {
        listOf("not json", "{} {}", "{'code':0}", "{code:0}", "/*comment*/{}",
            """{"code":0,"data":{"songList":[],}}""", "").forEach {
            assertError<TMEParseError.InvalidResponse>(request("songs"), 200, it)
        }
        assertError<TMEParseError.InvalidRequest>("""{"vendorId":2,"actionType":"songs"} true""", 200, "{}")
    }

    @Test fun gsonNonLenientExtensionsAreRejectedInBothRequestAndResponse() {
        val invalidValues = listOf("TRUE", "FALSE", "NULL", "01", "+1", "1.", "NaN", "Infinity",
            "\"raw\nnewline\"", "\"raw\ttab\"", "\"raw\u0000control\"")
        invalidValues.forEach { value ->
            val request = """{"vendorId":2,"actionType":"songs","unused":$value}"""
            assertError<TMEParseError.InvalidRequest>(request, 200, emptySongsBody())
            val response = """{"code":0,"data":{"songList":[]},"unused":$value}"""
            assertError<TMEParseError.InvalidResponse>(tmeRequest(), 200, response)
        }
    }

    @Test fun strictJSONKeepsValidEscapesLiteralsAndExponentIntegers() {
        val response = """{"code":0e0,"ignored":[true,false,null,-1,1.25],"data":{"songList":[
            {"songId":"s","songName":"line\nnext\t\u6b4c","duration":2.2e2}]}}"""
        val result = TMEJsonDecoder.decode(tmeRequest(), 200, response) as TMEParsedResponse.Songs
        assertEquals("line\nnext\t歌", result.result.songList.single().songName)
        assertEquals(220, result.result.songList.single().duration)
    }

    private fun emptySongsBody() = """{"code":0,"data":{"songList":[]}}"""

    @Test fun missingAndWronglyTypedResponseFieldsAreRejected() {
        listOf("[]", "null", "{}", """{"code":"0"}""", """{"code":0.5}""",
            """{"code":2147483648}""", """{"code":0,"msg":false,"data":{}}""",
            """{"code":0,"data":null}""").forEach {
            assertError<TMEParseError.InvalidResponse>(request("songs"), 200, it)
        }
        val badData = listOf("{}", """{"songList":null}""", """{"songList":{}}""",
            """{"songList":[{"songId":1,"songName":"n"}]}""",
            """{"songList":[{"songId":"s"}]}""",
            """{"songList":[{"songId":"s","songName":"n","duration":"1"}]}""",
            """{"songList":[{"songId":"s","songName":"n","artistList":[false]}]}""",
            """{"songList":[{"songId":"s","songName":"n","copyrightList":[{"sceneId":"1","terminalIdList":[1]}]}]}""")
        badData.forEach {
            assertError<TMEParseError.InvalidResponse>(request("songs"), 200, """{"code":0,"data":$it}""")
        }
        assertError<TMEParseError.InvalidResponse>(request("song-url"), 200,
            """{"code":0,"data":{"mediaList":[{"fileType":"mp3","url":"u","expire":123}]}}""")
        assertError<TMEParseError.InvalidResponse>(request("search-song"), 200,
            """{"code":0,"data":{"songList":[]}}""")
        assertError<TMEParseError.InvalidResponse>(request("ranklist-page"), 200,
            """{"code":0,"data":{"total":1,"list":[{"code":1,"title":"t"}]}}""")
    }

    @Test fun every2xxStatusIsAccepted() {
        for (status in listOf(200, 201, 204, 299)) {
            TMEJsonDecoder.decode(request("songs"), status, """{"code":0,"data":{"songList":[]}}""")
        }
        for (status in listOf(199, 300, 500)) assertError<TMEParseError.HttpError>(request("songs"), status, "{}")
    }

    private inline fun <reified E : TMEParseError> assertError(option: String, http: Int, response: String): E {
        try {
            TMEJsonDecoder.decode(option, http, response)
            fail("Expected ${E::class.java.simpleName}")
        } catch (error: TMEParseError) {
            assertTrue("Expected ${E::class.java.simpleName}, got ${error.javaClass.simpleName}", error is E)
            return error as E
        }
        throw AssertionError("unreachable")
    }

    private fun request(action: String) = """{"vendorId":2,"actionType":"$action"}"""
}
