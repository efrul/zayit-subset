@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package zayit.subset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import zayit.subset.data.MoveLibrary
import zayit.subset.data.size
import java.io.File

/**
 * The side island, one page at a time: building a library, the library just built (and what to do with it), or the
 * settings that rarely change (the source, desktop Zayit's library and where it lives, the limits for commentaries).
 */
@Composable
fun SidePanel(state: AppState) {
    val r = state.result
    when {
        state.panel == AppState.Panel.Settings -> SettingsPage(state)
        r != null -> ResultPage(state, r)
        else -> BuildPage(state)
    }
}

/**
 * A page as Zayit's home: its olive wallpaper fading in at the top, the branch of its logo by the title, cards that
 * scroll over it, and an optional footer that stays at the bottom.
 */
@Composable
private fun Page(
    title: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Backdrop(Modifier.fillMaxWidth().height(320.dp))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Branch(Modifier.size(width = 26.dp, height = 28.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    body()
                }
                VerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
            if (footer != null) {
                Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { footer() }
            }
        }
    }
}

@Composable
private fun IconAction(
    key: org.jetbrains.jewel.ui.icon.IconKey,
    label: String,
    onClick: () -> Unit,
) {
    Tooltip(tooltip = { Text(label) }) {
        IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) { Icon(key, contentDescription = label) }
    }
}

// ---------- building ----------

@Composable
private fun BuildPage(state: AppState) {
    Page(
        title = "ספרייה חדשה",
        trailing = { IconAction(AllIconsKeys.General.Settings, "הגדרות") { state.panel = AppState.Panel.Settings } },
        footer = { BuildFooter(state) },
    ) {
        EstimateHero(state)
        Section("מה לכלול") { Contents(state) }
        Section("לאן") { Destination(state) }
    }
}

