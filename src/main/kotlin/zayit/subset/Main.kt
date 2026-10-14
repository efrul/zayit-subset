package zayit.subset

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle
import zayit.subset.theme.ZayitTheme
import zayit.subset.theme.islandsComponentStyling
import zayit.subset.ui.AppState
import zayit.subset.ui.MainScreen

const val APP_NAME = "ספרייה מוקטנת לזית"

fun main() =
    application {
        val state = remember { AppState() }
        val icon = remember { BitmapPainter(AppState::class.java.getResourceAsStream("/drawable/app.png")!!.use { loadImageBitmap(it) }) }
        val isDark = ZayitTheme.isDark(isSystemInDarkTheme())
        val accent = ZayitTheme.accent(isDark)
        IntUiTheme(
            theme = ZayitTheme.themeDefinition(isDark, accent),
            styling =
                islandsComponentStyling(isDark, accent).decoratedWindow(
                    windowStyle = if (isDark) DecoratedWindowStyle.dark() else DecoratedWindowStyle.light(),
                    titleBarStyle = if (isDark) TitleBarStyle.dark() else TitleBarStyle.lightWithLightHeader(),
                ),
            swingCompatMode = false,
        ) {
            DecoratedWindow(
                onCloseRequest = {
                    state.cancel()
                    exitApplication()
                },
                state = rememberWindowState(size = windowSize(), position = WindowPosition(Alignment.Center)),
                title = APP_NAME,
                icon = icon,
            ) {
                TitleBar(gradientStartColor = ZayitTheme.titleBarGradientColor()) {
                    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Image(icon, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(APP_NAME)
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainScreen(state)
                }
            }
        }
    }

/** 1120×760, or less on a smaller screen: the window must fit the work area (a 1080p screen at 150% has ~690dp). */
private fun windowSize(): DpSize {
    val area = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    return DpSize(minOf(1120, area.width - 40).dp, minOf(760, area.height - 24).dp)
}
