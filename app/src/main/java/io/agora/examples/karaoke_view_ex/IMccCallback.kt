package io.agora.examples.karaoke_view_ex

interface IMccCallback {
    fun onMusicLyricRequest(
        songCode: Long,
        lyricUrl: String,
        pitchUrl: String,
        songOffsetBegin: Int,
        songOffsetEnd: Int,
        lyricOffset: Int
    ) {
    }

    fun onMusicPreloadResult(songCode: Long, percent: Int) {
    }

    fun onMusicPositionChange(position: Long) {
    }

    fun onMusicPitchScore(
        internalSongCode: Long,
        voicePitch: Double,
        pitchScore: Double,
        progressInMs: Long
    ) {
    }

    fun onMusicLineScore(
        internalSongCode: Long,
        linePitchScore: Float,
        cumulativeTotalLinePitchScores: Float,
        performedLineIndex: Int,
        performedTotalLines: Int
    ) {
    }

    fun onMusicPlaying() {
    }

    fun onMusicStop() {
    }
}