/** The expected size, large and in the accent as the time in Zayit's zmanim widget, and what it is made of. */
@Composable
private fun EstimateHero(state: AppState) {
    val e = state.estimate?.takeIf { state.selected.isNotEmpty() }
    val plan = state.plan
    val joined = if (state.commentaries && plan != null) plan.keep.size - plan.chosen.size else 0
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("גודל משוער", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                when {
                    e == null -> Hint("בחרו ספרים מהרשימה")
                    state.estimating -> Hint("מחשב…")
                    else ->
                        Text(
                            (if (joined > 0) "%,d נבחרו + %,d פירושים".format(e.books - joined, joined) else "%,d ספרים".format(e.books)) +
                                " · ‎%,d שורות".format(e.lines),
                            fontSize = 13.sp,
                        )
                }
            }
            Text(if (e == null) "—" else "כ־" + size(e.total), fontSize = 30.sp, color = accent, modifier = Modifier.padding(start = 12.dp))
        }
        if (e == null) return@Card
        if (plan != null && state.commentaries) {
            val partial = plan.linked.size
            val out = plan.candidates.size - joined
            if (partial > 0 || out > 0) {
                Hint(
                    listOfNotNull(
                        if (partial > 0) "%,d פירושים רק בקטעים המקושרים".format(partial) else null,
                        if (out > 0) "%,d לא נכנסים".format(out) else null,
                    ).joinToString("  ·  "),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ValueRow("מסד הנתונים", size(e.database))
            if (e.textIndex > 0) ValueRow(if (state.keepVectors) "חיפוש, גם לפי משמעות" else "חיפוש בטקסט", size(e.textIndex))
            ValueRow("מילון", size(e.lexical))
            if (e.lookup + e.catalog > 0) ValueRow("הצעות ורשימת ספרים", size(e.lookup + e.catalog))
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    info: String,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CheckboxRow(title, checked, onChange, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        InfoTip(info)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun Contents(state: AppState) {
    fun changed() {
        state.saveOptions()
        state.refreshPreview()
    }
    val candidates = state.plan?.candidates?.size ?: 0
    OptionRow(
        "פירושים ותרגומים",
        "ספרים שמקושרים לספרים שנבחרו כפירוש או כתרגום (כמו רש״י ואונקלוס). פירוש גדול נכנס רק בקטעים המקושרים, " +
            "וגדול מאוד לא נכנס; את הגבולות משנים בהגדרות, ואת הבחירה לכל פירוש ב\"בחירה ידנית\".",
        state.commentaries,
        trailing = {
            if (state.commentaries && candidates > 0) Link("בחירה ידנית ($candidates)", { state.view = AppState.View.Commentaries })
        },
    ) {
        state.commentaries = it
        changed()
    }
    OptionRow("חיפוש בטקסט", "אינדקס החיפוש של זית, רק לשורות שנכנסו: החיפוש מוצא בדיוק מה שהוא מוצא בספרייה המלאה.", state.textIndex) {
        state.textIndex = it
        changed()
    }
    OptionRow(
        "גם לפי משמעות",
        "שומר את הווקטורים של החיפוש הסמנטי (כ־225 בתים לשורה); הבנייה נמשכת בערך פי שניים. " +
            "רק זית במחשב משתמשת בהם, לא האפליקציה בטלפון.",
        state.keepVectors && state.textIndex,
        enabled = state.textIndex,
        modifier = Modifier.padding(start = 26.dp),
    ) {
        state.keepVectors = it
        changed()
    }
    OptionRow("הצעות ספרים ומחברים", "ההשלמה בשורת החיפוש, רק לספרים שבספרייה ולמחברים שלהם.", state.lookup) {
        state.lookup = it
        changed()
    }
}

@Composable
private fun Destination(state: AppState) {
    fun choose(a: AppState.After) {
        state.after = a
        state.saveOptions()
    }
    SegmentedControl(
        listOf(
            SegmentedControlButtonData(state.after == AppState.After.Separate, { Text("ספרייה נפרדת") }, { choose(AppState.After.Separate) }),
            SegmentedControlButtonData(state.after == AppState.After.Switch, { Text("זית תעבור אליה") }, { choose(AppState.After.Switch) }),
            SegmentedControlButtonData(state.after == AppState.After.Replace, { Text("החלפה מלאה") }, { choose(AppState.After.Replace) }),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    when (state.after) {
        AppState.After.Separate -> {
            Hint("נשמרת בתיקייה משלה. זית במחשב ממשיכה עם הספרייה שלה.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckboxRow("קיצור דרך לזית על הספרייה הזו", state.makeShortcut, {
                    state.makeShortcut = it
                    state.saveOptions()
                })
                Spacer(Modifier.width(6.dp))
                InfoTip("בשולחן העבודה ובתפריט התחל. פותח את זית עם פרופיל משלה (לשוניות, היסטוריה והערות נפרדות). כשזית כבר פתוחה, צריך לסגור אותה קודם.")
            }
        }
        AppState.After.Switch ->
            Hint("מהפתיחה הבאה זית במחשב משתמשת בספרייה המוקטנת, עם הפרופיל הרגיל שלך. הספרייה המלאה נשארת בדיסק, ואפשר לחזור אליה מההגדרות.")
        AppState.After.Replace -> {
            Row(verticalAlignment = Alignment.Top) {
                Icon(AllIconsKeys.General.Warning, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "הספרייה של זית במחשב (${size(state.zayitLibrarySize)}) תימחק, והמוקטנת תיכנס במקומה. ההערות, ההגדרות והלשוניות נשארות. " +
                        "כדי לחזור צריך להוריד שוב את הספרייה המלאה (כ־5.5 GB).",
                    fontSize = 12.sp,
                )
            }
            CheckboxRow("אני מבין שהספרייה המלאה תימחק", state.replaceConfirmed, { state.replaceConfirmed = it })
        }
    }
    if (state.after != AppState.After.Replace) {
        val scope = rememberCoroutineScope()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("נשמרת ב:", fontSize = 12.sp, color = secondary)
            Spacer(Modifier.width(6.dp))
            PathText(state.outParent.path, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Link("שינוי", {
                scope.launch {
                    FileKit.openDirectoryPicker(directory = PlatformFile(state.outParent.apply { mkdirs() }))?.file?.let {
                        state.outParent = it
                        state.saveOptions()
                    }
                }
            })
        }
    }
}

@Composable
private fun BuildFooter(state: AppState) {
    var details by remember { mutableStateOf(false) }
    if (state.building) {
        val p = state.progress
        if (p == null) IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth()) else HorizontalProgressBar(p, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.step, fontSize = 12.sp, color = secondary, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { state.cancel() }) { Text("עצירה") }
        }
    } else {
        DefaultButton(
            onClick = { state.build() },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            enabled = state.library != null && state.selected.isNotEmpty() && (state.after != AppState.After.Replace || state.replaceConfirmed),
        ) { Text(if (state.after == AppState.After.Replace) "יצירה והחלפת הספרייה של זית" else "יצירת הספרייה") }
        if (state.step.isNotEmpty()) Hint(state.step)
    }
    if (state.log.isNotEmpty()) {
        Link(if (details) "הסתרת הפרטים" else "פרטים", { details = !details })
        if (details || state.log.any { it.kind == LogLine.Kind.Error }) LogView(state)
    }
}

// ---------- the library just built ----------

@Composable
private fun ResultPage(
    state: AppState,
    r: Result,
) {
    var details by remember { mutableStateOf(false) }
    Page(
        title = if (state.replacedDone) "הספרייה של זית הוחלפה" else "הספרייה מוכנה",
        footer = {
            if (state.building) {
                val p = state.progress
                if (p == null) IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth()) else HorizontalProgressBar(p, Modifier.fillMaxWidth())
                Hint(state.step)
            }
            OutlinedButton(onClick = { state.newLibrary() }, modifier = Modifier.fillMaxWidth(), enabled = !state.building) { Text("ספרייה חדשה") }
        },
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AllIconsKeys.General.InspectionsOK, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("גודל", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(size(r.total), fontSize = 30.sp, color = accent)
            }
            PathText(r.dir.path)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for ((name, bytes) in r.parts) ValueRow(name, size(bytes), ltrLabel = true)
            }
            Link("פתיחת התיקייה", { state.openFolder(r.dir) })
        }
        if (state.replacePending && !state.building) {
            Section("ההחלפה עוד לא נעשתה") {
                Hint("ראו בפרטים למטה. אחרי שסוגרים את זית אפשר לנסות שוב.")
                DefaultButton(onClick = { state.replace(r.dir) }) { Text("החלפת הספרייה של זית עכשיו") }
            }
        }
        Section("לטלפון") {
            if (!state.pushOpen) {
                Hint("מחליף את הספרייה של זית לאנדרואיד בטלפון. דרושים חיבור USB וניפוי באגים.")
                DefaultButton(onClick = { state.openPush() }, enabled = !state.building) { Text("העברה לטלפון…") }
            } else {
                PushPanel(state, r)
            }
        }
        Section("במחשב") {
            Hint("פותח את זית על הספרייה הזו עם פרופיל משלה (רק כשזית סגורה).")
            OutlinedButton(onClick = { state.openInDesktop(r.dir) }, enabled = !state.building) { Text("פתיחה בזית למחשב") }
            state.desktopMessage?.let { Hint(it) }
        }
        if (state.log.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Link(if (details) "הסתרת הפרטים" else "פרטים", { details = !details })
                if (details || state.log.any { it.kind == LogLine.Kind.Error }) LogView(state)
            }
        }
    }
}

