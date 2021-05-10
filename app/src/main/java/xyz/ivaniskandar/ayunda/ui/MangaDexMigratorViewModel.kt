package xyz.ivaniskandar.ayunda.ui

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.room.Room
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.registerTypeAdapter
import com.github.salomonbrys.kotson.registerTypeHierarchyAdapter
import com.github.salomonbrys.kotson.set
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import eu.kanade.tachiyomi.data.backup.legacy.models.DHistory
import eu.kanade.tachiyomi.data.backup.legacy.serializer.CategoryTypeAdapter
import eu.kanade.tachiyomi.data.backup.legacy.serializer.ChapterTypeAdapter
import eu.kanade.tachiyomi.data.backup.legacy.serializer.HistoryTypeAdapter
import eu.kanade.tachiyomi.data.backup.legacy.serializer.MangaTypeAdapter
import eu.kanade.tachiyomi.data.backup.legacy.serializer.TrackTypeAdapter
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import xyz.ivaniskandar.ayunda.db.MangaDexDatabase
import xyz.ivaniskandar.ayunda.util.getDisplayName
import xyz.ivaniskandar.ayunda.util.isUUID
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup as LegacyBackup

class MangaDexMigratorViewModel(app: Application) : AndroidViewModel(app) {
    var status by mutableStateOf(Status.IDLE)
        private set

    var currentManga by mutableStateOf("")
        private set

    var processedCount by mutableStateOf(0)
        private set

    var migratedCount by mutableStateOf(0)
        private set

    var totalDexItems = 0
        private set

    private val _alreadyMigrated = mutableListOf<String>()
    val alreadyMigrated: List<String>
        get() = _alreadyMigrated

    private val _missingMangaId = mutableListOf<String>()
    val missingMangaId: List<String>
        get() = _missingMangaId

    private val _missingChapterId = mutableListOf<String>()
    val missingChapterId: List<String>
        get() = _missingChapterId

    var convertedBackup: Any? by mutableStateOf(null)
        private set

    var originalName: String? = null
        private set

    private val mangaDexDao by lazy {
        val db = Room.databaseBuilder(
            getApplication<Application>(),
            MangaDexDatabase::class.java,
            "mangadex.db"
        ).createFromAsset("mangadex.db").build()
        db.mangaDexDao()
    }

    private val mangaDexSourceIds = arrayOf(
        2499283573021220255, // en
        1145824452519314725, // sv
        1347402746269051958, // my
        1411768577036936240, // ja
        1424273154577029558, // he
        1471784905273036181, // ms
        1493666528525752601, // zh-Hant
        1713554459881080228, // th
        1952071260038453057, // it
        2098905203823335614, // ru
        2655149515337070132, // pt-BR
        3260701926561129943, // el
        3285208643537017688, // ko
        3339599426223341161, // ar
        3578612018159256808, // cs
        3781216447842245147, // fa
        3807502156582598786, // id
        3846770256925560569, // tr
        4150470519566206911, // bn
        425785191804166217,  // da
        4284949320785450865, // hu
        4505830566611664829, // fr
        4710920497926776490, // sh
        4774459486579224459, // ro
        4872213291993424667, // no
        4938773340256184018, // es-419
        5098537545549490547, // de
        5148895169070562838, // zh-Hans
        5189216366882819742, // pt
        5463447640980279236, // bg
        5779037855201976894, // uk
        5860541308324630662, // ca
        5967745367608513818, // mn
        6400665728063187402, // es
        6750440049024086587, // nl
        6840513937945146538, // hi
        737986167355114438,  // lt
        8033579885162383068, // pl
        8254121249433835847, // fi
        8578871918181236609, // fil
        9194073792736219759, // vi
    )

    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun processBackup(uri: Uri?) {
        if (uri == null) return

        try {
            originalName = uri.getDisplayName(getApplication())
            if (originalName == null) throw IllegalStateException("try again plz")

            currentManga = ""
            convertedBackup = null
            processedCount = 0
            migratedCount = 0
            totalDexItems = 0
            _alreadyMigrated.clear()
            _missingMangaId.clear()
            _missingChapterId.clear()

            when {
                originalName!!.endsWith(".proto.gz") -> {
                    processProtoBackup(uri)
                }
                originalName!!.endsWith(".json") -> {
                    processJsonBackup(uri)
                }
                else -> {
                    throw IllegalArgumentException("Invalid file selected")
                }
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(getApplication(), e.message, Toast.LENGTH_SHORT).show()
            }
            status = Status.IDLE
            e.printStackTrace()
        }
    }

