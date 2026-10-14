package zayit.subset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.TriStateCheckbox
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import zayit.subset.data.BookInfo
import zayit.subset.data.Cat
import zayit.subset.data.Mode
import zayit.subset.data.normalize
import zayit.subset.data.size
import java.io.File


/** The window body: Zayit's Islands, two cards on the canvas color. */
@Composable
fun MainScreen(state: AppState) {
    Row(
        Modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.toolwindowBackground)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Island(Modifier.weight(1f)) { BooksPane(state) }
        Island(Modifier.width(440.dp)) { SidePanel(state) }
    }
}

@Composable
private fun Island(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxHeight()
            .padding(top = 4.dp, bottom = 6.dp, start = 4.dp, end = 4.dp)
            .clip(IslandShape)
            .background(JewelTheme.globalColors.panelBackground),
    ) { content() }
}


// ---------- books ----------

private sealed interface Row {
    data class CatRow(val cat: Cat, val depth: Int) : zayit.subset.ui.Row

    data class BookRow(val book: BookInfo, val depth: Int, val withPath: Boolean) : zayit.subset.ui.Row

    data class CommentaryRow(val book: BookInfo) : zayit.subset.ui.Row
}

@Composable
private fun BooksPane(state: AppState) {
    val lib = state.library
    var query by remember { mutableStateOf("") }
    LaunchedEffect(state.search) {
        snapshotFlow { state.search.text.toString() }.debounce(200).collect { query = it }
    }
    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
        Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("אילו ספרים להכניס", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        TextField(
            state = state.search,
            style = rememberPillTextFieldStyle(),
            modifier = Modifier.fillMaxWidth().height(40.dp),
            placeholder = { Text("חיפוש ספר לפי שם…") },
            leadingIcon = { Icon(AllIconsKeys.Actions.Find, null, Modifier.padding(end = 6.dp)) },
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            fun show(v: AppState.View) {
                state.view = v
                state.search.edit { replace(0, length, "") }
            }
            val candidates = state.plan?.candidates?.size ?: 0
            val buttons =
                buildList {
                    add(SegmentedControlButtonData(state.view == AppState.View.Tree, { Text("כל הספרים") }, { show(AppState.View.Tree) }))
                    add(SegmentedControlButtonData(state.view == AppState.View.Selected, { Text("נבחרו (${state.selected.size})") }, { show(AppState.View.Selected) }))
                    if (state.commentaries && candidates > 0) {
                        add(SegmentedControlButtonData(state.view == AppState.View.Commentaries, { Text("פירושים ($candidates)") }, { show(AppState.View.Commentaries) }))
                    }
                }
            SegmentedControl(buttons)
            Spacer(Modifier.weight(1f))
            if (state.view == AppState.View.Commentaries) {
                Link("הכול במלואו", { state.allFull() })
                if (state.overrides.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Link("חזרה לבחירה האוטומטית", { state.resetModes() })
                }
            } else if (state.selected.isNotEmpty()) {
                Link("ניקוי הבחירה", { state.clearSelection() })
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Centered("טוען את רשימת הספרים…")
                lib == null -> Centered("אין ספרייה")
                else -> {
                    val q = normalize(query.trim())
                    val rows: List<zayit.subset.ui.Row> =
                        when {
                            q.isNotEmpty() ->
                                lib.books.values
                                    .filter { q in it.key }
                                    .sortedWith(compareBy({ if (it.key.startsWith(q)) 0 else 1 }, { it.key.length }, { it.order }))
                                    .take(300)
                                    .map { Row.BookRow(it, 0, true) }
                            state.view == AppState.View.Selected ->
                                state.selected.mapNotNull { lib.books[it] }
                                    .sortedWith(compareBy({ it.path }, { it.order }))
                                    .map { Row.BookRow(it, 0, true) }
                            state.view == AppState.View.Commentaries ->
                                state.plan?.candidates.orEmpty().mapNotNull { lib.books[it] }
                                    .sortedWith(compareBy({ it.path }, { it.order }))
                                    .map { Row.CommentaryRow(it) }
                            else -> flatten(lib.roots, state.expanded)
                        }
                    if (rows.isEmpty()) {
                        Centered(if (q.isNotEmpty()) "לא נמצאו ספרים בשם הזה" else "עוד לא נבחרו ספרים")
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(Modifier.fillMaxSize().padding(top = 2.dp), state = listState) {
                            items(
                                rows,
                                key = { r ->
                                    when (r) {
                                        is Row.CatRow -> "c" + r.cat.id
                                        is Row.BookRow -> "b" + r.book.id
                                        is Row.CommentaryRow -> "m" + r.book.id
                                    }
                                },
                            ) { r ->
                                when (r) {
                                    is Row.CatRow -> CatItem(state, r.cat, r.depth)
                                    is Row.BookRow -> BookItem(state, r.book, r.depth, r.withPath)
                                    is Row.CommentaryRow -> CommentaryItem(state, r.book)
                                }
                            }
                        }
                        VerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                }
            }
        }
    }
}

private fun flatten(
    cats: List<Cat>,
    expanded: Set<Long>,
    depth: Int = 0,
    into: MutableList<zayit.subset.ui.Row> = ArrayList(),
): List<zayit.subset.ui.Row> {
    for (c in cats) {
        if (c.all.isEmpty()) continue
        into.add(Row.CatRow(c, depth))
        if (c.id in expanded) {
            flatten(c.kids, expanded, depth + 1, into)
            for (b in c.books) into.add(Row.BookRow(b, depth + 1, false))
        }
    }
    return into
}

@Composable
private fun HoverRow(
    depth: Int,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(if (hovered) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .padding(start = (6 + depth * 20).dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CatItem(
    state: AppState,
    cat: Cat,
    depth: Int,
) {
    val open = cat.id in state.expanded
    HoverRow(depth, onClick = { state.toggleExpanded(cat) }) {
        Icon(if (open) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronLeft, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        TriStateCheckbox(
            state =
                when (state.catState(cat)) {
                    2 -> ToggleableState.On
                    1 -> ToggleableState.Indeterminate
                    else -> ToggleableState.Off
                },
            onClick = { state.toggleCat(cat) },
        )
        Spacer(Modifier.width(8.dp))
        Icon(AllIconsKeys.Nodes.Folder, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(cat.title, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        val n = state.selectedIn(cat)
        Text(if (n > 0) "‎$n / ${cat.all.size}" else "‎${cat.all.size}", color = secondary, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        state.weights(cat)?.let { (chosen, all) ->
            WeightText(if (n > 0) size(chosen) + " / " + size(all) else size(all), strong = n > 0)
        }
    }
}

@Composable
private fun BookItem(
    state: AppState,
    book: BookInfo,
    depth: Int,
    withPath: Boolean,
) {
    HoverRow(depth + if (withPath) 0 else 1, onClick = { state.toggleBook(book.id) }) {
        Checkbox(checked = book.id in state.selected, onCheckedChange = { state.toggleBook(book.id) })
        Spacer(Modifier.width(8.dp))
        Icon(BookIcon, null, Modifier.size(16.dp), tint = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(book.title)
                Spacer(Modifier.width(8.dp))
                Text("‎%,d שורות".format(book.lines), color = secondary, fontSize = 12.sp)
            }
            if (withPath) Text(book.path, color = secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        state.weight(book.id)?.let { WeightText(size(it), strong = book.id in state.selected) }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = secondary) }
}


/** A row's weight, in a column at the row's far side: what it adds to the small library (without its commentaries). */
@Composable
private fun WeightText(
    text: String,
    strong: Boolean,
) {
    Text(
        text,
        modifier = Modifier.padding(start = 12.dp, end = 10.dp),
        fontSize = 12.sp,
        color = if (strong) accent else secondary,
        fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
    )
}

/** A commentary or targum that joins the chosen books: how it goes in, by hand or as the limits choose. */
@Composable
private fun CommentaryItem(
    state: AppState,
    book: BookInfo,
) {
    val mode = state.modeOf(book.id) ?: return
    val auto = state.autoModeOf(book.id)
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(book.title, color = if (mode == Mode.Out) secondary else JewelTheme.globalColors.text.normal)
                Spacer(Modifier.width(8.dp))
                Text("\u200E%,d שורות".format(book.lines), color = secondary, fontSize = 12.sp)
                if (book.id in state.overrides) Text("  ·  נבחר ידנית", color = JewelTheme.globalColors.outlines.focused, fontSize = 12.sp)
            }
            Text(book.path, color = secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((m, label) in listOf(Mode.Full to "במלואו", Mode.Linked to "רק קטעים מקושרים", Mode.Out to "בלי")) {
                ModeChip(label + if (m == auto) " ·" else "", selected = mode == m) { state.setMode(book.id, m) }
            }
        }
        val w = state.weight(book.id)
        WeightText(
            when (mode) {
                Mode.Full -> if (w != null) size(w) else ""
                Mode.Linked -> "חלקי"
                Mode.Out -> "—"
            },
            strong = mode != Mode.Out,
        )
    }
}

/** One of a commentary's three choices; the dot marks what the limits would choose. */
@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    Box(
        Modifier
            .clip(RowShape)
            .border(1.dp, if (selected) accent else JewelTheme.globalColors.borders.normal, RowShape)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}
