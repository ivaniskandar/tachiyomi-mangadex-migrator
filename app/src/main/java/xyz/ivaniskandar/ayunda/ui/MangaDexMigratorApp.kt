package xyz.ivaniskandar.ayunda.ui

import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.toPaddingValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.ivaniskandar.ayunda.R
import xyz.ivaniskandar.ayunda.ui.MangaDexMigratorViewModel.Status.FINISHING
import xyz.ivaniskandar.ayunda.ui.MangaDexMigratorViewModel.Status.IDLE
import xyz.ivaniskandar.ayunda.ui.MangaDexMigratorViewModel.Status.PREPARING
import xyz.ivaniskandar.ayunda.ui.MangaDexMigratorViewModel.Status.PROCESSING
import xyz.ivaniskandar.ayunda.ui.component.ButtonWithBox
import xyz.ivaniskandar.ayunda.ui.component.ExpandableListColumn
import xyz.ivaniskandar.ayunda.ui.component.FlatCard
import xyz.ivaniskandar.ayunda.ui.component.RotatingCircularProgressIndicator
import xyz.ivaniskandar.ayunda.ui.component.SolidCircularProgressIndicator
import xyz.ivaniskandar.ayunda.ui.theme.AyundaTheme
import xyz.ivaniskandar.ayunda.util.CreateDocument
import xyz.ivaniskandar.ayunda.ui.MangaDexMigratorViewModel.Status as ActivityStatus

private const val TIME_TO_ENABLE_IMPORT = 4000L // 4s
private const val TICK_TO_ENABLE_IMPORT = 1L // 1ms

@Composable
fun MangaDexMigratorApp(viewModel: MangaDexMigratorViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val status = viewModel.status

    var progressUntilImportEnabled by rememberSaveable { mutableStateOf(0F) }
    var exporting by remember { mutableStateOf(false) }

    if (exporting) {
        ExportingDialog { exporting = !exporting }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = LocalWindowInsets.current.systemBars.toPaddingValues(
            additionalHorizontal = 16.dp,
            additionalVertical = 16.dp
        ),
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

        if (status == IDLE) {
            item {
                val selectBackup = rememberLauncherForActivityResult(contract = OpenDocument()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        viewModel.processBackup(it)
                    }
                }
                IdleCard(progressUntilImportEnabled) {
                    selectBackup.launch(arrayOf("*/*"))
                }

                // Show export card if converted backup is ready
                if (viewModel.migratedBackupFile != null) {
                    val export = rememberLauncherForActivityResult(contract = CreateDocument()) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch(Dispatchers.IO) {
                            exporting = true
                            viewModel.exportBackup(uri)
                            exporting = false
                            coroutineScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ExportCard(
                        migratedCount = viewModel.migratedCount,
                        totalCount = viewModel.totalDexItems,
                        alreadyMigratedTitles = viewModel.alreadyMigrated,
                        missingMangaTitles = viewModel.missingMangaId,
                        missingChapterTitles = viewModel.missingChapterId
                    ) {
                        export.launch(viewModel.migratedBackupFile!!.name)
                    }
                }
            }
        }

        if (status == PREPARING || status == PROCESSING || status == FINISHING) {
            item {
                WorkingCard(
                    status = status,
                    currentlyProcessedTitle = viewModel.currentManga,
                    processedCount = if (status == PROCESSING) viewModel.processedCount else 0,
                    totalCount = viewModel.totalDexItems
                )
            }
        }
    }

    LaunchedEffect(true) {
        if (progressUntilImportEnabled != 1F) {
            val timer = object : CountDownTimer(TIME_TO_ENABLE_IMPORT, TICK_TO_ENABLE_IMPORT) {
                override fun onTick(millisUntilFinished: Long) {
                    progressUntilImportEnabled = 1 - (millisUntilFinished.toFloat() / TIME_TO_ENABLE_IMPORT)
                }

                override fun onFinish() {
                    progressUntilImportEnabled = 1F
                }
            }
            delay(1000) // Wait until everything is settled
            timer.start()
        }
    }
}

@Composable
fun IdleCard(
    progressUntilImportEnabled: Float,
    onImportRequest: () -> Unit
) {
    FlatCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "To migrate your MangaDex manga:",
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.subtitle1
            )
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.body2) {
                Text(text = "1. Make a backup of your Tachiyomi library")
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "2. Import your backup to start the migration process")
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.caption,
                    LocalContentAlpha provides ContentAlpha.medium
                ) {
                    Text(text = "• Manga from other sources will be kept untouched")
                    Text(text = "• Do not minimize this app while the migration process is running")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "3. Export the processed backup")
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "4. Delete all MangaDex manga from your library")
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.caption,
                    LocalContentAlpha provides ContentAlpha.medium
                ) {
                    Text(text = "• You can see all your MangaDex manga in your library by typing \"mangadex\" in the search bar")
                    Text(text = "• Do not check \"Downloaded chapters\" if you would like to keep your downloaded chapters")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "5. Restore the exported backup in Tachiyomi")
            }

            Spacer(modifier = Modifier.padding(top = 24.dp))
            ButtonWithBox(
                text = "IMPORT BACKUP",
                boxContent = {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null
                    )
                    SolidCircularProgressIndicator(progress = 1 - progressUntilImportEnabled)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = progressUntilImportEnabled == 1F,
                onClick = onImportRequest
            )
        }
    }
}