    @Throws(IllegalStateException::class)
    private fun processProtoBackup(uri: Uri) {
        status = Status.PREPARING

        val backupString = try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.source()?.gzip()?.buffer()?.use {
                it.readByteArray()
            }
        } catch (e: Exception) {
            null
        } ?: throw IllegalStateException("try again plz")

        val backup = ProtoBuf.decodeFromByteArray(BackupSerializer, backupString)
        val backupMangaList = backup.backupManga.toMutableList()

        // Unfavorited manga will be silently migrated
        totalDexItems = backupMangaList.count { it.favorite && mangaDexSourceIds.contains(it.source) }

        status = Status.PROCESSING

        for (i in backupMangaList.indices) {
            val backupManga = backupMangaList[i].copy()
            if (!mangaDexSourceIds.contains(backupManga.source)) continue

            if (backupManga.favorite) currentManga = backupManga.title
            val oldMangaId = backupManga.url.split("/")[2]
            if (oldMangaId.isUUID()) {
                // don't bother if it's already migrated
                if (backupManga.favorite) {
                    _alreadyMigrated += currentManga
                    incrementProcessedCount()
                }
                continue
            }

            // Migrate manga url
            val newMangaId = mangaDexDao.getNewMangaId(oldMangaId)
            if (newMangaId == null) {
                // new manga id doesn't exist so does the chapters
                if (backupManga.favorite) {
                    _missingMangaId += currentManga
                    incrementProcessedCount()
                }
                continue
            }
            backupManga.url = "/manga/$newMangaId"

            // Migrate chapter url
            var chapterMissing: String? = null
            val chapters = backupManga.chapters.toMutableList()
            for (i2 in chapters.indices) {
                val backupChapter = chapters[i2].copy()
                val newChapterUrl = migrateChapterUrl(backupChapter.url)
                if (newChapterUrl != null) {
                    backupChapter.url = newChapterUrl
                    chapters[i2] = backupChapter
                } else {
                    chapterMissing = backupChapter.name
                    break
                }
            }
            if (chapterMissing != null) {
                // missing chapter is unlikely to happen but just in case
                if (backupManga.favorite) {
                    _missingChapterId += "$currentManga ($chapterMissing)"
                    incrementProcessedCount()
                }
                continue
            }

            // Migrate history url
            val history = backupManga.history.toMutableList()
            for (i2 in history.indices) {
                val backupHistory = history[i2].copy()
                val newChapterUrl = migrateChapterUrl(backupHistory.url)
                if (newChapterUrl != null) {
                    backupHistory.url = newChapterUrl
                    history[i2] = backupHistory
                }
            }

            backupManga.chapters = chapters
            backupManga.history = history
            backupMangaList[i] = backupManga
            if (backupManga.favorite) {
                migratedCount += 1
                incrementProcessedCount()
            }
        }

