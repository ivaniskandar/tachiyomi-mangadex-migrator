package xyz.ivaniskandar.ayunda.model

import com.soywiz.krypto.encoding.toBase64
import xyz.ivaniskandar.ayunda.MangaDexMigrator
import xyz.ivaniskandar.ayunda.TachiBackup
import eu.kanade.tachiyomi.data.backup.full.models.Backup as FullBackup
import xyz.ivaniskandar.ayunda.model.Backup as LegacyBackup

data class ProtoMigrationResult(
    val backup: FullBackup,
    override val fileName: String,
    override val alreadyMigrated: List<String>,
    override val missingMangaId: List<String>,
    override val missingChapterId: List<String>
) : MigrationResult() {
    override val fileBits = TachiBackup.encodeProto(backup)
    override val content = fileBits.toBase64()
    override val mimeType = "application/gzip"

    override val totalDexManga = backup.backupManga.count {
        it.favorite && MangaDexMigrator.tachiyomiSourceIds.contains(it.source)
    }
}

data class JsonMigrationResult(
    val backup: LegacyBackup,
    override val fileName: String,
    override val alreadyMigrated: List<String>,
    override val missingMangaId: List<String>,
    override val missingChapterId: List<String>
) : MigrationResult() {
    override val fileBits = TachiBackup.encodeJson(backup)
    override val content = fileBits.decodeToString()
    override val mimeType = "application/json"

    override val totalDexManga = backup.mangas.count { MangaDexMigrator.tachiyomiSourceIds.contains(it.data.source) }
}

abstract class MigrationResult {
    abstract val fileName: String
    abstract val fileBits: ByteArray
    abstract val content: String
    abstract val mimeType: String

    abstract val totalDexManga: Int

    val totalMigratedManga: Int
        get() = totalDexManga - alreadyMigrated.size - missingMangaId.size - missingChapterId.size

    //
    // Lists of titles
    //
    abstract val alreadyMigrated: List<String>
    abstract val missingMangaId: List<String>
    abstract val missingChapterId: List<String>
}
