package com.iptvup.app.data.identity

import java.text.Normalizer

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

    /**
     * Prioridad para elegir el canal de un evento deportivo:
     * 8K/4K > 1080/FHD > 720/HD > SD > sin calidad conocida.
     */
    fun sportsRank(name: String): Int = when (detect(name)) {
        "8K" -> 0
        "4K" -> 4
        "FHD" -> 3
        "HD" -> 2
        "SD" -> 1
        else -> 0
    }

    fun isJunkChannel(name: String): Boolean =
        name.trimStart().startsWith("##")

    fun baseName(name: String): String = name
        .replace(QUALITY_IN_NAME_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private fun detect(name: String): String? {
        val value = Normalizer.normalize(name, Normalizer.Form.NFKC)
            .replace(INVISIBLE_REGEX, "")
            .uppercase()
        return when {
            RE_8K.containsMatchIn(value) -> "8K"
            RE_4K.containsMatchIn(value) -> "4K"
            RE_FHD.containsMatchIn(value) -> "FHD"
            RE_HD.containsMatchIn(value) -> "HD"
            RE_SD.containsMatchIn(value) -> "SD"
            else -> null
        }
    }

    private const val UNKNOWN = "UNKNOWN"
    private val RE_8K = Regex("\\b8K\\b|\\b4320P?\\b")
    private val RE_4K = Regex("\\b4K\\b|\\bUHD\\b|\\b2160P?\\b")
    private val RE_FHD = Regex("\\bFHD\\b|[A-Z]{3,}FHD$|\\bFULL\\s*HD\\b|\\b1080P?\\b")
    private val RE_HD = Regex("\\bHD\\b|[A-Z]{3,}HD$|\\b720P?\\b")
    private val INVISIBLE_REGEX = Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]")
    private val RE_SD = Regex("\\bSD\\b|\\b480P?\\b|\\b576P?\\b")
    private val QUALITY_IN_NAME_REGEX = Regex(
        "(?i)\\b(8K|4K|UHD|FHD|FULL\\s*HD|1080P?|720P?|480P?|576P?|SD|HD|HEVC|H265|H264)\\b"
    )
    private val WHITESPACE_REGEX = Regex("\\s+")
}
