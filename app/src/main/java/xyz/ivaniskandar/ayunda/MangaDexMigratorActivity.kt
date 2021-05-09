package xyz.ivaniskandar.ayunda

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
                                                    viewModel.status = Status.IDLE
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                        Text(
                                            text = "To migrate your MangaDex manga:",
                                            modifier = Modifier.padding(bottom = 8.dp),
                                            style = MaterialTheme.typography.subtitle1
                                        )
                                        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.body2) {
                                            Text(text = "1. Make a backup of your Tachiyomi library")
                                            Text(text = "2. Import your backup to start the migration process")
                                            CompositionLocalProvider(
                                                LocalTextStyle provides MaterialTheme.typography.caption,
                                                LocalContentAlpha provides ContentAlpha.medium
                                            ) {
                                                Text(text = "• Manga from other sources will be kept untouched")
                                                Text(text = "• Do not minimize this app while the migration process is running")
                                            }
                                            Text(text = "3. Delete all MangaDex manga from your library")
                                            CompositionLocalProvider(
                                                LocalTextStyle provides MaterialTheme.typography.caption,
                                                LocalContentAlpha provides ContentAlpha.medium
                                            ) {
                                                Text(text = "• You can see all your MangaDex manga in your library by typing \"mangadex\" in the search bar")
                                                Text(text = "• Do not check \"Downloaded chapters\" if you'd like to keep your downloaded chapters")
                                            }
                                            Text(text = "4. Export the processed backup and restore in Tachiyomi")
                                        }
                                        Spacer(modifier = Modifier.padding(top = 24.dp))
                                        ButtonWithIcon(
                                            text = "IMPORT BACKUP",
                                            icon = Icons.Default.FileUpload,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { selectBackup.launch(arrayOf("*/*")) }
                                        )
                                    }
                                }
                                Status.PREPARING, Status.PROCESSING, Status.FINISHING -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        when (viewModel.status) {
                                            Status.PREPARING -> {
                                                Text(
                                                    text = "Preparing data",
                                                    style = MaterialTheme.typography.subtitle1
                                                )
                                            }
                                            Status.FINISHING -> {
                                                Text(
                                                    text = "Finishing migration",
                                                    style = MaterialTheme.typography.subtitle1
                                                )
                                            }
                                            Status.PROCESSING -> {
                                                Text(text = "${viewModel.processedCount} of ${viewModel.totalDexItems}")
                                                Text(
                                                    text = viewModel.currentManga,
                                                    overflow = TextOverflow.Ellipsis,
                                                    maxLines = 1
                                                )
                                            }
                                            else -> {}
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
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Migrated ${viewModel.migratedCount} of ${viewModel.totalDexItems} MangaDex manga",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.subtitle1
                                    )

                                    if (viewModel.alreadyMigrated.size > 0) {
                                        ExpandableListColumn(title = "Already migrated") {
                                            viewModel.alreadyMigrated.forEach {
                                                Text(text = "• $it", style = MaterialTheme.typography.body2)
                                            }
                                        }
                                    }
                                    if (viewModel.missingMangaId.size > 0) {
                                        ExpandableListColumn(title = "Missing new manga UUID") {
                                            viewModel.missingMangaId.forEach {
                                                Text(text = "• $it", style = MaterialTheme.typography.body2)
                                            }
                                        }
                                    }
                                    if (viewModel.missingChapterId.size > 0) {
                                        ExpandableListColumn(title = "Missing new chapter ID") {
                                            viewModel.missingChapterId.forEach {
                                                Text(text = "• $it", style = MaterialTheme.typography.body2)
                                            }
                                        }
                                    }

                                    ButtonWithIcon(
                                        text = "EXPORT BACKUP",
                                        icon = Icons.Default.FileDownload,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 24.dp),
                                        onClick = {
                                            var fileName = viewModel.originalName
                                            if (fileName != null) {
                                                val nameSplit = fileName.split(".")
                                                if (nameSplit.size >= 2) {
                                                    val nameWithoutExtension = nameSplit[0]
                                                    val extension = fileName.replace(nameWithoutExtension, "")
                                                    fileName = "${nameWithoutExtension}_modified$extension"
                                                }
                                            }
                                            export.launch(fileName)
                                        }
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
    fun ExpandableListColumn(title: String, content: @Composable ColumnScope.() -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        Column {
            if (expanded) Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.subtitle2
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    content = content
                )
            }
            if (expanded) Divider()
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
    IDLE, PREPARING, PROCESSING, FINISHING
}

class MangaDexMigratorViewModel(app: Application) : AndroidViewModel(app) {
    var status by mutableStateOf(Status.IDLE)
    var currentManga by mutableStateOf("")
    var processedCount by mutableStateOf(0)
    var migratedCount by mutableStateOf(0)
    var totalDexItems = 0

    val alreadyMigrated = mutableListOf<String>()
    val missingMangaId = mutableListOf<String>()
    val missingChapterId = mutableListOf<String>()

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

        originalName = uri.getDisplayName()
        if (originalName == null) throw IllegalStateException("try again plz")

        currentManga = ""
        convertedBackup = null
        processedCount = 0
        migratedCount = 0
        totalDexItems = 0
        alreadyMigrated.clear()
        missingMangaId.clear()
        missingChapterId.clear()

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
                    alreadyMigrated += currentManga
                    incrementProcessedCount()
                }
                continue
            }

            // Migrate manga url
            val newMangaId = dao.getNewMangaId(oldMangaId)
            if (newMangaId == null) {
                // new manga id doesn't exist so does the chapters
                if (backupManga.favorite) {
                    missingMangaId += currentManga
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
                    missingChapterId += "$currentManga ($chapterMissing)"
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
                alreadyMigrated += currentManga
                incrementProcessedCount()
                continue
            }

            // Migrate manga url
            val newMangaId = dao.getNewMangaId(oldMangaId)
            if (newMangaId == null) {
                // new manga id doesn't exist so does the chapters
                missingMangaId += currentManga
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
                missingChapterId += "$currentManga ($chapterMissing)"
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
            val newChapterId = dao.getNewChapterId(oldChapterId)
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
}
