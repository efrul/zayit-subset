package zayit.subset.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zayit.subset.data.BookSize
import zayit.subset.data.BookSizes
import zayit.subset.data.Builder
import zayit.subset.data.Cancelled
import zayit.subset.data.Cat
import zayit.subset.data.DesktopZayit
import zayit.subset.data.Device
import zayit.subset.data.Estimate
import zayit.subset.data.Mode
import zayit.subset.data.MoveLibrary
import zayit.subset.data.Plan
import zayit.subset.data.ReplaceLibrary
import zayit.subset.data.Library
import zayit.subset.data.Options
import zayit.subset.data.Phone
import zayit.subset.data.Settings
import zayit.subset.data.dirSize
import java.io.File
import java.sql.Statement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LogLine(val text: String, val kind: Kind) {
    enum class Kind { Info, Normal, Error, Success }
}

class Result(val dir: File, val total: Long, val parts: List<Pair<String, Long>>) {
    /** Bytes of the search-by-meaning vectors in the text index (0 when they were left out). */
    val vectors: Long =
        File(dir, "seforim.db.lucene").listFiles { f -> f.name.contains("HnswVectorsFormat") }?.sumOf { it.length() } ?: 0
}

/** Everything the window shows, and what its buttons do. */
class AppState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // The library
    var library by mutableStateOf<Library?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var sourceError by mutableStateOf<String?>(null)
        private set
    var sourceDir by mutableStateOf(File(Settings.get("Source", Library.defaultDir().path)))
        private set

    // The choice of books
    var selected by mutableStateOf(
        Settings.get("Books", "").split(',').mapNotNull { it.trim().toLongOrNull() }.toSet(),
    )
        private set
    var expanded by mutableStateOf(setOf<Long>())
        private set
    val search = TextFieldState()
    /** The side island's page: building (or the library just built), or the settings. */
    var panel by mutableStateOf(Panel.Main)

    /** The library just built took the place of desktop Zayit's. */
    var replacedDone by mutableStateOf(false)
        private set

    /** What the book pane shows: the tree, the chosen books, or the commentaries that join them. */
    var view by mutableStateOf(View.Tree)
    var preview by mutableStateOf("")
        private set

    // Options
    var commentaries by mutableStateOf(Settings.flag("Commentaries", true))
    var textIndex by mutableStateOf(Settings.flag("TextIndex", true))
    var lookup by mutableStateOf(Settings.flag("Lookup", true))
    val maxLines = TextFieldState(Settings.get("MaxLines", "10000"))
    val fullLines = TextFieldState(Settings.get("FullLines", "3000"))
    var noLimits by mutableStateOf(Settings.flag("NoLimits", false))

    /** Keep the vectors of desktop Zayit's search by meaning (only desktop uses them). */
    var keepVectors by mutableStateOf(Settings.flag("KeepVectors", false))

    /** What happens once the library is built: stays separate, desktop Zayit switches to it, or it replaces Zayit's. */
    var after by mutableStateOf(
        runCatching { After.valueOf(Settings.get("After", if (Settings.flag("UseInDesktop", false)) "Switch" else "Separate")) }
            .getOrDefault(After.Separate),
    )

    /** "I understand the full library will be deleted": asked again at every start, never saved. */
    var replaceConfirmed by mutableStateOf(false)

    /** The size of the library desktop Zayit uses now, for the warning. */
    var zayitLibrarySize by mutableStateOf(0L)
        private set

    /** A replacement that has not happened yet (desktop Zayit was open, or it failed): the result card offers it again. */
    var replacePending by mutableStateOf(false)
        private set

    /** After building a separate library: a shortcut that opens desktop Zayit on it. */
    var makeShortcut by mutableStateOf(Settings.flag("MakeShortcut", false))

    /** The small library desktop Zayit was switched to, or null when it uses its own. */
    var desktopLibrary by mutableStateOf<String?>(null)
        private set

    /** Commentaries chosen by hand: whole, by their linked lines, or left out. */
    var overrides by mutableStateOf(parseOverrides(Settings.get("Overrides", "")))
        private set
    var outParent by mutableStateOf(
        File(Settings.get("OutParent", File(System.getProperty("user.home"), "Downloads/Zayit Subset").path)),
    )

    // Build
    var building by mutableStateOf(false)
        private set
    var progress by mutableStateOf<Float?>(0f)
        private set
    var step by mutableStateOf("")
        private set
    val log = mutableStateListOf<LogLine>()
    var result by mutableStateOf<Result?>(null)
        private set
    private var builder: Builder? = null

    // Phone
    var pushOpen by mutableStateOf(false)
    var devices by mutableStateOf<List<Device>>(emptyList())
        private set
    var device by mutableStateOf<Device?>(null)
    var pushing by mutableStateOf(false)
        private set
    var deviceMessage by mutableStateOf<String?>(null)
        private set

    init {
        load(sourceDir)
        scope.launch { desktopLibrary = withContext(Dispatchers.IO) { DesktopZayit.permanent() } }
        scope.launch { zayitLibrarySize = withContext(Dispatchers.IO) { ReplaceLibrary.size(ReplaceLibrary.zayitLibraryDir()) } }
    }

    fun load(dir: File) {
        sourceDir = dir
        loading = true
        sourceError = null
        scope.launch {
            val lib = withContext(Dispatchers.IO) { runCatching { Library.load(dir) } }
            loading = false
            lib.onSuccess {
                library = it
                selected = selected.filter { id -> it.books.containsKey(id) }.toSet()
                Settings.set("Source", dir.path)
                refreshPreview()
                loadSizes(it)
            }.onFailure {
                library = null
                sourceError = it.message
            }
        }
    }

    // ---------- the weight of each book and category ----------

    var sizes by mutableStateOf<Map<Long, BookSize>?>(null)
        private set
    var sizesLoading by mutableStateOf(false)
        private set

    private fun loadSizes(lib: Library) {
        sizes = null
        sizesLoading = true
        scope.launch {
            val m = withContext(Dispatchers.IO) { runCatching { BookSizes.load(lib) }.getOrNull() }
            if (library === lib) {
                sizes = m
                sizesLoading = false
            }
        }
    }

    /** What the book adds on its own (without the commentaries it brings), or null until the sizes are read. */
    fun weight(bookId: Long): Long? = sizes?.let { m -> m[bookId]?.weight(textIndex) ?: 0L }

    /** The weight of all the books of [c], and of those of them that are chosen. */
    fun weights(c: Cat): Pair<Long, Long>? {
        val m = sizes ?: return null
        var all = 0L
        var chosen = 0L
        for (b in c.all) {
            val w = m[b.id]?.weight(textIndex) ?: 0L
            all += w
            if (b.id in selected) chosen += w
        }
        return chosen to all
    }

    // ---------- selection ----------

    fun catState(c: Cat): Int {
        var n = 0
        for (b in c.all) if (b.id in selected) n++
        return if (n == 0) 0 else if (n == c.all.size) 2 else 1
    }

    fun selectedIn(c: Cat): Int = c.all.count { it.id in selected }

    fun toggleCat(c: Cat) {
        val ids = c.all.map { it.id }
        selected = if (catState(c) == 2) selected - ids.toSet() else selected + ids
        changed()
    }

    fun toggleBook(id: Long) {
        selected = if (id in selected) selected - id else selected + id
        changed()
    }

    fun toggleExpanded(c: Cat) {
        expanded = if (c.id in expanded) expanded - c.id else expanded + c.id
    }


    fun clearSelection() {
        selected = emptySet()
        changed()
    }

    fun changed() {
        Settings.set("Books", selected.joinToString(","))
        refreshPreview()
    }

    // ---------- preview: which books join, and how big the library will be ----------

    var estimate by mutableStateOf<Estimate?>(null)
        private set
    var estimating by mutableStateOf(false)
        private set

    private var previewJob: Job? = null

    @Volatile private var previewStatement: Statement? = null

    var plan by mutableStateOf<Plan?>(null)
        private set

    fun refreshPreview() {
        previewJob?.cancel()
        runCatching { previewStatement?.cancel() }
        val lib = library ?: return
        if (selected.isEmpty()) {
            preview = "בחרו ספרים מהעץ או מהחיפוש."
            estimate = null
            plan = null
            estimating = false
            return
        }
        val o = options() ?: return
        estimating = true
        previewJob =
            scope.launch {
                delay(500)
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val p = Builder.plan(lib, o) { previewStatement = it }
                            p to Builder.estimate(lib, o, p) { previewStatement = it }
                        }.getOrNull()
                    } ?: return@launch
                val (p, e) = result
                estimating = false
                plan = p
                estimate = e
                val joined = p.keep.size - p.chosen.size
                val out = p.candidates.size - joined
                preview =
                    when {
                        !o.commentaries -> "בלי פירושים ותרגומים: %,d ספרים, כ־‎%,d שורות.".format(e.books, e.lines)
                        p.candidates.isEmpty() -> "לא נמצאו פירושים ותרגומים מקושרים."
                        else ->
                            "יחד עם %,d פירושים ותרגומים: %,d ספרים, כ־‎%,d שורות".format(joined, e.books, e.lines) +
                                (if (p.linked.isNotEmpty()) "; %,d מהם רק בקטעים המקושרים".format(p.linked.size) else "") +
                                (if (out > 0) "; %,d לא ייכנסו".format(out) else "") + "."
                    }
            }
    }

    fun modeOf(id: Long): Mode? {
        val lib = library ?: return null
        val o = options() ?: return null
        return o.modeOf(id, lib.books[id]?.lines ?: 0)
    }

    fun autoModeOf(id: Long): Mode? {
        val lib = library ?: return null
        val o = options() ?: return null
        return o.autoMode(lib.books[id]?.lines ?: 0)
    }

    /** Chooses [mode] for commentary [id]; choosing what the limits would choose drops the hand choice. */
    fun setMode(
        id: Long,
        mode: Mode,
    ) {
        overrides = if (mode == autoModeOf(id)) overrides - id else overrides + (id to mode)
        saveOverrides()
    }

    fun allFull() {
        val p = plan ?: return
        overrides = overrides + p.candidates.associateWith { Mode.Full }
        saveOverrides()
    }

    fun resetModes() {
        overrides = emptyMap()
        saveOverrides()
    }

    private fun saveOverrides() {
        Settings.set("Overrides", overrides.entries.joinToString(",") { it.key.toString() + ":" + it.value.name })
        refreshPreview()
    }

    private fun options(): Options? {
        val max = maxLines.text.toString().trim().toLongOrNull()?.takeIf { it > 0 } ?: return null
        val full = fullLines.text.toString().trim().toLongOrNull()?.takeIf { it > 0 } ?: return null
        return Options(selected.sorted(), commentaries, max, full, textIndex, lookup, noLimits, overrides, keepVectors && textIndex)
    }

    fun saveOptions() {
        Settings.set("Commentaries", commentaries)
        Settings.set("TextIndex", textIndex)
        Settings.set("Lookup", lookup)
        Settings.set("NoLimits", noLimits)
        Settings.set("KeepVectors", keepVectors)
        Settings.set("After", after.name)
        Settings.set("MakeShortcut", makeShortcut)
        Settings.set("MaxLines", maxLines.text.toString().trim())
        Settings.set("FullLines", fullLines.text.toString().trim())
        Settings.set("OutParent", outParent.path)
    }

    // ---------- build ----------

    fun build() {
        val lib = library ?: return
        if (building || selected.isEmpty()) return
        if (after == After.Replace && !replaceConfirmed) return
        val o = options()
        if (o == null) {
            log.add(LogLine("מספרי השורות בהגדרות המתקדמות אינם תקינים", LogLine.Kind.Error))
            return
        }
        saveOptions()
        val out = File(outParent, "Zayit subset " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm")))
        log.clear()
        result = null
        replacePending = false
        replacedDone = false
        desktopMessage = null
        pushOpen = false
        building = true
        progress = 0f
        log.add(LogLine("יעד: " + out.path, LogLine.Kind.Info))
        val b =
            Builder(
                lib, o, out,
                log = { text -> scope.launch { log.add(LogLine(text, LogLine.Kind.Normal)) } },
                progress = { f, s -> scope.launch { progress = f; step = s } },
            )
        builder = b
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { b.run() } }
            building = false
            builder = null
            outcome.onSuccess {
                step = ""
                afterBuild(out)
                result =
                    Result(
                        out,
                        dirSize(out) - File(out, "subset_info.txt").length(),
                        listOf("seforim.db", "seforim.db.lucene", "seforim.db.lookup.lucene", "lexical.db", "catalog.pb")
                            .map { it to dirSize(File(out, it)) }
                            .filter { it.second > 0 },
                    )
            }.onFailure { e ->
                if (e is Cancelled) {
                    step = "נעצר. התיקייה החלקית נמחקה."
                    withContext(Dispatchers.IO) { out.deleteRecursively() }
                } else {
                    step = ""
                    log.add(LogLine("שגיאה: " + (e.message ?: e.toString()), LogLine.Kind.Error))
                }
            }
        }
    }

    fun cancel() {
        builder?.cancel()
    }

    // ---------- phone ----------

    fun openPush() {
        pushOpen = true
        refreshDevices()
    }

    fun refreshDevices() {
        deviceMessage = "מחפש מכשירים…"
        scope.launch {
            val adb = withContext(Dispatchers.IO) { Phone.adb() }
            if (adb == null) {
                devices = emptyList()
                deviceMessage = "לא נמצא adb במחשב. יש להתקין Android platform-tools."
                return@launch
            }
            val list = withContext(Dispatchers.IO) { runCatching { Phone.devices(adb) }.getOrDefault(emptyList()) }
            devices = list
            if (device !in list) device = list.firstOrNull()
            deviceMessage =
                if (list.isEmpty()) "לא נמצא טלפון מחובר. יש לחבר את הטלפון ב־USB, להפעיל ניפוי באגים ולאשר את המחשב בטלפון." else null
        }
    }

    fun push() {
        val r = result ?: return
        val d = device ?: return
        if (pushing || building) return
        pushing = true
        building = true
        progress = null
        step = "מעביר ל־${d.model}…"
        log.add(LogLine("העברה ל־${d.model}", LogLine.Kind.Info))
        scope.launch {
            val code =
                withContext(Dispatchers.IO) {
                    val adb = Phone.adb() ?: return@withContext -1
                    runCatching {
                        Phone.push(
                            adb, d, r.dir,
                            log = { text, error -> scope.launch { log.add(LogLine(text, if (error) LogLine.Kind.Error else LogLine.Kind.Normal)) } },
                            status = { text -> scope.launch { step = text } },
                        )
                    }.getOrElse { e ->
                        scope.launch { log.add(LogLine(e.message ?: e.toString(), LogLine.Kind.Error)) }
                        -1
                    }
                }
            pushing = false
            building = false
            step = ""
            if (code == 0) {
                pushOpen = false
                log.add(LogLine("ההעברה הסתיימה. אפשר לפתוח את זית בטלפון.", LogLine.Kind.Success))
            } else {
                log.add(LogLine("ההעברה נכשלה.", LogLine.Kind.Error))
            }
        }
    }

    // ---------- desktop Zayit ----------

    /** What was chosen for after the build: switch desktop Zayit to the library, or a shortcut to it. */
    private fun afterBuild(out: File) {
        if (after == After.Replace) {
            replace(out)
            return
        }
        val switch = after == After.Switch
        val shortcut = makeShortcut && after == After.Separate
        if (!switch && !shortcut) return
        scope.launch {
            val r =
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (switch) DesktopZayit.setPermanent(File(out, "seforim.db"))
                        if (shortcut) DesktopZayit.createShortcut(out)
                        DesktopZayit.permanent()
                    }
                }
            r.onSuccess { now ->
                desktopLibrary = now
                log.add(
                    LogLine(
                        if (switch) {
                            "זית במחשב תשתמש מעכשיו בספרייה הזו, מהפתיחה הבאה שלה. אפשר לחזור לספרייה המלאה מכרטיס \"ספריית המקור\"."
                        } else {
                            "נוצר קיצור דרך \"זית – ${out.name}\" בשולחן העבודה ובתפריט התחל."
                        },
                        LogLine.Kind.Success,
                    ),
                )
            }.onFailure { e -> log.add(LogLine("לא הצלחתי: " + (e.message ?: e.toString()), LogLine.Kind.Error)) }
        }
    }

    /** The small library in [out] takes the place of desktop Zayit's library, which is deleted. */
    fun replace(out: File) {
        val target = ReplaceLibrary.zayitLibraryDir()
        replacePending = true
        building = true
        progress = null
        step = "מחליף את הספרייה של זית…"
        scope.launch {
            val r =
                withContext(Dispatchers.IO) {
                    runCatching {
                        ReplaceLibrary.replace(out, target, log = { text -> scope.launch { log.add(LogLine(text, LogLine.Kind.Normal)) } })
                    }
                }
            building = false
            step = ""
            r.onSuccess {
                replacePending = false
                replaceConfirmed = false
                replacedDone = true
                desktopLibrary = null
                zayitLibrarySize = ReplaceLibrary.size(target)
                log.add(LogLine("הספרייה של זית במחשב הוחלפה בספרייה המוקטנת. מעכשיו זה גם המקור של התוכנה הזו.", LogLine.Kind.Success))
                result = Result(target, ReplaceLibrary.size(target), result?.parts.orEmpty())
                load(target)
            }.onFailure { e -> log.add(LogLine(e.message ?: e.toString(), LogLine.Kind.Error)) }
        }
    }

    // ---------- moving desktop Zayit's own library (the full one) to another folder or drive ----------

    /** Where desktop Zayit keeps its library (its own setting, else its own folder). */
    var zayitDir by mutableStateOf(ReplaceLibrary.zayitLibraryDir())
        private set

    /** The folder the library would move to; the move panel is open while it is set. */
    var moveTarget by mutableStateOf<File?>(null)
    var moving by mutableStateOf(false)
        private set
    var moveProgress by mutableStateOf(0f)
        private set
    var moveMessage by mutableStateOf<String?>(null)
        private set

    @Volatile private var moveCancelled = false

    private fun refreshZayitDir() {
        scope.launch {
            val (dir, bytes) = withContext(Dispatchers.IO) { ReplaceLibrary.zayitLibraryDir().let { it to ReplaceLibrary.size(it) } }
            zayitDir = dir
            zayitLibrarySize = bytes
        }
    }

    fun chooseMoveTarget(chosen: File) {
        moveTarget = MoveLibrary.destination(chosen)
        moveMessage = null
    }

    fun startMove() {
        val to = moveTarget ?: return
        val from = zayitDir
        if (moving || building) return
        moving = true
        building = true
        moveCancelled = false
        moveProgress = 0f
        moveMessage = "מעתיק…"
        scope.launch {
            val r =
                withContext(Dispatchers.IO) {
                    runCatching {
                        MoveLibrary.move(
                            from, to,
                            progress = { done, total ->
                                scope.launch {
                                    moveProgress = if (total > 0) done.toFloat() / total else 0f
                                    moveMessage = "מעתיק " + zayit.subset.data.size(done) + " מתוך " + zayit.subset.data.size(total)
                                }
                            },
                            cancelled = { moveCancelled },
                            log = { text -> scope.launch { log.add(LogLine(text, LogLine.Kind.Normal)) } },
                        )
                    }
                }
            moving = false
            building = false
            r.onSuccess {
                moveTarget = null
                moveMessage = "הספרייה של זית הועברה ל־" + to.path
                refreshZayitDir()
                if (library?.dir?.canonicalFile == from.canonicalFile) load(to)
            }.onFailure { e ->
                moveMessage = if (e is Cancelled) "ההעברה נעצרה; הספרייה נשארה במקומה." else (e.message ?: e.toString())
            }
        }
    }

    fun cancelMove() {
        moveCancelled = true
    }

    fun restoreDesktop() {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { DesktopZayit.clearPermanent(); DesktopZayit.permanent() } }
            r.onSuccess { desktopLibrary = it }
        }
    }

    var desktopMessage by mutableStateOf<String?>(null)
        private set

    fun openInDesktop(dir: File) {
        val exe = DesktopZayit.find()
        if (exe == null) {
            desktopMessage = "לא נמצאה זית למחשב במחשב הזה."
            return
        }
        desktopMessage = "פותח את זית למחשב…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { DesktopZayit.open(exe, dir) }.getOrDefault(false) }
            desktopMessage =
                if (ok) {
                    "זית למחשב נפתחה על הספרייה המוקטנת, עם פרופיל משלה: הלשוניות, ההיסטוריה וההערות שלה נפרדות מאלה של הספרייה המלאה."
                } else {
                    "זית למחשב נסגרה מיד. אם היא כבר פתוחה, יש לסגור אותה ולנסות שוב."
                }
        }
    }

    fun openFolder(dir: File) {
        runCatching { ProcessBuilder("explorer.exe", dir.path).start() }
    }

    enum class View { Tree, Selected, Commentaries }

    enum class After { Separate, Switch, Replace }

    enum class Panel { Main, Settings }

    /** Back to building: the result and its log go, the choice of books stays. */
    fun newLibrary() {
        result = null
        log.clear()
        step = ""
        pushOpen = false
        replacePending = false
        replacedDone = false
        desktopMessage = null
    }

    companion object {
        private fun parseOverrides(text: String): Map<Long, Mode> =
            text.split(',').mapNotNull { part ->
                val i = part.indexOf(':')
                if (i <= 0) return@mapNotNull null
                val id = part.substring(0, i).trim().toLongOrNull() ?: return@mapNotNull null
                val mode = runCatching { Mode.valueOf(part.substring(i + 1).trim()) }.getOrNull() ?: return@mapNotNull null
                id to mode
            }.toMap()

        /** The books CliKt builds when given none (a fixed set for checks, not offered in the window). */
        val TEST_SET = listOf(1L, 103, 40, 122, 123, 124, 208, 209, 39, 132, 128, 268)
    }
}
