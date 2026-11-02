package com.iptvup.app.data.model

enum class ProfileKind(val wire: String) {
    M3U("m3u"),
    XTREAM("xtream");

    companion object {
        fun fromWire(value: String?): ProfileKind =
            if (value.equals("xtream", ignoreCase = true)) XTREAM else M3U
    }
}

data class IptvProfile(
    val id: String,
    val name: String,
    val kind: ProfileKind,
    val epgUrl: String?,
    val xtreamHost: String?,
    val xtreamUsername: String?,
    val updatedAt: Long,
    val lastSyncedAt: Long?
)

data class ProfileCredentials(
    val m3uUrl: String? = null,
    val xtreamHost: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val epgUrl: String? = null
)

data class ParsedChannel(
    val channelKey: String,
    val dedupeKey: String,
    val name: String,
    val logoUrl: String?,
    val groupName: String,
    val streamUrl: String,
    val epgChannelId: String?,
    val streamId: String? = null,
    val sortOrder: Int = 0
)

data class ParsedProgram(
    val epgChannelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String?
)

data class EpgEntry(
    val now: ParsedProgram? = null,
    val next: ParsedProgram? = null
)
