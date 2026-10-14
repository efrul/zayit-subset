// Copied verbatim from Zayit 0.6.4 (kdroidFilter/Zayit, SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/theme/IslandsComponentStyling.kt): the same Islands look.
package zayit.subset.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.styling.Default
import org.jetbrains.jewel.intui.standalone.styling.Editor
import org.jetbrains.jewel.intui.standalone.styling.Outlined
import org.jetbrains.jewel.intui.standalone.styling.Undecorated
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.styling.outlined
import org.jetbrains.jewel.intui.standalone.styling.undecorated
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.CheckboxMetrics
import org.jetbrains.jewel.ui.component.styling.CheckboxStyle
import org.jetbrains.jewel.ui.component.styling.ComboBoxColors
import org.jetbrains.jewel.ui.component.styling.ComboBoxMetrics
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.component.styling.DropdownColors
import org.jetbrains.jewel.ui.component.styling.DropdownMetrics
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.MenuMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.intui.standalone.styling.macOs
import org.jetbrains.jewel.intui.standalone.styling.macOsDark
import org.jetbrains.jewel.intui.standalone.styling.macOsLight
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.component.styling.TabColors
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaMetrics
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import kotlin.time.Duration.Companion.milliseconds

private val roundedCorner = CornerSize(8.dp)
private val checkboxCorner = CornerSize(5.dp)

/**
 * Builds a [ComponentStyling] for the Islands theme with rounded corners
 * on all interactive components (buttons, text fields, combo boxes, checkboxes, tooltips).
 */