@Composable
private fun PushPanel(
    state: AppState,
    r: Result,
) {
    if (r.vectors > 0) {
        Hint("הספרייה כוללת את הווקטורים של החיפוש לפי משמעות (${size(r.vectors)}); האפליקציה בטלפון לא משתמשת בהם. לטלפון כדאי ספרייה בלעדיהם.")
    }
    state.deviceMessage?.let { Hint(it) }
    for (d in state.devices) {
        RadioButtonRow("${d.model}  (${d.serial})", state.device == d, onClick = { state.device = d })
    }
    state.device?.let { d -> Text("הספרייה של זית ב־${d.model} תוחלף בזו (${size(r.total)}). זית בטלפון ייסגר לזמן ההעברה.", fontSize = 12.sp) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DefaultButton(onClick = { state.push() }, enabled = state.device != null && !state.building) { Text("העברה") }
        OutlinedButton(onClick = { state.refreshDevices() }, enabled = !state.building) { Text("רענון") }
        OutlinedButton(onClick = { state.pushOpen = false }, enabled = !state.building) { Text("ביטול") }
    }
}

// ---------- settings ----------

@Composable
private fun SettingsPage(state: AppState) {
    val scope = rememberCoroutineScope()
    Page(
        title = "הגדרות",
        trailing = {
            // Where the gear was: the same spot opens the settings and closes them
            OutlinedButton(onClick = { state.panel = AppState.Panel.Main }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AllIconsKeys.Actions.Forward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("חזרה")
                }
            }
        },
    ) {
        Section("ספריית המקור") {
            Hint("הספרייה שממנה נבנות הספריות המוקטנות. היא נקראת בלבד ולא משתנה.")
            PathText(state.sourceDir.path)
            val lib = state.library
            when {
                state.loading -> Hint("טוען…")
                lib != null -> Hint("מהדורה ${lib.versionText()} · %,d ספרים · %s".format(lib.books.size, size(lib.dbSize)))
                else -> Text("לא נמצאה ספריית זית כאן (${state.sourceError}).", fontSize = 12.sp, color = JewelTheme.globalColors.text.error)
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val f = FileKit.openFilePicker(type = FileKitType.File(setOf("db")), directory = PlatformFile(state.sourceDir))
                    f?.file?.parentFile?.let { state.load(it) }
                }
            }) { Text("בחירת seforim.db אחר…") }
        }
        Section("הספרייה של זית במחשב") { ZayitLibrary(state) }
        Section("שמירה") {
            Hint("ספרייה נפרדת נשמרת בתיקייה חדשה כאן.")
            PathText(state.outParent.path)
            OutlinedButton(onClick = {
                scope.launch {
                    FileKit.openDirectoryPicker(directory = PlatformFile(state.outParent.apply { mkdirs() }))?.file?.let {
                        state.outParent = it
                        state.saveOptions()
                    }
                }
            }) { Text("שינוי התיקייה…") }
        }
        Section("פירושים גדולים") {
            CheckboxRow("בלי גבולות: כל פירוש נכנס במלואו", state.noLimits, {
                state.noLimits = it
                state.saveOptions()
                state.refreshPreview()
            })
            NumberField("גדול מכך (שורות) לא נכנס", state.maxLines, state, enabled = !state.noLimits)
            NumberField("גדול מכך (שורות) נכנס רק בקטעים המקושרים", state.fullLines, state, enabled = !state.noLimits)
            Hint("ברירת המחדל 10,000 ו־3,000. את הבחירה לכל פירוש בנפרד עושים ב\"בחירה ידנית\" שליד \"פירושים ותרגומים\".")
        }
        // Zayit's license (AGPL v3, its section 7b terms) asks every work built on it to show this
        Text(
            "Powered by the technologies that drive Zayit — https://zayitapp.com/",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(textDirection = TextDirection.Ltr, textAlign = TextAlign.Center),
            fontSize = 11.sp,
            color = secondary,
        )
    }
}

