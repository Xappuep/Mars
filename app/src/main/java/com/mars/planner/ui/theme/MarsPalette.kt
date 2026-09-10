package com.mars.planner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.domain.model.EffectIntensity

/**
 * Палитра для экранов, которые ещё читают акцент/фон напрямую.
 * Цвета берутся из токенов тем «Рубежа» ([AppThemes]).
 */
data class MarsPalette(
    val theme: AppTheme,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val highlight: Color,
    val backgroundDeep: Color,
    val backgroundMid: Color,
    val background: Color,
    val backgroundElevated: Color,
    val card: Color,
    val glass: Color,
    val text: Color,
    val textMuted: Color,
    val isLight: Boolean
)

fun paletteFor(theme: AppTheme): MarsPalette {
    val tokens = AppThemes.tokens(ThemeId.fromId(theme.key))
    return MarsPalette(
        theme = theme,
        accent = tokens.accent,
        accentStrong = tokens.accentAlt,
        accentSoft = tokens.accent.copy(alpha = 0.20f),
        highlight = tokens.accentAlt,
        backgroundDeep = tokens.bg,
        backgroundMid = tokens.panel,
        background = tokens.bg,
        backgroundElevated = tokens.raised,
        card = tokens.panel.copy(alpha = tokens.cardFillAlpha.coerceIn(0.55f, 1f)),
        glass = tokens.panel.copy(alpha = tokens.panelFillAlpha.coerceIn(0.55f, 0.92f)),
        text = tokens.text,
        textMuted = tokens.textMuted,
        isLight = tokens.isLight
    )
}

val LocalMarsPalette = compositionLocalOf { paletteFor(AppTheme.ORBIT) }
val LocalEffectIntensity = compositionLocalOf { EffectIntensity.NORMAL }

@Composable
fun ProvideMarsAppearance(
    theme: AppTheme,
    intensity: EffectIntensity,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(theme)
    val tokens = AppThemes.tokens(ThemeId.fromId(theme.key))
        .copy(effectIntensity = intensity.factor)
    CompositionLocalProvider(
        LocalMarsPalette provides palette,
        LocalEffectIntensity provides intensity,
        LocalAppTheme provides tokens,
        content = content
    )
}