@OptIn(ExperimentalFoundationApi::class)
fun islandsComponentStyling(
    isDark: Boolean,
    accent: Color,
): ComponentStyling =
    if (isDark) {
        ComponentStyling.dark(
            scrollbarStyle = ScrollbarStyle.macOsDark(scrollbarVisibility = ScrollbarVisibility.WhenScrolling.macOs()),
            linkStyle = LinkStyle.dark(colors = LinkColors.dark(content = accent, contentFocused = accent, contentPressed = accent, contentHovered = accent, contentVisited = accent)),
            defaultButtonStyle =
                accentDefaultButtonStyleDark(accent).let {
                    ButtonStyle.Default.dark(
                        colors = it.colors,
                        metrics = ButtonMetrics.default(cornerSize = roundedCorner),
                    )
                },
            menuStyle =
                accentMenuStyleDark(accent).let {
                    MenuStyle.dark(
                        colors = it.colors,
                        metrics = MenuMetrics.defaults(cornerSize = roundedCorner),
                    )
                },
            dropdownStyle =
                DropdownStyle.Default.dark(
                    colors = DropdownColors.Default.dark(borderFocused = accent),
                    metrics = DropdownMetrics.default(cornerSize = roundedCorner),
                    menuStyle =
                        accentMenuStyleDark(accent).let {
                            MenuStyle.dark(
                                colors = it.colors,
                                metrics = MenuMetrics.defaults(cornerSize = roundedCorner),
                            )
                        },
                ),
            undecoratedDropdownStyle =
                DropdownStyle.Undecorated.dark(
                    metrics = DropdownMetrics.undecorated(cornerSize = roundedCorner),
                    menuStyle =
                        accentMenuStyleDark(accent).let {
                            MenuStyle.dark(
                                colors = it.colors,
                                metrics = MenuMetrics.defaults(cornerSize = roundedCorner),
                            )
                        },
                ),
            outlinedButtonStyle =
                ButtonStyle.Outlined.dark(
                    metrics = ButtonMetrics.outlined(cornerSize = roundedCorner),
                ),
            textFieldStyle =
                TextFieldStyle.dark(
                    metrics = TextFieldMetrics.defaults(cornerSize = roundedCorner),
                ),
            textAreaStyle =
                TextAreaStyle.dark(
                    metrics = TextAreaMetrics.defaults(cornerSize = roundedCorner),
                ),
            comboBoxStyle =
                ComboBoxStyle.Default.dark(
                    colors = ComboBoxColors.Default.dark(borderFocused = accent),
                    metrics = ComboBoxMetrics.default(cornerSize = roundedCorner),
                ),
            simpleListItemStyle =
                SimpleListItemStyle.dark(
                    colors = SimpleListItemColors.dark(backgroundSelectedActive = accent.copy(alpha = 0.15f)),
                ),
            checkboxStyle =
                CheckboxStyle.dark(
                    metrics =
                        CheckboxMetrics.defaults(
                            outlineCornerSize = checkboxCorner,
                            outlineFocusedCornerSize = checkboxCorner,
                            outlineSelectedCornerSize = checkboxCorner,
                            outlineSelectedFocusedCornerSize = checkboxCorner,
                        ),
                ),
            defaultTabStyle =
                TabStyle.Default.dark(
                    colors = TabColors.Default.dark(underlineSelected = accent),
                ),
            editorTabStyle =
                TabStyle.Editor.dark(
                    colors = TabColors.Editor.dark(underlineSelected = accent),
                ),
            tooltipStyle =
                TooltipStyle.dark(
                    intUiTooltipMetrics =
                        TooltipMetrics.defaults(
                            cornerSize = roundedCorner,
                            regularDisappearDelay = 10000.milliseconds,
                            fullDisappearDelay = 30000.milliseconds,
                        ),
                ),
        )
    } else {
        ComponentStyling.light(
            scrollbarStyle = ScrollbarStyle.macOsLight(scrollbarVisibility = ScrollbarVisibility.WhenScrolling.macOs()),
            linkStyle = LinkStyle.light(colors = LinkColors.light(content = accent, contentFocused = accent, contentPressed = accent, contentHovered = accent, contentVisited = accent)),
            defaultButtonStyle =
                accentDefaultButtonStyleLight(accent).let {
                    ButtonStyle.Default.light(
                        colors = it.colors,
                        metrics = ButtonMetrics.default(cornerSize = roundedCorner),
                    )
                },
            menuStyle =
                accentMenuStyleLight(accent).let {
                    MenuStyle.light(
                        colors = it.colors,
                        metrics = MenuMetrics.defaults(cornerSize = roundedCorner),
                    )
                },
            dropdownStyle =
                DropdownStyle.Default.light(
                    colors = DropdownColors.Default.light(borderFocused = accent),
                    metrics = DropdownMetrics.default(cornerSize = roundedCorner),
                    menuStyle =
                        accentMenuStyleLight(accent).let {
                            MenuStyle.light(
                                colors = it.colors,
                                metrics = MenuMetrics.defaults(cornerSize = roundedCorner),
                            )
                        },
                ),
            undecoratedDropdownStyle =
                DropdownStyle.Undecorated.light(
                    metrics = DropdownMetrics.undecorated(cornerSize = roundedCorner),
                    menuStyle =
                        accentMenuStyleLight(accent).let {
                            MenuStyle.light(
                                colors = it.colors,
                                metrics = MenuMetrics.defaults(cornerSize = roundedCorner),
                            )
                        },
                ),
            outlinedButtonStyle =
                ButtonStyle.Outlined.light(
                    metrics = ButtonMetrics.outlined(cornerSize = roundedCorner),
                ),
            textFieldStyle =
                TextFieldStyle.light(
                    metrics = TextFieldMetrics.defaults(cornerSize = roundedCorner),
                ),
            textAreaStyle =
                TextAreaStyle.light(
                    metrics = TextAreaMetrics.defaults(cornerSize = roundedCorner),
                ),
            comboBoxStyle =
                ComboBoxStyle.Default.light(
                    colors = ComboBoxColors.Default.light(borderFocused = accent),
                    metrics = ComboBoxMetrics.default(cornerSize = roundedCorner),
                ),
            simpleListItemStyle =
                SimpleListItemStyle.light(
                    colors = SimpleListItemColors.light(backgroundSelectedActive = accent.copy(alpha = 0.12f)),
                ),
            checkboxStyle =
                CheckboxStyle.light(
                    metrics =
                        CheckboxMetrics.defaults(
                            outlineCornerSize = checkboxCorner,
                            outlineFocusedCornerSize = checkboxCorner,
                            outlineSelectedCornerSize = checkboxCorner,
                            outlineSelectedFocusedCornerSize = checkboxCorner,
                        ),
                ),
            defaultTabStyle =
                TabStyle.Default.light(
                    colors = TabColors.Default.light(underlineSelected = accent),
                ),
            editorTabStyle =
                TabStyle.Editor.light(
                    colors = TabColors.Editor.light(underlineSelected = accent),
                ),
            tooltipStyle =
                TooltipStyle.light(
                    intUiTooltipColors =
                        TooltipColors.light(
                            backgroundColor = IntUiLightTheme.colors.grayOrNull(13) ?: Color.White,
                            contentColor = IntUiLightTheme.colors.grayOrNull(2) ?: Color.Black,
                            borderColor = IntUiLightTheme.colors.grayOrNull(9) ?: Color.Gray,
                        ),
                    intUiTooltipMetrics =
                        TooltipMetrics.defaults(
                            cornerSize = roundedCorner,
                            regularDisappearDelay = 10000.milliseconds,
                            fullDisappearDelay = 30000.milliseconds,
                        ),
                ),
        )
    }
