package xyz.ivaniskandar.ayunda.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@Serializable
data class Backup(
    val version: Int = 1,
    val mangas: List<Manga>,
    @JsonNames ("categories") private val _categories: List<JsonArray>,
    val extensions: List<String>
) {
    val categories: Category
        get() = Category(
            _categories[0].jsonPrimitive.content,
            _categories[1].jsonPrimitive.int
        )
}

@Serializable
data class Manga(
    @JsonNames("manga") private val _data: JsonArray,
    val chapters: List<MangaChapter> = emptyList(),
    val track: List<MangaTrack> = emptyList(),
    @JsonNames("history") private val _history: List<JsonArray> = emptyList()
) {
    val data: MangaData
        get() = MangaData(
            _data[0].jsonPrimitive.content,
            _data[1].jsonPrimitive.content,
            _data[2].jsonPrimitive.long,
            _data[3].jsonPrimitive.int,
            _data[4].jsonPrimitive.int
        )

    val history: List<MangaHistory>
        get() = _history.map {
            MangaHistory(
                it[0].jsonPrimitive.content,
                it[1].jsonPrimitive.long
            )
        }

    fun copyWith(
        newData: MangaData = data,
        newChapters: List<MangaChapter> = chapters,
        newTrack: List<MangaTrack> = track,
        newHistory: List<MangaHistory> = history
    ): Manga {
        val dataJsonArray = buildJsonArray {
            add(newData.url)
            add(newData.title)
            add(newData.source)
            add(newData.viewer_flags)
            add(newData.chapter_flags)
        }
        val historyJsonArrayList = newHistory.map {
            buildJsonArray {
                add(it.url)
                add(it.lastRead)
            }
        }
        return copy(
            _data = dataJsonArray,
            chapters = newChapters,
            track = newTrack,
            _history = historyJsonArrayList
        )
    }
}

@Serializable
data class MangaChapter(
    @JsonNames("u") var url: String,
    @JsonNames("r") val read: Int = 0,
    @JsonNames("b") val bookmark: Int = 0,
    @JsonNames("l") val lastRead: Int = 0
)

@Serializable
data class MangaTrack(
    @JsonNames("t") val title: String,
    @JsonNames("s") val sync: Int,
    @JsonNames("r") val media: Int,
    @JsonNames("ml") val library: Long,
    @JsonNames("l") val lastRead: Int,
    @JsonNames("u") val trackingUrl: String
)

data class Category(
    val name: String,
    val order: Int
)

data class MangaHistory(
    var url: String,
    val lastRead: Long
)

data class MangaData(
    var url: String,
    val title: String,
    val source: Long,
    val viewer_flags: Int,
    val chapter_flags: Int
)
