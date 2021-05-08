package xyz.ivaniskandar.ayunda

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
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
import xyz.ivaniskandar.ayunda.db.MangaDexDatabase

class MangaDexMigratorActivity : AppCompatActivity() {

    private val viewModel by viewModels<MangaDexMigratorViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var status by mutableStateOf(Status.IDLE)
            var currentManga by mutableStateOf("")

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
                                    currentManga = ""
                                    viewModel.apply {
                                        originalName = null
                                        convertedBackup = null
                                        processedItems = 0
                                        totalDexItems = 0
                                        skippedItems.clear()
                                    }

                                    lifecycleScope.launch(Dispatchers.IO) {
                                        status = Status.PREPARING

                                        val backupString = try {
                                            contentResolver.openInputStream(it)?.source()?.gzip()?.buffer()?.use {
                                                it.readByteArray()
                                            }
                                        } catch (e: Exception) {
                                            null
                                        }

                                        if (backupString == null) {
                                            lifecycleScope.launch(Dispatchers.Main) {
                                                Toast.makeText(
                                                    this@MangaDexMigratorActivity,
                                                    "try again plz",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            status = Status.IDLE
                                            return@launch
                                        }

                                        val db = Room.databaseBuilder(
                                            this@MangaDexMigratorActivity,
                                            MangaDexDatabase::class.java,
                                            "mangadex.db"
                                        ).createFromAsset("mangadex.db").build()

                                        val backup = ProtoBuf.decodeFromByteArray(BackupSerializer, backupString)
                                        val backupMangaList = backup.backupManga.toMutableList()
                                        viewModel.totalDexItems = backupMangaList.count { it.source == MANGADEX_SOURCE_ID }

                                        status = Status.PROCESSING

                                        val mangaDexDao = db.mangaDexDao()
                                        for (i in backupMangaList.indices) {
                                            val backupManga = backupMangaList[i].copy()
                                            if (backupManga.source != MANGADEX_SOURCE_ID) continue

                                            currentManga = backupManga.title
                                            val oldMangaId = backupManga.url.split("/")[2]
                                            if (oldMangaId.isUUID()) {
                                                // don't bother if it's already migrated
                                                viewModel.skippedItems += currentManga
                                                continue
                                            }

                                            val newMangaId = mangaDexDao.getNewMangaId(oldMangaId)
                                            if (newMangaId != null) {
                                                backupManga.url = "/manga/$newMangaId"

                                                var chapterMissing = false
                                                val chapters = backupManga.chapters.toMutableList()
                                                for (i2 in chapters.indices) {
                                                    val backupChapter = chapters[i2].copy()
                                                    if (backupChapter.url.startsWith("/api/")) {
                                                        val oldChapterId = backupChapter.url.split("/")[3]
                                                        val newChapterId = mangaDexDao.getNewChapterId(oldChapterId)
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
                                                    viewModel.processedItems += 1
                                                } else {
                                                    viewModel.skippedItems += currentManga
                                                }
                                            } else {
                                                viewModel.skippedItems += currentManga
                                            }
                                        }

                                        viewModel.originalName = it.getDisplayName()
                                            ?.replace(".proto.gz", "_modified.proto.gz")
                                        viewModel.convertedBackup = backup.copy(backupManga = backupMangaList)
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
                                            onClick = { selectBackupFile.launch(arrayOf("*/*")) }
                                        )
                                    }
                                    Status.PREPARING, Status.PROCESSING -> {
                                        CircularProgressIndicator()
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                if (status == Status.PREPARING) {
                                    Text(text = "Preparing data", style = MaterialTheme.typography.subtitle1)
                                    Spacer(modifier = Modifier.height(8.dp))
                                } else if (status == Status.PROCESSING) {
                                    Text(text = "${viewModel.processedItems} of ${viewModel.totalDexItems}")
                                    Text(
                                        text = currentManga,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                        val byteArray = ProtoBuf.encodeToByteArray(
                                            BackupSerializer,
                                            viewModel.convertedBackup!!
                                        )
                                        contentResolver.openOutputStream(uri)?.sink()?.gzip()?.buffer()?.use {
                                            it.write(byteArray)
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
                                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
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
                                        onClick = { export.launch(Pair(viewModel.originalName, "application/gzip")) }
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
        IDLE, PREPARING, PROCESSING
    }

    companion object {
        private const val MANGADEX_SOURCE_ID = 2499283573021220255
    }
}

class MangaDexMigratorViewModel : ViewModel() {
    var processedItems by mutableStateOf(0)
    var totalDexItems by mutableStateOf(0)
    val skippedItems by mutableStateOf(mutableListOf<String>())

    var convertedBackup: Backup? by mutableStateOf(null)
    var originalName: String? = null
}
