package xyz.ivaniskandar.ayunda

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
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
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import okio.source
import xyz.ivaniskandar.ayunda.db.MangaDexDatabase
import eu.kanade.tachiyomi.data.backup.full.models.Backup as FullBackup
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup as LegacyBackup

class MangaDexMigratorActivity : AppCompatActivity() {

    private val viewModel by viewModels<MangaDexMigratorViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AyundaTheme {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            color = MaterialTheme.colors.onBackground,
                            style = MaterialTheme.typography.h5
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        Card(elevation = 16.dp) {
                            when (viewModel.status) {
                                Status.IDLE -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val selectBackup = rememberLauncherForActivityResult(contract = OpenDocument()) {
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                try {
                                                    viewModel.processBackup(it)
                                                } catch (e: Exception) {
                                                    runOnUiThread {
                                                        Toast.makeText(
                                                            this@MangaDexMigratorActivity,
                                                            e.message,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = "How to migrate your MangaDex manga:",
                                            modifier = Modifier.padding(bottom = 4.dp),
                                            style = MaterialTheme.typography.subtitle2
                                        )
                                        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.body2) {
                                            Text(text = "1. Make a backup of your Tachiyomi library")
                                            Text(text = "2. Delete all MangaDex manga from your library")
                                            CompositionLocalProvider(
                                                LocalTextStyle provides MaterialTheme.typography.caption,
                                                LocalContentAlpha provides ContentAlpha.medium
                                            ) {
                                                Text(text = "• You can see all your MangaDex manga in your library by typing \"mangadex\" in the search bar")
                                                Text(text = "• Do not check \"Downloaded chapters\" if you'd like to keep your downloaded chapters")
                                            }
                                            Text(text = "3. Import your backup to start the migration process")
                                            CompositionLocalProvider(
                                                LocalTextStyle provides MaterialTheme.typography.caption,
                                                LocalContentAlpha provides ContentAlpha.medium
                                            ) {
                                                Text(text = "• Manga from other sources will be kept untouched")
                                                Text(text = "• Do not minimize this app while the migration process is running")
                                            }
                                            Text(text = "4. Export the processed backup and restore in Tachiyomi")
                                        }
                                        Spacer(modifier = Modifier.padding(top = 16.dp))
                                        ButtonWithIcon(
                                            text = "IMPORT BACKUP",
                                            icon = Icons.Default.FileUpload,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                val mimeTypeMap = MimeTypeMap.getSingleton()
                                                val mimeTypes = arrayOf(
                                                    mimeTypeMap.getMimeTypeFromExtension("gz"),
                                                    mimeTypeMap.getMimeTypeFromExtension("json")
                                                )
                                                selectBackup.launch(mimeTypes)
                                            }
                                        )
                                    }
                                }
                                Status.PREPARING, Status.PROCESSING -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (viewModel.status == Status.PREPARING) {
                                            Text(text = "Preparing data", style = MaterialTheme.typography.subtitle1)
                                            Spacer(modifier = Modifier.height(8.dp))
                                        } else if (viewModel.status == Status.PROCESSING) {
                                            Text(text = "${viewModel.processedItems} of ${viewModel.totalDexItems}")
                                            Text(
                                                text = viewModel.currentManga,
                                                overflow = TextOverflow.Ellipsis,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (viewModel.convertedBackup != null) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(elevation = 16.dp) {
                                val export = rememberLauncherForActivityResult(contract = CreateDocument()) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val backup = viewModel.convertedBackup
                                        if (backup is FullBackup) {
                                            val byteArray = ProtoBuf.encodeToByteArray(
                                                BackupSerializer,
                                                backup
                                            )
                                            contentResolver.openOutputStream(uri)?.sink()?.gzip()?.buffer()?.use {
                                                it.write(byteArray)
                                            }
                                        } else if (backup is String) {
                                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                                                it.write(backup)
                                            }
                                        }
                                        lifecycleScope.launch(Dispatchers.Main) {
                                            Toast.makeText(
                                                this@MangaDexMigratorActivity,
                                                "Done",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Migrated ${viewModel.processedItems} of ${viewModel.totalDexItems} MangaDex manga",
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.subtitle1
                                    )

                                    if (viewModel.skippedItems.size > 0) {
                                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                            Text(text = "Skipped items:", style = MaterialTheme.typography.subtitle2)

                                            for (item in viewModel.skippedItems) {
                                                Text(text = "• $item", style = MaterialTheme.typography.body2)
                                            }
                                        }
                                    }

                                    ButtonWithIcon(
                                        text = "EXPORT BACKUP",
                                        icon = Icons.Default.FileDownload,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        onClick = { export.launch(viewModel.originalName) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ButtonWithIcon(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
        val padding = PaddingValues(
            start = 12.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 8.dp
        )
        Button(onClick = onClick, modifier = modifier, contentPadding = padding) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(text = text)
        }
    }
}

enum class Status {
    IDLE, PREPARING, PROCESSING
}

class MangaDexMigratorViewModel(app: Application) : AndroidViewModel(app) {
    var status by mutableStateOf(Status.IDLE)
    var currentManga by mutableStateOf("")
    var processedItems by mutableStateOf(0)
    var totalDexItems = 0
    val skippedItems = mutableListOf<String>()

    var convertedBackup: Any? by mutableStateOf(null)
    var originalName: String? = null

    private val dao by lazy {
        val db = Room.databaseBuilder(
            getApplication<Application>(),
            MangaDexDatabase::class.java,
            "mangadex.db"
        ).createFromAsset("mangadex.db").build()
        db.mangaDexDao()
    }

    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun processBackup(uri: Uri?) {
        if (uri == null) return

        originalName = uri.getDisplayName()
        if (originalName == null) throw IllegalStateException("try again plz")

        currentManga = ""
        convertedBackup = null
        processedItems = 0
        totalDexItems = 0
        skippedItems.clear()

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
        }

        if (backupString == null) {
            status = Status.IDLE
            throw IllegalStateException("try again plz")
        }

        val backup = ProtoBuf.decodeFromByteArray(BackupSerializer, backupString)
        val backupMangaList = backup.backupManga.toMutableList()
        totalDexItems = backupMangaList.count { it.source == MANGADEX_SOURCE_ID }

        status = Status.PROCESSING

        for (i in backupMangaList.indices) {
            val backupManga = backupMangaList[i].copy()
            if (backupManga.source != MANGADEX_SOURCE_ID) continue

            currentManga = backupManga.title
            val oldMangaId = backupManga.url.split("/")[2]
            if (oldMangaId.isUUID()) {
                // don't bother if it's already migrated
                skippedItems += currentManga
                continue
            }

            val newMangaId = dao.getNewMangaId(oldMangaId)
            if (newMangaId != null) {
                backupManga.url = "/manga/$newMangaId"

                var chapterMissing = false
                val chapters = backupManga.chapters.toMutableList()
                for (i2 in chapters.indices) {
                    val backupChapter = chapters[i2].copy()
                    if (backupChapter.url.startsWith("/api/")) {
                        val oldChapterId = backupChapter.url.split("/")[3]
                        val newChapterId = dao.getNewChapterId(oldChapterId)
                        if (newChapterId != null) {
                            backupChapter.url = "/chapter/$newChapterId"
                            chapters[i2] = backupChapter
                        } else {
                            chapterMissing = true
                            break
                        }
                    }
                }

                if (!chapterMissing) {
                    backupManga.chapters = chapters
                    backupMangaList[i] = backupManga
                    processedItems += 1
                } else {
                    skippedItems += currentManga
                }
            } else {
                skippedItems += currentManga
            }
        }

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
            Pair(manga, chapters)
        }
        totalDexItems = mangaList.count { it.first.source == MANGADEX_SOURCE_ID }

        status = Status.PROCESSING

        for (i in mangaList.indices) {
            val (manga, chapters) = mangaList[i]
            if (manga.source != MANGADEX_SOURCE_ID) continue

            currentManga = manga.title
            val oldMangaId = manga.url.split("/")[2]
            if (oldMangaId.isUUID()) {
                // don't bother if it's already migrated
                skippedItems += currentManga
                continue
            }

            val newMangaId = dao.getNewMangaId(oldMangaId)
            if (newMangaId != null) {
                manga.url = "/manga/$newMangaId"

                var chapterMissing = false
                for (i2 in chapters.indices) {
                    val backupChapter = chapters[i2]
                    if (backupChapter.url.startsWith("/api/")) {
                        val oldChapterId = backupChapter.url.split("/")[3]
                        val newChapterId = dao.getNewChapterId(oldChapterId)
                        if (newChapterId != null) {
                            backupChapter.url = "/chapter/$newChapterId"
                            chapters[i2] = backupChapter
                        } else {
                            chapterMissing = true
                            break
                        }
                    }
                }

                if (!chapterMissing) {
                    val mangaJsonObject = mangaArray[i].asJsonObject
                    mangaJsonObject[LegacyBackup.MANGA] = parser.toJsonTree(manga)
                    mangaJsonObject[LegacyBackup.CHAPTERS] = parser.toJsonTree(chapters)
                    mangaArray[i] = mangaJsonObject
                    processedItems += 1
                } else {
                    skippedItems += currentManga
                }
            } else {
                skippedItems += currentManga
            }
        }

        root[LegacyBackup.MANGAS] = mangaArray
        convertedBackup = parser.toJson(root)
        status = Status.IDLE
    }

    private fun Uri.getDisplayName(): String? {
        getApplication<Application>().contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                return cursor.getString(i)
            }
        }
        return null
    }

    private fun String.isUUID(): Boolean {
        return try {
            UUID.fromString(this)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    companion object {
        private const val MANGADEX_SOURCE_ID = 2499283573021220255
    }
}
