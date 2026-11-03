package com.iptvup.app.data.parser

import java.text.Normalizer
import java.util.Locale

object EpgFallback {
    const val URL = "https://epgshare01.online/epgshare01/epg_ripper_ES1.xml.gz"

    fun normalize(value: String): String {
        val text = Normalizer.normalize(
            value.lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replace(DIACRITICS, "")
        return text
            .replace(QUALITY, " ")
            .replace(COUNTRY, " ")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
            .replace(WHITESPACE, " ")
    }

    fun syntheticId(name: String): String =
        "es:" + normalize(name).replace(' ', '-')

    private val DIACRITICS = Regex("\\p{M}+")
    private val QUALITY = Regex(
        "\\b(fhd|uhd|4k|hd|sd|hevc|h265|h264|1080p|720p|480p|576p)\\b"
    )
    private val COUNTRY = Regex("\\b(es|esp|spain|espana)\\b")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val WHITESPACE = Regex("\\s+")
}
