package com.iptvup.app.data.parser

import com.iptvup.app.data.identity.ChannelIdentity
import com.iptvup.app.data.identity.ChannelQuality
import com.iptvup.app.data.model.ParsedChannel
import java.io.BufferedReader
import javax.inject.Inject

class M3uParser @Inject constructor() {

    data class M3uParseResult(
        val count: Int,
        val epgUrl: String?
    )

    suspend fun parse(
        reader: BufferedReader,
        onChannel: suspend (ParsedChannel) -> Unit
    ): M3uParseResult {
        var epgUrl: String? = null
        var pendingAttrs: Map<String, String>? = null
        var pendingName: String? = null
        var currentGroup: String? = null
        var count = 0

        while (true) {
            val line = reader.readLine() ?: break
            val trimmed = line.trim().removePrefix("\uFEFF")
            when {
                trimmed.isEmpty() -> Unit

                trimmed.startsWith("#EXTM3U") -> {
                    val attrs = parseAttrs(trimmed)
                    epgUrl = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: epgUrl
                }

                trimmed.startsWith("#EXTINF") -> {
                    val (head, name) = splitExtinf(trimmed)
                    val attrs = parseAttrs(head)
                    pendingAttrs = attrs
                    pendingName = name
                        .ifBlank { attrs["tvg-name"].orEmpty() }
                        .ifBlank { "Canal" }
                    attrs["group-title"]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { currentGroup = it }
                }

                trimmed.startsWith("#EXTGRP:") -> {
                    currentGroup = trimmed.substringAfter(':').trim().ifBlank { null }
                }

                trimmed.startsWith("#") -> Unit

                else -> {
                    val attrs = pendingAttrs
                    val name = pendingName
                    if (attrs != null && name != null) {
                        val url = trimmed
                        val group = (currentGroup ?: attrs["group-title"])
                            .orEmpty()
                            .ifBlank { "Sin grupo" }
                        val epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                        val tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                        val displayName = when {
                            ChannelQuality.label(name) != null -> name
                            tvgName != null && ChannelQuality.label(tvgName) != null -> tvgName
                            else -> name
                        }
                        val dedupeKey = ChannelIdentity.dedupeKey(displayName, group, epgId)
                        onChannel(
                            ParsedChannel(
                                channelKey = ChannelIdentity.channelKey(dedupeKey),
                                dedupeKey = dedupeKey,
                                name = displayName,
                                logoUrl = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                                groupName = group,
                                streamUrl = url,
                                epgChannelId = epgId,
                                sortOrder = count
                            )
                        )
                        count++
                        pendingAttrs = null
                        pendingName = null
                    }
                }
            }
        }

        return M3uParseResult(count, epgUrl)
    }

    private fun splitExtinf(line: String): Pair<String, String> {
        var inQuotes = false
        for (index in line.indices) {
            when (val char = line[index]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    return line.substring(0, index) to line.substring(index + 1).trim()
                }
            }
        }
        return line to ""
    }

    private fun parseAttrs(input: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ATTR_REGEX.findAll(input).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private companion object {
        val ATTR_REGEX = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
    }
}
