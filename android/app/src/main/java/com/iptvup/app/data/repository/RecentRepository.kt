package com.iptvup.app.data.repository

import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.RecentChannelEntity
import com.iptvup.app.data.local.RecentDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentRepository @Inject constructor(
    private val dao: RecentDao
) {

    fun observe(profileId: String): Flow<List<RecentChannelEntity>> =
        dao.observe(profileId)

    suspend fun record(channel: ChannelEntity) {
        dao.upsert(
            RecentChannelEntity(
                id = channel.id,
                profileId = channel.profileId,
                channelKey = channel.channelKey,
                name = channel.name,
                logoUrl = channel.logoUrl,
                groupName = channel.groupName,
                openedAt = System.currentTimeMillis()
            )
        )
        dao.trim(channel.profileId)
    }
}