        status = Status.FINISHING
        convertedBackup = backup.copy(backupManga = backupMangaList)
        status = Status.IDLE
    }

    private fun processJsonBackup(uri: Uri) {
        status = Status.PREPARING
        val reader = JsonReader(getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader())
        val root = JsonParser.parseReader(reader).asJsonObject

        val version = root.get(LegacyBackup.VERSION)?.asInt ?: 1
        if (version != LegacyBackup.CURRENT_VERSION) throw IllegalArgumentException("Unknown backup version")

        val parser = GsonBuilder()
            .registerTypeAdapter<MangaImpl>(MangaTypeAdapter.build())
            .registerTypeHierarchyAdapter<ChapterImpl>(ChapterTypeAdapter.build())
            .registerTypeAdapter<CategoryImpl>(CategoryTypeAdapter.build())
            .registerTypeAdapter<DHistory>(HistoryTypeAdapter.build())
            .registerTypeHierarchyAdapter<TrackImpl>(TrackTypeAdapter.build())
            .create()

        val mangaArray = root.get(LegacyBackup.MANGAS).asJsonArray
        val mangaList = mangaArray.map {
            val mangaJsonObject = it.deepCopy().asJsonObject
            val manga = parser.fromJson<MangaImpl>(mangaJsonObject.get(LegacyBackup.MANGA))
            val chapters = parser
                .fromJson<List<ChapterImpl>>(mangaJsonObject.get(LegacyBackup.CHAPTERS) ?: JsonArray())
                .toMutableList()
            val history = parser
                .fromJson<List<DHistory>>(mangaJsonObject.get(LegacyBackup.HISTORY) ?: JsonArray())
                .toMutableList()
            Triple(manga, chapters, history)
        }

        totalDexItems = mangaList.count { mangaDexSourceIds.contains(it.first.source) }

        status = Status.PROCESSING

        for (i in mangaList.indices) {
            val (manga, chapters, history) = mangaList[i]
            if (!mangaDexSourceIds.contains(manga.source)) continue

            currentManga = manga.title
            val oldMangaId = manga.url.split("/")[2]
            if (oldMangaId.isUUID()) {
                // don't bother if it's already migrated
                _alreadyMigrated += currentManga
                incrementProcessedCount()
                continue
            }

            // Migrate manga url
            val newMangaId = mangaDexDao.getNewMangaId(oldMangaId)
            if (newMangaId == null) {
                // new manga id doesn't exist so does the chapters
                _missingMangaId += currentManga
                incrementProcessedCount()
                continue
            }
            manga.url = "/manga/$newMangaId"

            // Migrate chapter url
            var chapterMissing: String? = null
            for (i2 in chapters.indices) {
                val backupChapter = chapters[i2]
                val newChapterUrl = migrateChapterUrl(backupChapter.url)
                if (newChapterUrl != null) {
                    backupChapter.url = newChapterUrl
                    chapters[i2] = backupChapter
                } else {
                    chapterMissing = backupChapter.name
                    break
                }
            }
            if (chapterMissing != null) {
                // missing chapter is unlikely to happen but just in case
                _missingChapterId += "$currentManga ($chapterMissing)"
                incrementProcessedCount()
                continue
            }

            // Migrate history url
            for (i2 in history.indices) {
                val backupHistory = history[i2].copy()
                val newChapterUrl = migrateChapterUrl(backupHistory.url)
                if (newChapterUrl != null) {
                    history[i2] = backupHistory.copy(url = newChapterUrl)
                }
            }

            val mangaJsonObject = mangaArray[i].asJsonObject
            mangaJsonObject[LegacyBackup.MANGA] = parser.toJsonTree(manga)
            mangaJsonObject[LegacyBackup.CHAPTERS] = parser.toJsonTree(chapters)
            mangaJsonObject[LegacyBackup.HISTORY] = parser.toJsonTree(history)
            mangaArray[i] = mangaJsonObject
            migratedCount += 1
            incrementProcessedCount()
        }

        status = Status.FINISHING
        root[LegacyBackup.MANGAS] = mangaArray
        convertedBackup = parser.toJson(root)
        status = Status.IDLE
    }

    private fun migrateChapterUrl(url: String): String? {
        if (url.startsWith("/api/")) {
            val oldChapterId = url.split("/")[3]
            val newChapterId = mangaDexDao.getNewChapterId(oldChapterId)
            if (newChapterId != null) {
                return "/chapter/$newChapterId"
            }
        }
        return null
    }

    private fun incrementProcessedCount() {
        processedCount += 1
        if (processedCount == totalDexItems) {
            status = Status.FINISHING
        }
    }

    enum class Status {
        IDLE, PREPARING, PROCESSING, FINISHING
    }
}
