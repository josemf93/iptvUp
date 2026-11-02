package com.iptvup.app.data.identity

object ChannelQuality {

    fun label(name: String): String? = detect(name)

    fun key(name: String): String = detect(name) ?: UNKNOWN

    fun score(name: String): Int = when (detect(name)) {
        "8K" -> 16
        "4K" -> 12
        "FHD" -> 8
        "HD" -> 4
        "SD" -> -2
        else -> 0
    }

    fun baseName(name: String): String = name
        .replace(QUALITY_IN_NAME_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private fun detect(name: String): String? {
        val value = name.uppercase()
        return when {
            Regex("\\b8K\\b|\\b4320P?\\b").containsMatchIn(value) -> "8K"
            Regex("\\b4K\\b|\\bUHD\\b|\\b2160P?\\b").containsMatchIn(value) -> "4K"
            Regex("\\bFHD\\b|\\bFULL\\s*HD\\b|\\b1080P?\\b").containsMatchIn(value) -> "FHD"
            Regex("\\bHD\\b|\\b720P?\\b").containsMatchIn(value) -> "HD"
            Regex("\\bSD\\b|\\b480P?\\b|\\b576P?\\b").containsMatchIn(value) -> "SD"
            else -> null
        }
    }

    private const val UNKNOWN = "UNKNOWN"
    private val QUALITY_IN_NAME_REGEX = Regex(
        "(?i)\\b(8K|4K|UHD|FHD|FULL\\s*HD|1080P?|720P?|480P?|576P?|SD|HD|HEVC|H265|H264)\\b"
    )
    private val WHITESPACE_REGEX = Regex("\\s+")
}
