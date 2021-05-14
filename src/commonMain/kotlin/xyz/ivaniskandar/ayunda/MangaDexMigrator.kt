package xyz.ivaniskandar.ayunda

import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import xyz.ivaniskandar.ayunda.model.JsonMigrationResult
import xyz.ivaniskandar.ayunda.model.MappingRequest
import xyz.ivaniskandar.ayunda.model.MappingResult
import xyz.ivaniskandar.ayunda.model.ProtoMigrationResult

object MangaDexMigrator {
    private val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    private const val MAPPING_API_PATH = "https://salty-thicket-57859.herokuapp.com/https://api.mangadex.org/legacy/mapping"

    // Good Things Come to Those Who Wait
    private const val PROCESS_DELAY_MS = 50L
    private const val FINISHING_DELAY_MS = 1000L

    val tachiyomiSourceIds = arrayOf(
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

    suspend fun processProtoBackup(
        base64: String,
        fileName: String,
        onProgressUpdate: (String) -> Unit
    ): ProtoMigrationResult {
        onProgressUpdate("Preparing data")

        val backup = TachiBackup.decodeProto(base64)
        val backupMangaList = backup.backupManga.toMutableList()

        var mangaIdMap = mutableMapOf<Int, String?>()
        var chapterIdMap = mutableMapOf<Int, String?>()

        // Collect ids to migrate
        for (i in backupMangaList.indices) {
            val backupManga = backupMangaList[i]
            if (!tachiyomiSourceIds.contains(backupManga.source)) continue

            if (backupManga.favorite) {
                onProgressUpdate(backupManga.title)
                delay(PROCESS_DELAY_MS)
            }

            val oldMangaId = backupManga.url.split("/")[2]
            if (oldMangaId.isUUID()) {
                // don't bother if it's already migrated
                continue
            }
            mangaIdMap[oldMangaId.toInt()] = null

            val chapters = backupManga.chapters
            for (i2 in chapters.indices) {
                val backupChapter = chapters[i2]
                if (backupChapter.url.startsWith("/chapter/")) {
                    // Somehow already migrated
                    continue
                }
                val oldChapterId = backupChapter.url.split("/")[3].toInt()
                chapterIdMap[oldChapterId] = null
            }

            val history = backupManga.history
            for (i2 in history.indices) {
                val backupHistory = history[i2]
                if (backupHistory.url.startsWith("/chapter/")) {
                    // Somehow already migrated
                    continue
                }
                val oldChapterId = backupHistory.url.split("/")[3].toInt()
                chapterIdMap[oldChapterId] = null
            }
        }

        onProgressUpdate("Retrieving new manga IDs")
        mangaIdMap = convertMangaMapping(mangaIdMap) {
            onProgressUpdate("Retrieving new manga IDs ${it.toPercentageString()}")
        } ?: throw IllegalStateException("try again plz")
        onProgressUpdate("Retrieving new chapter IDs")
        chapterIdMap = convertChapterMapping(chapterIdMap) {
            onProgressUpdate("Retrieving new chapter IDs ${it.toPercentageString()}")
        } ?: throw IllegalStateException("try again plz")

        onProgressUpdate("Finishing migration")

        // Final migration process
        // Unfavorited manga will be silently migrated (won't be added to title lists below)
        val alreadyMigrated = mutableListOf<String>()
        val missingMangaId = mutableListOf<String>()
        val missingChapterId = mutableListOf<String>()
        for (i in backupMangaList.indices) {
            val backupManga = backupMangaList[i].copy()
            if (!tachiyomiSourceIds.contains(backupManga.source)) continue

            val oldMangaId = backupManga.url.split("/")[2]
            if (oldMangaId.isUUID()) {
                // don't bother if it's already migrated
                if (backupManga.favorite) {
                    alreadyMigrated += backupManga.title
                }
                continue
            }
            val newMangaId = mangaIdMap[oldMangaId.toInt()]
            if (newMangaId == null) {
                if (backupManga.favorite) {
                    missingMangaId += backupManga.title
                }
                continue
            }
            backupManga.url = "/manga/${newMangaId}"

            var chapterMissing: String? = null
            val chapters = backupManga.chapters.toMutableList()
            for (i2 in chapters.indices) {
                val backupChapter = chapters[i2].copy()
                if (backupChapter.url.startsWith("/chapter/")) {
                    // Somehow already migrated
                    continue
                }
                val oldChapterId = backupChapter.url.split("/")[3].toInt()
                val newChapterId = chapterIdMap[oldChapterId]
                if (newChapterId != null) {
                    backupChapter.url = "/chapter/$newChapterId"
                    chapters[i2] = backupChapter
                } else {
                    chapterMissing = backupChapter.name
                    break
                }
                chapterIdMap[oldChapterId] = null
            }
            if (chapterMissing != null) {
                // missing chapter is unlikely to happen but just in case
                if (backupManga.favorite) {
                    missingChapterId += "${backupManga.title} ($chapterMissing)"
                }
                continue
            }

            val history = backupManga.history.toMutableList()
            for (i2 in history.indices) {
                val backupHistory = history[i2]
                if (backupHistory.url.startsWith("/chapter/")) {
                    // Somehow already migrated
                    continue
                }
                val oldChapterId = backupHistory.url.split("/")[3].toInt()
                val newChapterId = chapterIdMap[oldChapterId]
                if (newChapterId != null) {
                    backupHistory.url = "/chapter/$newChapterId"
                    history[i2] = backupHistory
                }
            }

            backupManga.chapters = chapters
            backupManga.history = history
            backupMangaList[i] = backupManga
        }

        delay(FINISHING_DELAY_MS)
        return ProtoMigrationResult(
            backup.copy(backupManga = backupMangaList),
            fileName.replace(".proto.gz", "_modified.proto.gz"),
            alreadyMigrated,
            missingMangaId,
            missingChapterId
        )
    }

    suspend fun processJsonBackup(
        string: String,
        fileName: String,
        onProgressUpdate: (String) -> Unit
    ): JsonMigrationResult {
        onProgressUpdate("Preparing data")

        val backup = TachiBackup.decodeJson(string)
        if (backup.version != 2) throw IllegalArgumentException("Unknown backup version")

        val backupMangaList = backup.mangas.toMutableList()

        var mangaIdMap = mutableMapOf<Int, String?>()
        var chapterIdMap = mutableMapOf<Int, String?>()

        // Collect ids to migrate
        for (i in backupMangaList.indices) {
            val backupManga = backupMangaList[i]
            if (!tachiyomiSourceIds.contains(backupManga.data.source)) continue

            onProgressUpdate(backupManga.data.title)
            delay(PROCESS_DELAY_MS)

            val oldMangaId = backupManga.data.url.split("/")[2]
            if (oldMangaId.isUUID()) {
                // don't bother if it's already migrated
                continue
            }
            mangaIdMap[oldMangaId.toInt()] = null

            val chapters = backupManga.chapters
            for (i2 in chapters.indices) {
                val backupChapter = chapters[i2]
                if (backupChapter.url.startsWith("/chapter/")) {
                    // Somehow already migrated
                    continue
                }
                val oldChapterId = backupChapter.url.split("/")[3].toInt()
                chapterIdMap[oldChapterId] = null
            }

            val history = backupManga.history
            for (i2 in history.indices) {
                val backupHistory = history[i2]
                if (backupHistory.url.startsWith("/chapter/")) {
                    // Somehow already migrated
                    continue
                }
                val oldChapterId = backupHistory.url.split("/")[3].toInt()
                chapterIdMap[oldChapterId] = null
            }
        }

        onProgressUpdate("Retrieving new manga IDs")
        mangaIdMap = convertMangaMapping(mangaIdMap) {
            onProgressUpdate("Retrieving new manga IDs ${it.toPercentageString()}")
        } ?: throw IllegalStateException("try again plz")
        onProgressUpdate("Retrieving new chapter IDs")
        chapterIdMap = convertChapterMapping(chapterIdMap) {
            onProgressUpdate("Retrieving new chapter IDs ${it.toPercentageString()}")
        } ?: throw IllegalStateException("try again plz")

        onProgressUpdate("Finishing migration")

        // Final migration process
        val alreadyMigrated = mutableListOf<String>()
        val missingMangaId = mutableListOf<String>()
        val missingChapterId = mutableListOf<String>()
        for (i in backupMangaList.indices) {
            val backupManga = backupMangaList[i]
            val backupMangaData = backupManga.data
            if (!tachiyomiSourceIds.contains(backupManga.data.source)) continue

            val oldMangaId = backupMangaData.url.split("/")[2]
            if (oldMangaId.isUUID()) {
                // don't bother if it's already migrated
                alreadyMigrated += backupMangaData.title
                continue
            }
            val newMangaId = mangaIdMap[oldMangaId.toInt()]
            if (newMangaId == null) {
                missingMangaId += backupMangaData.title
                continue
            }
            backupMangaData.url = "/manga/${newMangaId}"

            var chapterMissing = false
            val chapters = backupManga.chapters.toMutableList()
            for (i2 in chapters.indices) {
                val backupChapter = chapters[i2].copy()
                if (backupChapter.url.startsWith("/chapter/")) {
                    // Somehow already migrated
                    continue
                }
                val oldChapterId = backupChapter.url.split("/")[3].toInt()
                val newChapterId = chapterIdMap[oldChapterId]
                if (newChapterId != null) {
                    backupChapter.url = "/chapter/$newChapterId"
                    chapters[i2] = backupChapter
                } else {
                    chapterMissing = true
                    break
                }
                chapterIdMap[oldChapterId] = null
            }
            if (chapterMissing) {
                // missing chapter is unlikely to happen but just in case
                missingChapterId += backupMangaData.title
                continue
            }

            val history = backupManga.history.toMutableList()
            for (i2 in history.indices) {
                val backupHistory = history[i2]
                if (backupHistory.url.startsWith("/chapter/")) {
                    // Somehow already migrated
                    continue
                }
                val oldChapterId = backupHistory.url.split("/")[3].toInt()
                val newChapterId = chapterIdMap[oldChapterId]
                if (newChapterId != null) {
                    backupHistory.url = "/chapter/$newChapterId"
                    history[i2] = backupHistory
                }
            }

            backupMangaList[i] = backupManga.copyWith(
                newData = backupMangaData,
                newChapters = chapters,
                newHistory = history
            )
        }

        delay(FINISHING_DELAY_MS)
        return JsonMigrationResult(
            backup.copy(mangas = backupMangaList),
            fileName.replace(".json", "_modified.json"),
            alreadyMigrated,
            missingMangaId,
            missingChapterId
        )
    }

    private suspend fun convertMangaMapping(
        map: Map<Int, String?>,
        onProgressUpdate: (Float) -> Unit = {}
    ) = convertMapping(IdType.MANGA, map, onProgressUpdate)

    private suspend fun convertChapterMapping(
        map: Map<Int, String?>,
        onProgressUpdate: (Float) -> Unit = {}
    ) = convertMapping(IdType.CHAPTER, map, onProgressUpdate)

    private suspend fun convertMapping(
        type: IdType,
        map: Map<Int, String?>,
        onProgressUpdate: (Float) -> Unit = {}
    ): MutableMap<Int, String?>? {
        val mutableMap = map.toMutableMap()

        // Migrate in chunks of 1000 ids to avoid 10k body limit
        val keyChunks = map.keys.chunked(1000)
        keyChunks.forEachIndexed { index, ids ->
            val results = try {
                client.post<List<MappingResult>>(MAPPING_API_PATH) {
                    contentType(ContentType.Application.Json)
                    body = MappingRequest(type.toString().toLowerCase(), ids)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Don't continue if one of the batch is failed
                return null
            }
            results.forEach { result ->
                mutableMap[result.data.attributes.legacyId] = result.data.attributes.newId
            }

            val progress = (index + 1) / keyChunks.size.toFloat()
            onProgressUpdate(progress)

            // "Rate limit"
            delay(200)
        }

        return mutableMap
    }

    private enum class IdType {
        MANGA, CHAPTER
    }
}
