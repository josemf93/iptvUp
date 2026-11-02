package com.iptvup.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE profiles SET lastSyncedAt = :at WHERE id = :id")
    suspend fun updateLastSynced(id: String, at: Long)

    @Query("UPDATE profiles SET epgUrl = :epgUrl WHERE id = :id")
    suspend fun updateEpgUrl(id: String, epgUrl: String?)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE profileId = :profileId ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(profileId: String): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupName FROM channels WHERE profileId = :profileId ORDER BY groupName COLLATE NOCASE")
    fun observeGroups(profileId: String): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE profileId = :profileId AND groupName = :groupName " +
            "ORDER BY sortOrder, name COLLATE NOCASE"
    )
    suspend fun getForGroup(profileId: String, groupName: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE profileId = :profileId AND channelKey = :channelKey LIMIT 1")
    suspend fun getByKey(profileId: String, channelKey: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)

    @Query("SELECT COUNT(*) FROM channels WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int

    @Query(
        "SELECT DISTINCT epgChannelId FROM channels " +
            "WHERE profileId = :profileId AND epgChannelId IS NOT NULL AND epgChannelId != ''"
    )
    suspend fun epgChannelIds(profileId: String): List<String>

    @Query(
        "SELECT * FROM channels WHERE profileId = :profileId " +
            "AND name LIKE '%' || :query || '%' COLLATE NOCASE " +
            "ORDER BY name COLLATE NOCASE LIMIT 300"
    )
    suspend fun search(profileId: String, query: String): List<ChannelEntity>
}

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)

    @Query("DELETE FROM epg_programs WHERE profileId = :profileId AND endMs < :before")
    suspend fun deleteBefore(profileId: String, before: Long)

    @Query(
        "SELECT * FROM epg_programs WHERE profileId = :profileId " +
            "AND endMs >= :from AND startMs <= :to ORDER BY startMs"
    )
    suspend fun getWindow(profileId: String, from: Long, to: Long): List<EpgProgramEntity>

    @Query(
        "SELECT * FROM epg_programs WHERE profileId = :profileId AND epgChannelId = :epgId " +
            "AND startMs <= :at AND endMs > :at ORDER BY startMs DESC LIMIT 1"
    )
    suspend fun getNow(profileId: String, epgId: String, at: Long): EpgProgramEntity?

    @Query(
        "SELECT * FROM epg_programs WHERE profileId = :profileId AND epgChannelId = :epgId " +
            "AND startMs > :at ORDER BY startMs LIMIT 1"
    )
    suspend fun getNext(profileId: String, epgId: String, at: Long): EpgProgramEntity?

    @Query(
        "SELECT * FROM epg_programs WHERE profileId = :profileId AND epgChannelId = :epgId " +
            "AND endMs >= :from AND startMs <= :to ORDER BY startMs"
    )
    suspend fun getForChannel(
        profileId: String,
        epgId: String,
        from: Long,
        to: Long
    ): List<EpgProgramEntity>
}

@Dao
interface RecentDao {
    @Query(
        "SELECT * FROM recent_channels WHERE profileId = :profileId " +
            "ORDER BY openedAt DESC LIMIT 10"
    )
    fun observe(profileId: String): Flow<List<RecentChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentChannelEntity)

    @Query(
        "DELETE FROM recent_channels WHERE profileId = :profileId " +
            "AND id NOT IN (" +
            "SELECT id FROM recent_channels WHERE profileId = :profileId " +
            "ORDER BY openedAt DESC LIMIT 10)"
    )
    suspend fun trim(profileId: String)

    @Query("DELETE FROM recent_channels WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT channelKey FROM favorites WHERE profileId = :profileId")
    fun observeKeys(profileId: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND channelKey = :channelKey)")
    suspend fun exists(profileId: String, channelKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND channelKey = :channelKey")
    suspend fun delete(profileId: String, channelKey: String)

    @Query("DELETE FROM favorites WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)
}

@Dao
interface HiddenDao {
    @Query("SELECT * FROM hidden_items WHERE profileId = :profileId")
    fun observeAll(profileId: String): Flow<List<HiddenItemEntity>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM hidden_items " +
            "WHERE profileId = :profileId AND kind = :kind AND itemKey = :itemKey)"
    )
    suspend fun exists(profileId: String, kind: String, itemKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HiddenItemEntity)

    @Query("DELETE FROM hidden_items WHERE profileId = :profileId AND kind = :kind AND itemKey = :itemKey")
    suspend fun delete(profileId: String, kind: String, itemKey: String)

    @Query("DELETE FROM hidden_items WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)
}
