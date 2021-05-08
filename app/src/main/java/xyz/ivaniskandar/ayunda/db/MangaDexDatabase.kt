package xyz.ivaniskandar.ayunda.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Chapter::class, Manga::class], version = 1)
abstract class MangaDexDatabase : RoomDatabase() {
    abstract fun mangaDexDao(): MangaDexDao
}
