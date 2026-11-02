package com.iptvup.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        ChannelEntity::class,
        EpgProgramEntity::class,
        FavoriteEntity::class,
        HiddenItemEntity::class,
        RecentChannelEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun hiddenDao(): HiddenDao
    abstract fun recentDao(): RecentDao
}
