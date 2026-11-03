package com.iptvup.app.data.identity

import com.iptvup.app.data.model.ParsedChannel
import java.security.MessageDigest
import java.text.Normalizer

/**
 * Identidad estable de un canal para evitar duplicados entre distintas URLs
 * del mismo proveedor y para que favoritos/ocultos sobrevivan a una resync.
 */
object ChannelIdentity {

    fun dedupeKey(
        name: String,
        groupName: String,
        epgChannelId: String?
    ): String {
        val quality = ChannelQuality.key(name)
        val epg = epgChannelId?.trim()?.lowercase()
        if (!epg.isNullOrBlank() && epg != "null" && epg != "0") {
            return "id:$epg|q:$quality"
        }
        return "name:${normalize(ChannelQuality.baseName(name))}|${normalize(groupName)}|q:$quality"
    }

    fun channelKey(dedupeKey: String): String =
        "ch:" + sha256(dedupeKey).take(20)

    fun uniqueChannelKey(vararg parts: String): String =
        "ch:" + sha256(parts.joinToString("|")).take(24)

    fun qualityScore(channel: ParsedChannel): Int {
        var score = ChannelQuality.score(channel.name)
        if (!channel.epgChannelId.isNullOrBlank()) score += 20
        if (channel.streamUrl.contains(".m3u8", ignoreCase = true)) score += 1
        if (channel.streamUrl.startsWith("https://", ignoreCase = true)) score += 1
        return score
    }

    private fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(
            value.lowercase(),
            Normalizer.Form.NFD
        ).replace(DIACRITICS_REGEX, "")
        return withoutAccents
            .replace(QUALITY_REGEX, " ")
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .trim()
            .replace(WHITESPACE_REGEX, " ")
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private val DIACRITICS_REGEX = Regex("\\p{M}+")
    private val QUALITY_REGEX = Regex(
        "\\b(fhd|uhd|4k|hd|sd|hevc|h265|h264|1080p|720p|480p)\\b"
    )
    private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")
    private val WHITESPACE_REGEX = Regex("\\s+")
}
