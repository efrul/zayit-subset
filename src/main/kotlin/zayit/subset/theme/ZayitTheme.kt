package zayit.subset.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import java.util.prefs.Preferences

/**
 * Zayit's theme (desktop 0.6.4, core/presentation/theme/ThemeUtils.kt and AccentColor.kt): the Islands palette,
 * the accent on checkboxes, buttons and focus rings, Noto Serif Hebrew as the default text, and the title bar tint.
 * Theme and accent are Zayit's own: read from its settings (java.util.prefs), so the tool looks as Zayit does here.
 */
object ZayitTheme {
    private val prefs = runCatching { Preferences.userRoot() }.getOrNull()

    private fun pref(
        key: String,
        default: String,
    ): String = runCatching { prefs?.get(key, default) }.getOrNull() ?: default

    /** Zayit's AccentColor: Gold unless changed in its settings ("System" falls back to Jewel's blue, as Zayit does without one). */
    fun accent(isDark: Boolean): Color =
        when (pref("accent_color", "Gold")) {
            "Teal" -> if (isDark) Color(0xFF2FC2B6) else Color(0xFF1A998E)
            "Green" -> if (isDark) Color(0xFF5AB869) else Color(0xFF3D9A50)
            "Default", "System" ->
                if (isDark) {
                    IntUiDarkTheme.colors.blueOrNull(6) ?: Color(0xFF3574F0)
                } else {
                    IntUiLightTheme.colors.blueOrNull(4) ?: Color(0xFF4682FA)
                }
            else -> if (isDark) Color(0xFFD4A843) else Color(0xFFBE9117)
        }

    /** Zayit's theme mode: light or dark as chosen there, or as Windows when it follows the system. */
    fun isDark(system: Boolean): Boolean =
        when (pref("theme_mode", "System")) {
            "Light", "LightWithLightHeader" -> false
            "Dark" -> true
            else -> system
        }

    /**
     * Zayit's text, drawn the way Windows draws its own (ClearType): sub-pixel edges, full hinting and glyphs on whole
     * pixels. Compose's default (slight hinting, sub-pixel positions) draws the same font softer.
     */
    @OptIn(ExperimentalTextApi::class)
    fun defaultTextStyle(): TextStyle =
        TextStyle(
            fontFamily = FontFamily(Font(resource = "font/notoserifhebrew.ttf")),
            platformStyle =
                PlatformTextStyle(
                    spanStyle = null,
                    paragraphStyle =
                        PlatformParagraphStyle(
                            FontRasterizationSettings(
                                smoothing = FontSmoothing.SubpixelAntiAlias,
                                hinting = FontHinting.Full,
                                subpixelPositioning = false,
                                autoHintingForced = false,
                            ),
                        ),
                ),
        )

    fun themeDefinition(
        isDark: Boolean,
        accent: Color,
    ): ThemeDefinition {
        val disabledValues = if (isDark) DisabledAppearanceValues.dark() else DisabledAppearanceValues.light()
        val iconData = accentIconData(accent, isDark)
        return if (isDark) {
            JewelTheme.darkThemeDefinition(
                colors = islandsDarkGlobalColors(accent),
                iconData = iconData,
                defaultTextStyle = defaultTextStyle(),
                disabledAppearanceValues = disabledValues,
            )
        } else {
            JewelTheme.lightThemeDefinition(
                colors = lightIslandsGlobalColors(accent),
                iconData = iconData,
                defaultTextStyle = defaultTextStyle(),
                disabledAppearanceValues = disabledValues,
            )
        }
    }

    /** The accent at 25% over the panel background: the title bar gradient tint. */
    @Composable
    fun titleBarGradientColor(): Color {
        val accent = JewelTheme.globalColors.outlines.focused
        val bg = JewelTheme.globalColors.panelBackground
        val t = 0.25f
        return Color(
            red = bg.red * (1f - t) + accent.red * t,
            green = bg.green * (1f - t) + accent.green * t,
            blue = bg.blue * (1f - t) + accent.blue * t,
            alpha = 1f,
        )
    }

    private fun islandsDarkGlobalColors(accent: Color): GlobalColors =
        GlobalColors.dark(
            borders =
                BorderColors.dark(
                    normal = Color(0xFF3C3F41),
                    focused = accent,
                    disabled = Color(0xFF2B2D30),
                ),
            outlines =
                OutlineColors.dark(
                    focused = accent,
                    focusedWarning = Color(0xFFE8A33E),
                    focusedError = Color(0xFFF75464),
                    warning = Color(0xFFE8A33E),
                    error = Color(0xFFF75464),
                ),
            text =
                TextColors.dark(
                    normal = Color(0xFFBCBEC4),
                    selected = Color(0xFFBCBEC4),
                    disabled = Color(0xFF7A7E85),
                    info = Color(0xFF7A7E85),
                    error = Color(0xFFF75464),
                ),
            panelBackground = Color(0xFF1E1F22),
            toolwindowBackground = Color(0xFF181A1D),
        )

    private fun lightIslandsGlobalColors(accent: Color): GlobalColors =
        GlobalColors.light(
            outlines =
                OutlineColors.light(
                    focused = accent,
                    focusedWarning = Color(0xFFE8A33E),
                    focusedError = Color(0xFFF75464),
                    warning = Color(0xFFE8A33E),
                    error = Color(0xFFF75464),
                ),
            borders = BorderColors.light(focused = accent),
            toolwindowBackground = Color(0xFFE8E9EB),
        )

    private fun accentIconData(
        accent: Color,
        isDark: Boolean,
    ): ThemeIconData {
        val hex = accent.toHexString()
        val base = if (isDark) IntUiDarkTheme.iconData else IntUiLightTheme.iconData
        return ThemeIconData(
            iconOverrides = base.iconOverrides,
            colorPalette =
                base.colorPalette +
                    mapOf(
                        "Checkbox.Background.Selected" to hex,
                        "Checkbox.Border.Selected" to hex,
                        "Checkbox.Focus.Thin.Selected" to hex,
                    ),
            selectionColorPalette = base.selectionColorPalette,
        )
    }

    private fun Color.toHexString(): String {
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }
}