/** Where desktop Zayit keeps its library, switching back from a small one, and moving it (to an external drive too). */
@Composable
private fun ZayitLibrary(state: AppState) {
    val scope = rememberCoroutineScope()
    val inDefault = state.zayitDir.canonicalFile == MoveLibrary.defaultDir().canonicalFile
    state.desktopLibrary?.let { lib ->
        Text("זית משתמשת עכשיו בספרייה מוקטנת:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        PathText(File(lib).parent ?: lib)
        OutlinedButton(onClick = { state.restoreDesktop() }) { Text("חזרה לספרייה של זית") }
        Spacer(Modifier.height(4.dp))
    }
    PathText(state.zayitDir.path)
    Hint(size(state.zayitLibrarySize) + (if (inDefault) " · במקום הרגיל" else " · מחוץ למקום הרגיל") + " · פנויים בכונן " + size(MoveLibrary.free(state.zayitDir)))
    val target = state.moveTarget
    if (target == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { FileKit.openDirectoryPicker()?.file?.let { state.chooseMoveTarget(it) } } }, enabled = !state.building) {
                Text("העברה למקום אחר…")
            }
            if (!inDefault) {
                OutlinedButton(onClick = { state.chooseMoveTarget(MoveLibrary.defaultDir()) }, enabled = !state.building) { Text("החזרה למקום הרגיל") }
            }
        }
    } else {
        val free = remember(target) { MoveLibrary.free(target) }
        val enough = free > state.zayitLibrarySize + 200L * 1024 * 1024
        Text("העברה אל:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        PathText(target.path)
        Text(
            "נדרשים " + size(state.zayitLibrarySize) + ", פנויים " + size(free) + (if (enough) "" else " — אין מספיק מקום"),
            fontSize = 12.sp,
            color = if (enough) secondary else JewelTheme.globalColors.text.error,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hint("זית צריכה להיות סגורה. מעתיקים ובודקים, ורק אז זית עוברת לשם והעותק הישן נמחק.", Modifier.weight(1f))
            InfoTip("בכונן חיצוני: הוא חייב להיות מחובר (באותה אות כונן) כשזית נפתחת, ובדיסק־און־קי החיפוש איטי יותר. המפתח של זית לא תומך רשמית בספרייה מחוץ למקום הרגיל. ההערות וההגדרות נשארות במחשב.")
        }
        if (state.moving) HorizontalProgressBar(state.moveProgress, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.moving) {
                OutlinedButton(onClick = { state.cancelMove() }) { Text("עצירה") }
            } else {
                DefaultButton(onClick = { state.startMove() }, enabled = enough && !state.building) { Text("העברה") }
                OutlinedButton(onClick = { state.moveTarget = null }) { Text("ביטול") }
            }
        }
    }
    state.moveMessage?.let { Text(it, fontSize = 12.sp) }
}
