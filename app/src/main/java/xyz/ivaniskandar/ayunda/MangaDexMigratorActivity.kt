package xyz.ivaniskandar.ayunda

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import eu.kanade.tachiyomi.data.backup.full.models.Backup
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import okio.source

class MangaDexMigratorActivity : AppCompatActivity() {

    private var newMangaIds: List<List<String>>? = null
    private var newChapterIds: List<List<String>>? = null

    private var status by mutableStateOf(Status.IDLE)
    private var processedItems by mutableStateOf(0)
    private var totalDexItems by mutableStateOf(0)
    private var currentManga by mutableStateOf("")
    private val skippedItems = mutableStateListOf<String>()

    private var originalName: String? = null
    private var convertedBackup: Backup? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AyundaTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(elevation = 16.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (status == Status.IDLE) {
                                Text(
                                    text = "Legacy backup is not supported",
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    style = MaterialTheme.typography.subtitle1
                                )
                            }

                            val selectBackupFile = rememberLauncherForActivityResult(contract = OpenDocument()) {
                                originalName = null
                                convertedBackup = null
                                processedItems = 0
                                totalDexItems = 0
                                lifecycleScope.launch(Dispatchers.IO) {
                                    status = Status.PREPARING
                                    if (newMangaIds == null) {
                                        newMangaIds = csvReader().readAll(resources.openRawResource(R.raw.manga_map))
                                    }
                                    if (newChapterIds == null) {
                                        newChapterIds = csvReader().readAll(resources.openRawResource(R.raw.chapter_map))
                                    }

                                    val backupString = try {
                                        contentResolver.openInputStream(it)?.source()?.gzip()?.buffer()?.use {
                                            it.readByteArray()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(this@MangaDexMigratorActivity, "git gud", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    if (backupString == null) {
                                        status = Status.FAILURE
                                        return@launch
                                    }

                                    val backup = ProtoBuf.decodeFromByteArray(BackupSerializer, backupString)
                                    val dexItems = backup.backupManga.filter { it.source == MANGADEX_ID }
                                    totalDexItems = dexItems.size

                                    status = Status.PROCESSING

                                    for (backupManga in dexItems) {
                                        currentManga = backupManga.title

                                        val oldMangaId = backupManga.url.split("/")[2]
                                        if (oldMangaId.isUUID()) {
                                            // don't bother if it's already migrated
                                            skippedItems += currentManga
                                            continue
                                        }

                                        val newMangaId = findNewId(newMangaIds!!, oldMangaId)
                                        if (newMangaId != null) {
                                            backupManga.url = "/manga/$newMangaId"
                                        }

                                        backupManga.chapters.forEach { backupChapter ->
                                            if (backupChapter.url.startsWith("/api/")) {
                                                val oldChapterId = backupChapter.url.split("/")[3]
                                                val newChapterId = findNewId(newChapterIds!!, oldChapterId)
                                                if (newChapterId != null) {
                                                    backupChapter.url = "/chapter/$newChapterId"
                                                }
                                            }
                                        }

                                        processedItems += 1
                                    }

                                    originalName = it.getDisplayName()?.replace(".proto.gz", "_modified.proto.gz")
                                    convertedBackup = backup
                                    status = Status.IDLE
                                }
                            }

                            when (status) {
                                Status.IDLE -> {
                                    Spacer(modifier = Modifier.padding(top = 8.dp))
                                    ButtonWithIcon(
                                        text = "IMPORT BACKUP",
                                        icon = Icons.Default.FileUpload,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { selectBackupFile.launch(arrayOf("application/gzip")) }
                                    )
                                }
                                Status.PREPARING, Status.PROCESSING -> {
                                    CircularProgressIndicator()
                                }
                                Status.FAILURE -> {
                                    Text(text = "Invalid backup file, try again.")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            if (status == Status.PREPARING) {
                                Text(text = "Preparing data", style = MaterialTheme.typography.subtitle1)
                                Spacer(modifier = Modifier.height(8.dp))
                            } else if (status == Status.PROCESSING) {
                                Text(text = "$processedItems of $totalDexItems migrated")
                                Text(
                                    text = currentManga,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    if (status == Status.PROCESSING || convertedBackup != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(elevation = 16.dp) {
                            val export = rememberLauncherForActivityResult(contract = CreateDocument()) { uri ->
                                if (uri == null) return@rememberLauncherForActivityResult
                                val byteArray = ProtoBuf.encodeToByteArray(BackupSerializer, convertedBackup!!)
                                contentResolver.openOutputStream(uri)?.sink()?.gzip()?.buffer()?.use {
                                    it.write(byteArray)
                                }
                                Toast.makeText(this@MangaDexMigratorActivity, "Done", Toast.LENGTH_SHORT).show()
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Migrated $processedItems of $totalDexItems MangaDex manga",
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.subtitle1
                                )

                                if (skippedItems.size > 0) {
                                    LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                                        item {
                                            Text(text = "Skipped items:", style = MaterialTheme.typography.subtitle2)
                                        }
                                        items(skippedItems) {
                                            Text(text = "• $it", style = MaterialTheme.typography.body2)
                                        }
                                    }
                                }

                                if (convertedBackup != null) {
                                    ButtonWithIcon(
                                        text = "EXPORT BACKUP",
                                        icon = Icons.Default.FileDownload,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        onClick = { export.launch(Pair(originalName, "application/gzip")) }
                                    )
                                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                                        Text(
                                            text = "Purge your library before restoring.",
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            style = MaterialTheme.typography.caption
                                        )
                                    }
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

    private fun findNewId(csv: List<List<String>>, oldId: String): String? {
        val i = csv.binarySearchBy(oldId, selector = { it[0] })
        return if (i >= 0) csv[i][1] else null
    }

    private fun Uri.getDisplayName(): String? {
        contentResolver.query(this, null, null, null, null)?.use { cursor ->
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

    private enum class Status {
        IDLE, PREPARING, PROCESSING, FAILURE
    }

    companion object {
        private const val MANGADEX_ID = 2499283573021220255
    }
}