@Composable
fun WorkingCard(
    status: ActivityStatus,
    currentlyProcessedTitle: String = "",
    processedCount: Int = 0,
    totalCount: Int = 0
) {
    FlatCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val progress = processedCount.toFloat() / (totalCount).coerceAtLeast(1) // Avoiding NaN
            RotatingCircularProgressIndicator(progress = progress)
            Spacer(modifier = Modifier.height(8.dp))
            when (status) {
                PREPARING -> {
                    Text(
                        text = "Preparing data",
                        style = MaterialTheme.typography.subtitle1
                    )
                }
                FINISHING -> {
                    Text(
                        text = "Finishing migration",
                        style = MaterialTheme.typography.subtitle1
                    )
                }
                PROCESSING -> {
                    Text(text = "$processedCount of $totalCount")
                    Text(
                        text = currentlyProcessedTitle,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
                else -> {
                    Log.wtf("WorkingCard", "$status is not a valid status")
                }
            }
        }
    }
}

@Composable
private fun ExportCard(
    migratedCount: Int,
    totalCount: Int,
    alreadyMigratedTitles: List<String>,
    missingMangaTitles: List<String>,
    missingChapterTitles: List<String>,
    onExportRequested: () -> Unit
) {
    FlatCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Migrated $migratedCount of $totalCount MangaDex manga",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.subtitle1
            )

            var listShown = false
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.body2) {
                if (alreadyMigratedTitles.isNotEmpty()) {
                    ExpandableListColumn(title = "Already migrated (${alreadyMigratedTitles.size})") {
                        alreadyMigratedTitles.forEach {
                            Text(text = "• $it")
                        }
                    }
                    listShown = true
                }
                if (missingMangaTitles.isNotEmpty()) {
                    ExpandableListColumn(title = "Missing new manga ID (${missingMangaTitles.size})") {
                        missingMangaTitles.forEach {
                            Text(text = "• $it")
                        }
                    }
                    listShown = true
                }
                if (missingChapterTitles.isNotEmpty()) {
                    ExpandableListColumn(title = "Missing new chapter ID (${missingChapterTitles.size})") {
                        missingChapterTitles.forEach {
                            Text(text = "• $it")
                        }
                    }
                    listShown = true
                }
            }

            ButtonWithBox(
                text = "EXPORT BACKUP",
                boxContent = { Icon(imageVector = Icons.Default.FileDownload, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (listShown) 24.dp else 0.dp),
                onClick = onExportRequested
            )
        }
    }
}

@Composable
fun ExportingDialog(onDismissRequest: () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(elevation = 16.dp) {
            Row(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.width(24.dp))
                Text(text = "Exporting backup file")
            }
        }
    }
}

@Preview
@Composable
fun IdleCardPreview() {
    AyundaTheme {
        IdleCard(
            progressUntilImportEnabled = 1F,
            onImportRequest = { /* Do nothing */ }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WorkingCardPreviews() {
    AyundaTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            WorkingCard(status = PREPARING)
            Spacer(modifier = Modifier.height(16.dp))
            WorkingCard(
                status = PROCESSING,
                currentlyProcessedTitle = "Title Name",
                processedCount = 88,
                totalCount = 888
            )
            Spacer(modifier = Modifier.height(16.dp))
            WorkingCard(status = FINISHING)
        }
    }
}

@Preview(name = "Export Card Normal")
@Composable
fun ExportCardNormalPreview() {
    AyundaTheme {
        ExportCard(
            migratedCount = 888,
            totalCount = 888,
            alreadyMigratedTitles = emptyList(),
            missingMangaTitles = emptyList(),
            missingChapterTitles = emptyList(),
            onExportRequested = { /* Do nothing */ }
        )
    }
}

@Preview(name = "Export Card with Exception")
@Composable
fun ExportCardExceptionPreview() {
    AyundaTheme {
        ExportCard(
            migratedCount = 882,
            totalCount = 888,
            alreadyMigratedTitles = listOf("Title 1"),
            missingMangaTitles = listOf("Title 2", "Title 3"),
            missingChapterTitles = listOf("Title 4", "Title 5", "Title 6"),
            onExportRequested = { /* Do nothing */ }
        )
    }
}
