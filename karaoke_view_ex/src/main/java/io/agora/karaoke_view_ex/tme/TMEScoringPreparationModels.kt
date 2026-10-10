package io.agora.karaoke_view_ex.tme

import io.agora.karaoke_view_ex.model.LyricModel

enum class TMEScoringPreparationStage {
    QUERYING, LYRIC_DOWNLOADING, LYRIC_DOWNLOADED, PITCH_DOWNLOADING, PITCH_DOWNLOADED, PARSING
}

enum class TMEScoringResource { LYRIC, PITCH }

enum class TMEScoringPreparationErrorCategory {
    REQUEST_REJECTED, HTTP, BUSINESS, INVALID_RESPONSE, SONG_NOT_FOUND, MISSING_RESOURCE,
    INVALID_URL, DOWNLOAD, EMPTY_FILE, FILE_IO, PARSE, QUERY_TIMEOUT, DOWNLOAD_TIMEOUT, OVERALL_TIMEOUT
}

/** Safe for presentation: contains no response, URL or underlying exception message. */
data class TMEScoringPreparationError @JvmOverloads constructor(
    val category: TMEScoringPreparationErrorCategory,
    val resource: TMEScoringResource? = null,
    val httpCode: Int? = null,
    val businessCode: Int? = null
)

data class TMEScoringPreparationResult(val lyricModel: LyricModel, val scorableLineCount: Int)

data class TMEScoringPreparationTimeouts @JvmOverloads constructor(
    val queryMs: Long = 30_000,
    val downloadMs: Long = 60_000,
    val overallMs: Long = 120_000
) {
    init { require(queryMs > 0 && downloadMs > 0 && overallMs > 0) { "Timeouts must be positive" } }
}

/** Called on main. Callers may cancel, close or prepare another song from these callbacks. */
interface TMEScoringPreparationListener {
    fun onStage(stage: TMEScoringPreparationStage) {}
    fun onSuccess(result: TMEScoringPreparationResult) {}
    fun onFailure(error: TMEScoringPreparationError) {}
}
