package com.mars.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.domain.model.EffectIntensity

val MarsGraphite = Color(0xFF0E0E10)
val MarsGraphiteDeep = Color(0xFF08080A)
val MarsGraphiteMid = Color(0xFF121218)
val MarsGraphiteElevated = Color(0xFF1A1A1F)
val MarsGlass = Color(0xCC1E1E26)
val MarsAccentProgress = Color(0xFF7B8CFF)
val MarsAccentProgressSoft = Color(0x337B8CFF)
val MarsOverdueGlow = Color(0xFFFF5A36)
val MarsOverdueSoft = Color(0x33FF5A36)
val MarsGoldGlow = Color(0xFFFFB347)
val MarsOrange = Color(0xFFFF6A00)
val MarsOrangeSoft = Color(0x33FF6A00)
val MarsPeach = Color(0xFFFFB38A)
val MarsCardLight = Color(0xFFF7F2EC)
val MarsCardDark = Color(0xFF22222A)
val MarsWhite = Color(0xFFFFFFFF)
val MarsMuted = Color(0xFFB7B3AC)
val MarsOutline = Color(0xFF3A3A44)
val StatusDone = Color(0xFF3DDC97)
val StatusOpen = Color(0xFF6C8CFF)
val StatusOverdue = Color(0xFFFF5A36)
val StatusDanger = Color(0xFFE8455F)
val StatusProgress = MarsOrange
val StatusPostponed = MarsPeach
val StatusNotDone = Color(0xFFFF5A36)
val StatusCancelled = Color(0xFF8A8A93)
val StatusNew = StatusOpen

val MarsPresenceNeutral = Color(0xFF9B9B9B)

internal val MarsBaseTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
)

@Composable
fun MarsTheme(
    theme: AppTheme = AppTheme.ORBIT,
    intensity: EffectIntensity = EffectIntensity.NORMAL,
    content: @Composable () -> Unit
) {
    val palette = remember(theme) { paletteFor(theme) }
    val tokens = remember(theme, intensity) {
        AppThemes.tokens(ThemeId.fromId(theme.key)).copy(effectIntensity = intensity.factor)
    }
    val colors = remember(tokens) {
        if (tokens.isLight) {
            lightColorScheme(
                primary = tokens.accent,
                onPrimary = tokens.onAccent,
                secondary = tokens.accentAlt,
                onSecondary = tokens.onAccent,
                background = tokens.bg,
                onBackground = tokens.text,
                surface = tokens.panel,
                onSurface = tokens.text,
                surfaceVariant = tokens.raised,
                onSurfaceVariant = tokens.textMuted,
                error = tokens.danger,
                outline = tokens.border
            )
        } else {
            darkColorScheme(
                primary = tokens.accent,
                onPrimary = tokens.onAccent,
                secondary = tokens.accentAlt,
                onSecondary = tokens.onAccent,
                background = tokens.bg,
                onBackground = tokens.text,
                surface = tokens.panel,
                onSurface = tokens.text,
                surfaceVariant = tokens.raised,
                onSurfaceVariant = tokens.textMuted,
                error = tokens.danger,
                outline = tokens.border
            )
        }
    }
    val typography = remember(tokens) {
        Typography(
            displayLarge = MarsBaseTypography.displayLarge.copy(color = tokens.text),
            headlineLarge = MarsBaseTypography.headlineLarge.copy(color = tokens.text),
            headlineMedium = MarsBaseTypography.headlineMedium.copy(color = tokens.text),
            titleLarge = MarsBaseTypography.titleLarge.copy(color = tokens.text),
            titleMedium = MarsBaseTypography.titleMedium.copy(color = tokens.text),
            bodyLarge = MarsBaseTypography.bodyLarge.copy(color = tokens.text),
            bodyMedium = MarsBaseTypography.bodyMedium.copy(color = tokens.textMuted),
            labelLarge = MarsBaseTypography.labelLarge.copy(color = tokens.text)
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                tokens.bg.luminance() > 0.5f
        }
    }
    @Suppress("UNUSED_VARIABLE")
    val ignored = isSystemInDarkTheme()
    ProvideMarsAppearance(theme = theme, intensity = intensity) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = tokens.shapes(),
            content = content
        )
    }
}
