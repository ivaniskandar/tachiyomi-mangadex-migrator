package xyz.ivaniskandar.ayunda.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface MangaDexDao {
    @Query("SELECT new_id FROM chapter WHERE legacy_id = :legacyId LIMIT 1")
    fun getNewChapterId(legacyId: String): String?

    @Query("SELECT new_id FROM manga WHERE legacy_id = :legacyId LIMIT 1")
    fun getNewMangaId(legacyId: String): String?
}
