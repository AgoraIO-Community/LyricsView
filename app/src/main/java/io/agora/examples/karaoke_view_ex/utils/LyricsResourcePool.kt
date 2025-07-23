package io.agora.examples.karaoke_view_ex.utils

import io.agora.musiccontentcenter.MccConstants

object LyricsResourcePool {
    fun asMusicListVendor1(): List<MusicResource> {
        val list = ArrayList<MusicResource>()

        list.add(
            MusicResource(
                0,
                "7162848775797850",
                "爱情转移",
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_DEFAULT.value(),
                4,
                ""
            )
        )
        list.add(
            MusicResource(
                1,
                "7104926139658410",
                "说爱你",
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_DEFAULT.value(),
                4,
                ""
            )
        )
        list.add(
            MusicResource(
                2,
                "7162848696618210",
                "love story",
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_DEFAULT.value(),
                4,
                ""
            )
        )

        //海外曲库
        //list.add(new MusicResource(3, "6800601815479800", "趁着你", MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_DEFAULT.value(), 4, ""));
        return list
    }

    fun asMusicListVendor2(): List<MusicResource> {
        val list = ArrayList<MusicResource>()

//        list.add(
//            MusicResource(
//                0,
//                "40289835",
//                "十年",
//                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2.value(),
//                4,
//                ""
//            )
//        )
//        list.add(
//            MusicResource(
//                0,
//                "32183724",
//                "你在不在",
//                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2.value(),
//                4,
//                ""
//            )
//        )
        list.add(
            MusicResource(
                0,
                "89488966",
                "在你的身边",
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2.value(),
                4,
                "{\"format\":{\"highPart\":1}}"
            )
        )
        list.add(
            MusicResource(
                0,
                "310937426",
                "怀抱",
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2.value(),
                4,
                ""
            )
        )
//        list.add(
//            MusicResource(
//                0,
//                "542869354",
//                "Masih Mencintainya",
//                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2.value(),
//                4,
//                ""
//            )
//        )
//        list.add(
//            MusicResource(
//                0,
//                "625281172",
//                "最后一页",
//                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2.value(),
//                4,
//                ""
//            )
//        )
        list.add(
            MusicResource(
                0,
                "621162805",
                "梦回花事了",
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2.value(),
                4,
                ""
            )
        )
        list.add(
            MusicResource(
                0,
                "288308118",
                "还是分开",
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2.value(),
                4,
                ""
            )
        )
        list.add(
            MusicResource(
                0,
                "130598261",
                "火力全开",
                MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2.value(),
                4,
                ""
            )
        )
        return list
    }

    class MusicResource(
        val index: Int,
        val songCode: String,
        val songName: String,
        val vendorId: Int,
        val songType: Int,
        val songOptionJson: String
    )
}
