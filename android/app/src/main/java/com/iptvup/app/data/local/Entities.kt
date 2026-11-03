package com.iptvup.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.data.model.ParsedChannel
import com.iptvup.app.data.model.ParsedProgram
import com.iptvup.app.data.model.ProfileKind

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val epgUrl: String?,
    val xtreamHost: String?,
    val xtreamUsername: String?,
    val updatedAt: Long,
    val lastSyncedAt: Long?
)

@Entity(
    tableName = "channels",
    primaryKeys = ["id"],
    indices = [
        Index("profileId"),
        Index("epgChannelId"),
        Index("groupName"),
        Index("name")
    ]
)
data class ChannelEntity(
    val id: String,
    val profileId: String,
    val channelKey: String,
    val name: String,
    val logoUrl: String?,
    val groupName: String,
    val streamUrl: String,
    val streamId: String?,
    val epgChannelId: String?,
    val sortOrder: Int
)

@Entity(
    tableName = "epg_programs",
    primaryKeys = ["id"],
    indices = [
        Index("profileId"),
        Index("epgChannelId"),
        Index("startMs")
    ]
)
data class EpgProgramEntity(
    val id: String,
    val profileId: String,
    val epgChannelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String?
)

@Entity(
    tableName = "recent_channels",
    primaryKeys = ["id"],
    indices = [Index("profileId"), Index("openedAt")]
)
data class RecentChannelEntity(
    val id: String,
    val profileId: String,
    val channelKey: String,
    val name: String,
    val logoUrl: String?,
    val groupName: String,
    val openedAt: Long
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["id"],
    indices = [Index("profileId")]
)
data class FavoriteEntity(
    val id: String,
    val profileId: String,
    val channelKey: String,
    val createdAt: Long
)

@Entity(
    tableName = "hidden_items",
    primaryKeys = ["id"],
    indices = [Index("profileId")]
)
data class HiddenItemEntity(
    val id: String,
    val profileId: String,
    val kind: String,
    val itemKey: String,
    val createdAt: Long
)

fun ProfileEntity.toModel(): IptvProfile = IptvProfile(
    id = id,
    name = name,
    kind = ProfileKind.fromWire(kind),
    epgUrl = epgUrl,
    xtreamHost = xtreamHost,
    xtreamUsername = xtreamUsername,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt
)

fun IptvProfile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    name = name,
    kind = kind.wire,
    epgUrl = epgUrl,
    xtreamHost = xtreamHost,
    xtreamUsername = xtreamUsername,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt
)

fun ParsedChannel.toEntity(profileId: String): ChannelEntity = ChannelEntity(
    id = "$profileId:$channelKey",
    profileId = profileId,
    channelKey = channelKey,
    name = name,
    logoUrl = logoUrl,
    groupName = groupName,
    streamUrl = streamUrl,
    streamId = streamId,
    epgChannelId = epgChannelId,
    sortOrder = sortOrder
)

fun ParsedProgram.toEntity(profileId: String): EpgProgramEntity = EpgProgramEntity(
    id = "$profileId:$epgChannelId:$startMs",
    profileId = profileId,
    epgChannelId = epgChannelId,
    startMs = startMs,
    endMs = endMs,
    title = title,
    description = description
)

fun EpgProgramEntity.toParsedProgram(): ParsedProgram = ParsedProgram(
    epgChannelId = epgChannelId,
    startMs = startMs,
    endMs = endMs,
    title = title,
    description = description
)

/**
 * Elimina programas duplicados o solapados del mismo canal. Se queda con el
 * primero de cada hora de inicio y descarta los que empiezan antes de que
 * termine el anterior.
 */
fun List<EpgProgramEntity>.dedupePrograms(): List<EpgProgramEntity> {
    val result = ArrayList<EpgProgramEntity>(size)
    val starts = HashSet<Long>()
    sortedBy { it.startMs }.forEach { program ->
        val overlaps = result.isNotEmpty() && program.startMs < result.last().endMs
        if (!overlaps && starts.add(program.startMs)) {
            result += program
        }
    }
    return result
}
