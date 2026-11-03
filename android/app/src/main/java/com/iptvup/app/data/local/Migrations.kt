package com.iptvup.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_channels_profileId_groupName` " +
                "ON `channels` (`profileId`, `groupName`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_epg_programs_profileId_epgChannelId_startMs` " +
                "ON `epg_programs` (`profileId`, `epgChannelId`, `startMs`)"
        )
    }
}
