package xyz.ivaniskandar.ayunda.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga")
data class Manga(
    @PrimaryKey @ColumnInfo(name = "legacy_id") val legacyId: Int,
    @ColumnInfo(name = "new_id") val newId: String
)
