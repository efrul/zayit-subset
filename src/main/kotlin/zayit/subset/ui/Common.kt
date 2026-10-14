@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package zayit.subset.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.debounce
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.textFieldStyle

internal val IslandShape = RoundedCornerShape(12.dp)
internal val CardShape = RoundedCornerShape(10.dp)
internal val RowShape = RoundedCornerShape(6.dp)

/** Zayit's home widgets' corners. */
internal val WidgetShape = RoundedCornerShape(18.dp)

internal val secondary @Composable get() = JewelTheme.globalColors.text.info

internal val accent @Composable get() = JewelTheme.globalColors.outlines.focused

/** Zayit's own pictures: the olive branch of its logo and its home page's wallpaper (desktop 0.6.4). */
internal object Art {
    val branch: ImageBitmap by lazy { load("/drawable/zayit_logo_branch.png") }
    val wallpaper: ImageBitmap by lazy { load("/drawable/wallpaper.jpg") }
    val wallpaperDark: ImageBitmap by lazy { load("/drawable/wallpaper_dark.jpg") }

    private fun load(path: String): ImageBitmap =
        org.jetbrains.skia.Image
            .makeFromEncoded(Art::class.java.getResourceAsStream(path)!!.use { it.readBytes() })
            .toComposeImageBitmap()
}

/** The olive branch of Zayit's logo. */
@Composable
internal fun Branch(modifier: Modifier = Modifier) {
    val painter = remember { BitmapPainter(Art.branch, filterQuality = FilterQuality.High) }
    Image(painter, contentDescription = null, modifier = modifier)
}

/**
 * Zayit's home wallpaper, olive trees on a hillside, fading into the panel as Zayit's home page does: seen at the top,
 * the panel's own colour by the bottom, so the text over it stays readable.
 */
@Composable
internal fun Backdrop(modifier: Modifier = Modifier) {
    val panel = JewelTheme.globalColors.panelBackground
    val dark = JewelTheme.isDark
    val painter = remember(dark) { BitmapPainter(if (dark) Art.wallpaperDark else Art.wallpaper, filterQuality = FilterQuality.Medium) }
    Box(modifier) {
        Image(painter, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.00f to panel.copy(alpha = 0.20f),
                    0.35f to panel.copy(alpha = 0.40f),
                    0.70f to panel.copy(alpha = 0.80f),
                    1.00f to panel,
                ),
            ),
        )
    }
}

/** Zayit's PanelCard (its home widgets): rounded, a soft border, the panel's colour, a little see-through over the wallpaper. */
@Composable
internal fun Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = JewelTheme.isDark
    val border = if (dark) JewelTheme.globalColors.borders.disabled else JewelTheme.globalColors.borders.normal
    Column(
        modifier
            .fillMaxWidth()
            .clip(WidgetShape)
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = if (dark) 0.72f else 0.82f), WidgetShape)
            .border(1.5.dp, border, WidgetShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** A card with a title, as Zayit's widgets ("היסטוריה", "מועדפים"). */
@Composable
internal fun Section(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

/** A name and its value at the far side, as the times in Zayit's zmanim widget. */
@Composable
internal fun ValueRow(
    label: String,
    value: String,
    ltrLabel: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 13.sp,
            color = secondary,
            modifier = Modifier.weight(1f),
            style = if (ltrLabel) TextStyle(textDirection = TextDirection.Ltr, textAlign = TextAlign.Right) else TextStyle.Default,
        )
        Text(value, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

/** The theme's text field, pill-shaped, as Zayit's search bars (its PillTextFieldStyle). */
@Composable
internal fun rememberPillTextFieldStyle(): TextFieldStyle {
    val base = JewelTheme.textFieldStyle
    return remember(base) {
        TextFieldStyle(
            colors = base.colors,
            metrics =
                TextFieldMetrics(
                    borderWidth = base.metrics.borderWidth,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    cornerSize = CornerSize(50),
                    minSize = base.metrics.minSize,
                ),
            iconButtonStyle = base.iconButtonStyle,
        )
    }
}

/** A small (i) whose explanation shows on hover: the long texts stay out of the way. */
@Composable
internal fun InfoTip(text: String) {
    Tooltip(tooltip = { Text(text, modifier = Modifier.width(300.dp)) }) {
        Icon(AllIconsKeys.General.ContextHelp, contentDescription = text, modifier = Modifier.size(14.dp))
    }
}

/** A secondary line under a control. */
@Composable
internal fun Hint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, color = secondary, fontSize = 12.sp, modifier = modifier)
}

/** A file path inside Hebrew text: left to right, aligned with the Hebrew around it, cut at its start. */
@Composable
internal fun PathText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(textDirection = TextDirection.Ltr, textAlign = TextAlign.Right),
        fontSize = 12.sp,
        color = secondary,
        maxLines = 1,
        overflow = TextOverflow.StartEllipsis,
    )
}

@Composable
internal fun NumberField(
    label: String,
    field: TextFieldState,
    state: AppState,
    enabled: Boolean = true,
) {
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.debounce(400).collect {
            state.saveOptions()
            state.refreshPreview()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = if (enabled) JewelTheme.globalColors.text.normal else secondary)
        TextField(state = field, modifier = Modifier.width(90.dp), enabled = enabled)
    }
}

/** The build's log: what was done, with sizes, and any error in red. */
@Composable
internal fun LogView(state: AppState) {
    if (state.log.isEmpty()) return
    val scroll = rememberScrollState()
    LaunchedEffect(state.log.size) { scroll.animateScrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (line in state.log) {
            Text(
                line.text,
                fontSize = 12.sp,
                color =
                    when (line.kind) {
                        LogLine.Kind.Info -> secondary
                        LogLine.Kind.Error -> JewelTheme.globalColors.text.error
                        LogLine.Kind.Success -> accent
                        LogLine.Kind.Normal -> JewelTheme.globalColors.text.normal
                    },
            )
        }
    }
}
