import dev.fritz2.binding.RootStore
import dev.fritz2.binding.storeOf
import dev.fritz2.components.FileReadingStrategy
import dev.fritz2.components.FileSelectionBaseComponent
import dev.fritz2.components.box
import dev.fritz2.components.clickButton
import dev.fritz2.components.file
import dev.fritz2.components.flexBox
import dev.fritz2.components.icon
import dev.fritz2.components.modal
import dev.fritz2.components.showToast
import dev.fritz2.components.spinner
import dev.fritz2.components.stackUp
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.dom.html.render
import dev.fritz2.styling.p
import dev.fritz2.styling.params.BoxParams
import dev.fritz2.styling.params.Style
import dev.fritz2.tracking.tracker
import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.FileReader
import xyz.ivaniskandar.ayunda.MangaDexMigrator
import xyz.ivaniskandar.ayunda.model.MigrationResult
import dev.fritz2.components.data.File as FritzFile

fun main() {
    val currentProgressStore = storeOf<String?>(null)
    val migrationResultStore = object : RootStore<MigrationResult?>(null) {
        val export = handle {
            if (it != null) {
                val x = it.fileBits.asUByteArray()
                val u = Uint8Array(x.size)
                for (i in x.indices) {
                    u[i] = x[i].unsafeCast<Byte>()
                }
                val blob = Blob(arrayOf(u.buffer))
                val blobUrl = URL.createObjectURL(blob)

                val a = document.createElement("a") as HTMLElement
//                a.setAttribute("href", "data:${it.mimeType};base64,${it.content}")
                a.setAttribute("href", blobUrl)
                a.setAttribute("download", it.fileName)
                a.style.display = "none"
                document.body?.appendChild(a)
                a.click()
                document.body?.removeChild(a)
            }
            it
        }
    }

    val mainStore = object : RootStore<Unit>(Unit) {
        val tracker = tracker()

        val processFile = handle<FritzFile> { _, file ->
            GlobalScope.launch(Dispatchers.Unconfined) {
                tracker.track {
                    try {
                        when {
                            file.name.endsWith(".proto.gz") -> {
                                migrationResultStore.update(
                                    MangaDexMigrator.processProtoBackup(file.content, file.name) {
                                        currentProgressStore.update(it)
                                    }
                                )
                            }
                            file.name.endsWith(".json") -> {
                                migrationResultStore.update(
                                    MangaDexMigrator.processJsonBackup(file.content, file.name) {
                                        currentProgressStore.update(it)
                                    }
                                )
                            }
                            else -> {
                                throw IllegalArgumentException("Please select file with proto.gz or json extension")
                            }
                        }
                    } catch (e: Exception) {
                        showToast {
                            hasCloseButton(false)
                            placement { bottom }
                            background { danger.main }
                            content {
                                p({
                                    margin { normal }
                                    color { neutral.main }
                                    textAlign { center }
                                }) {
                                    +"${e.message}"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // if json then return plaintext, otherwise base64
    val readingStrategy: FileReadingStrategy = { file ->
        callbackFlow {
            val isJson = file.type == "application/json"
            val reader = FileReader()
            val listener: (Event) -> Unit = { _ ->
                val content = if (isJson) {
                    reader.result.toString()
                } else {
                    var stringResult = reader.result.toString()
                    val index = stringResult.indexOf("base64,")
                    if (index > -1) stringResult = stringResult.substring(index + 7)
                    stringResult
                }
                offer(
                    dev.fritz2.components.data.File(
                        file.name,
                        file.type,
                        file.size.toLong(),
                        content
                    )
                )
            }
            reader.addEventListener(FileSelectionBaseComponent.eventName, listener)
            if (isJson) {
                reader.readAsText(file, "utf-8")
            } else {
                reader.readAsDataURL(file)
            }
            awaitClose { reader.removeEventListener(FileSelectionBaseComponent.eventName, listener) }
        }
    }

    fun RenderContext.idleCard() {
        box({
            background { color { gray200 } }
            radius { large }
            width { "100%" }
        }) {
            stackUp({
                alignItems { center }
                padding { small }
            }) {
                spacing { none }
                items {
                    box({
                        margins { bottom { small } }
                        width { full }
                    }) {
                        val pointStyle: Style<BoxParams> = {
                            fontSize { small }
                        }
                        val subpointStyle: Style<BoxParams> = {
                            fontSize { smaller }
                            opacity { "0.8" }
                        }
                        p({ fontSize { normal } }) { +"To migrate your MangaDex manga: " }
                        p(pointStyle) { +"1. Make a backup of your Tachiyomi library"}
                        p(pointStyle) { +"2. Import your backup to start the migration process"}
                        p(subpointStyle) { +"• Manga from other sources will be kept untouched" }
                        p(subpointStyle) { +"• Do not minimize this app while the migration process is running" }
                        p(pointStyle) { +"3. Export the processed backup"}
                        p(pointStyle) { +"4. Delete all MangaDex manga from your library" }
                        p(subpointStyle) { +"• You can see all your MangaDex manga in your library by typing \"mangadex\" in the search bar" }
                        p(subpointStyle) { +"• Do not check \"Downloaded chapters\" if you would like to keep your downloaded chapters" }
                        p(pointStyle) { +"5. Restore the exported backup in Tachiyomi"}
                    }
                    file({
                        width { "100%" }
                    }) {
                        fileReadingStrategy { readingStrategy }
                        button({
                            width { "100%" }
                        }) {
                            icon { fromTheme { add } }
                            text("Import backup")
                        }
                    } handledBy mainStore.processFile
                }
            }
        }
    }

    fun RenderContext.workingCard() {
        box({
            background { color { gray200 } }
            radius { large }
            width { "100%" }
        }) {
            stackUp({
                alignItems { center }
                padding { small }
            }) {
                spacing { smaller }
                items {
                    spinner({
                        size { "3rem" }
                    }) {}
                    p({
                        textAlign { center }
                    }) {
                        currentProgressStore.data.asText()
                    }
                }
            }
        }
    }

    fun RenderContext.exportCard() {
        box({
            background { color { gray200 } }
            radius { large }
            width { "100%" }
        }) {
            stackUp({
                alignItems { center }
                padding { small }
            }) {
                spacing { smaller }
                items {
                    val result = migrationResultStore.current!!
                    p({
                        textAlign { center }
                    }) {
                        +"Migrated ${result.totalMigratedManga} of ${result.totalDexManga} MangaDex manga"
                    }
                    val showExceptionForList: (String, List<String>) -> Unit = { title, list ->
                        if (list.isNotEmpty()) {
                            flexBox({
                                width { "100%" }
                                direction { row }
                                justifyContent { spaceBetween }
                                alignItems { center }
                            }) {
                                p({
                                    fontSize { small }
                                    fontWeight { semiBold }
                                }) {
                                    +title
                                }
                                icon({ margins { vertical { smaller } } }) { fromTheme { chevronDown } }

                                clicks.events.map { /* such clicks, very handler */ } handledBy modal({
                                    overflowY { auto }
                                }) {
                                    size { full }
                                    content {
                                        p({
                                            fontSize { large }
                                            fontWeight { semiBold }
                                            margins { bottom { normal } }
                                        }) {
                                            +title
                                        }
                                        list.forEach {
                                            p { +"• $it" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    showExceptionForList(
                        "Already migrated (${result.alreadyMigrated.size})",
                        result.alreadyMigrated
                    )
                    showExceptionForList(
                        "Missing new manga ID (${result.missingMangaId.size})",
                        result.missingMangaId
                    )
                    showExceptionForList(
                        "Missing new chapter ID (${result.missingChapterId.size})",
                        result.missingChapterId
                    )
                    clickButton({
                        width { "100%" }
                    }) {
                        text("Export")
                        icon { fromTheme { export } }
                    } handledBy migrationResultStore.export
                }
            }
        }
    }

    render {
        flexBox({
            width { full }
            justifyContent { center }
        }) {
            mainStore.tracker.data.render { isWorking ->
                stackUp({
                    alignItems { center }
                    width(sm = { full }, md = { "768px" })
                    padding { small }
                }) {
                    spacing { smaller }
                    items {
                        p({
                            fontSize { large }
                            fontWeight { semiBold }
                            textAlign { center }
                        }) {
                            +"Tachi Dex Migrator"
                        }
                        if (isWorking) {
                            workingCard()
                        } else {
                            idleCard()
                            migrationResultStore.data.render { a ->
                                if (a != null) {
                                    exportCard()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
