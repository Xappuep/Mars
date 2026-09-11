package com.mars.planner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Группы тем «Рубежа». */
enum class ThemeGroup(val labelRu: String) {
    FUTURISTIC("Футуристичный"),
    FATALISTIC("Фаталистичный"),
    RETROFUTURISTIC("Ретрофутуристичный")
}

/**
 * Семь тем «Рубежа». `id` совпадает с идентификатором на ПК, чтобы выбор
 * пользователя читался одинаково в обеих программах (тема не синхронизируется).
 */
enum class ThemeId(
    val id: String,
    val displayNameRu: String,
    val group: ThemeGroup
) {
    ORBIT("orbit", "Орбита", ThemeGroup.FUTURISTIC),
    NEBULA("nebula", "Туманность", ThemeGroup.FUTURISTIC),
    WHITE_STATION("white-station", "Белая станция", ThemeGroup.FUTURISTIC),
    ASH_AMBER("ash-amber", "Пепел и янтарь", ThemeGroup.FATALISTIC),
    POLAR_NIGHT("polar-night", "Полярная ночь", ThemeGroup.FATALISTIC),
    LIGHT_CONCRETE("light-concrete", "Светлый бетон", ThemeGroup.FATALISTIC),
    BUNKER("shelter-terminal", "Убежище", ThemeGroup.RETROFUTURISTIC);

    companion object {
        val Default = ORBIT

        fun fromId(raw: String?): ThemeId {
            val key = raw?.trim()?.lowercase().orEmpty()
            if (key == "bunker") return BUNKER
            return entries.find { it.id == key } ?: Default
        }
    }
}

/** Визуальные токены темы, адаптированные под мобильный экран. */
data class AppThemeTokens(
    val themeId: ThemeId,
    val bg: Color,
    val panel: Color,
    val raised: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentAlt: Color,
    val onAccent: Color,
    val danger: Color,
    val warning: Color,
    val ok: Color,
    val radiusPanel: Dp,
    val radiusControl: Dp,
    val glow: Boolean,
    val grid: Boolean,
    val sharp: Boolean,
    val crtLines: Boolean,
    val segmented: Boolean,
    val letterSpacingSp: Float,
    val labelCaps: Boolean,
    val monoHeadings: Boolean,
    val isLight: Boolean,
    val panelFillAlpha: Float,
    val cardFillAlpha: Float,
    val dialogFillAlpha: Float,
    /** Насыщенность декоративных эффектов, 0f..1f. */
    val effectIntensity: Float = 1f
) {
    val name: String get() = themeId.displayNameRu

    val panelFill: Color get() = panel.copy(alpha = panelFillAlpha)
    val cardFill: Color get() = raised.copy(alpha = cardFillAlpha)
    val cardFillSelected: Color get() = raised.copy(alpha = minOf(1f, cardFillAlpha + 0.18f))
    val dialogFill: Color get() = panel.copy(alpha = dialogFillAlpha)

    /** Мягкая подсветка акцента; отключается темами без `glow`. */
    fun accentGlow(intensity: Float = effectIntensity): Color =
        if (!glow) Color.Transparent
        else accent.copy(alpha = (0.22f * intensity).coerceIn(0f, 1f))

    fun panelShape(): RoundedCornerShape = RoundedCornerShape(radiusPanel)
    fun controlShape(): RoundedCornerShape = RoundedCornerShape(radiusControl)

    fun colorScheme(): ColorScheme {
        val base = if (isLight) lightColorScheme() else darkColorScheme()
        return base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = if (isLight) 0.18f else 0.26f),
            onPrimaryContainer = text,
            secondary = accentAlt,
            onSecondary = onAccent,
            secondaryContainer = accentAlt.copy(alpha = if (isLight) 0.18f else 0.26f),
            onSecondaryContainer = text,
            tertiary = ok,
            onTertiary = onAccent,
            background = bg,
            onBackground = text,
            surface = panel,
            onSurface = text,
            surfaceVariant = raised,
            onSurfaceVariant = textMuted,
            surfaceTint = accent,
            inverseSurface = text,
            inverseOnSurface = bg,
            outline = border,
            outlineVariant = border.copy(alpha = 0.55f),
            error = danger,
            onError = onAccent,
            errorContainer = danger.copy(alpha = if (isLight) 0.16f else 0.24f),
            onErrorContainer = text,
            scrim = Color.Black
        )
    }

    fun shapes(): Shapes = Shapes(
        extraSmall = RoundedCornerShape(maxOf(1.dp, radiusControl / 2)),
        small = controlShape(),
        medium = controlShape(),
        large = panelShape(),
        extraLarge = RoundedCornerShape(radiusPanel + 4.dp)
    )
}

/**
 * Токены активной темы «Рубежа». Насыщенность эффектов лежит в
 * [AppThemeTokens.effectIntensity] — отдельный CompositionLocal не нужен.
 */
val LocalAppTheme = staticCompositionLocalOf { AppThemes.tokens(ThemeId.Default) }

object AppThemes {

    val all: List<ThemeId> = ThemeId.entries.toList()

    fun tokens(themeId: ThemeId): AppThemeTokens = when (themeId) {
        ThemeId.ORBIT -> AppThemeTokens(
            themeId = themeId,
            bg = Color(0xFF050B16),
            panel = Color(0xFF0A1628),
            raised = Color(0xFF12243A),
            border = Color(0xFF2A4A68),
            text = Color(0xFFE8F6FF),
            textMuted = Color(0xFF8FB0CC),
            accent = Color(0xFF4DE8F0),
            accentAlt = Color(0xFF8B7CFF),
            onAccent = Color(0xFF041820),
            danger = Color(0xFFFF7A6E),
            warning = Color(0xFFF0C05A),
            ok = Color(0xFF6FE0A0),
            radiusPanel = 16.dp,
            radiusControl = 10.dp,
            glow = true,
            grid = true,
            sharp = false,
            crtLines = false,
            segmented = true,
            letterSpacingSp = 0.4f,
            labelCaps = false,
            monoHeadings = false,
            isLight = false,
            panelFillAlpha = 0.72f,
            cardFillAlpha = 0.80f,
            dialogFillAlpha = 0.96f
        )

        ThemeId.NEBULA -> AppThemeTokens(
            themeId = themeId,
            bg = Color(0xFF0C0818),
            panel = Color(0xFF17102C),
            raised = Color(0xFF261A42),
            border = Color(0xFF5A4580),
            text = Color(0xFFF6F0FF),
            textMuted = Color(0xFFB9A8D4),
            accent = Color(0xFFD4B0FF),
            accentAlt = Color(0xFF6EE8F5),
            onAccent = Color(0xFF1A0C30),
            danger = Color(0xFFFF8CA0),
            warning = Color(0xFFE8C070),
            ok = Color(0xFF80E0B8),
            radiusPanel = 18.dp,
            radiusControl = 12.dp,
            glow = true,
            grid = true,
            sharp = false,
            crtLines = false,
            segmented = false,
            letterSpacingSp = 0.6f,
            labelCaps = false,
            monoHeadings = false,
            isLight = false,
            panelFillAlpha = 0.74f,
            cardFillAlpha = 0.82f,
            dialogFillAlpha = 0.96f
        )

        ThemeId.WHITE_STATION -> AppThemeTokens(
            themeId = themeId,
            bg = Color(0xFFE4EEF7),
            panel = Color(0xFFF7FBFF),
            raised = Color(0xFFD7E4F2),
            border = Color(0xFF9BB4CC),
            text = Color(0xFF0F2A42),
            textMuted = Color(0xFF3F5A72),
            // Холодный голубой акцент (часть 3, мобильный прототип).
            accent = Color(0xFF2B9BC8),
            accentAlt = Color(0xFF4AA8D4),
            onAccent = Color(0xFFFFFFFF),
            danger = Color(0xFFB01818),
            warning = Color(0xFF8A5A00),
            ok = Color(0xFF157A42),
            radiusPanel = 14.dp,
            radiusControl = 8.dp,
            glow = true,
            grid = true,
            sharp = false,
            crtLines = false,
            segmented = true,
            letterSpacingSp = 0.2f,
            labelCaps = false,
            monoHeadings = false,
            isLight = true,
            panelFillAlpha = 0.88f,
            cardFillAlpha = 0.90f,
            dialogFillAlpha = 0.96f
        )

        ThemeId.ASH_AMBER -> AppThemeTokens(
            themeId = themeId,
            bg = Color(0xFF0C0E10),
            panel = Color(0xFF15181C),
            raised = Color(0xFF1C2126),
            border = Color(0xFF3A4046),
            text = Color(0xFFF0F2F4),
            textMuted = Color(0xFFA8AEB4),
            accent = Color(0xFFE8944A),
            accentAlt = Color(0xFFB06830),
            onAccent = Color(0xFF140E08),
            danger = Color(0xFFD86858),
            warning = Color(0xFFC8A858),
            ok = Color(0xFF6E9870),
            radiusPanel = 2.dp,
            radiusControl = 2.dp,
            glow = false,
            grid = false,
            sharp = true,
            crtLines = false,
            segmented = true,
            letterSpacingSp = 0.8f,
            labelCaps = true,
            monoHeadings = false,
            isLight = false,
            panelFillAlpha = 0.76f,
            cardFillAlpha = 0.84f,
            dialogFillAlpha = 0.96f
        )

        ThemeId.POLAR_NIGHT -> AppThemeTokens(
            themeId = themeId,
            bg = Color(0xFF070E14),
            panel = Color(0xFF0E161E),
            raised = Color(0xFF152028),
            border = Color(0xFF2A3A48),
            text = Color(0xFFE2ECF4),
            textMuted = Color(0xFF8498A8),
            accent = Color(0xFF5AA8D0),
            accentAlt = Color(0xFF3E78A8),
            onAccent = Color(0xFF050C12),
            danger = Color(0xFFC86868),
            warning = Color(0xFFB8A058),
            ok = Color(0xFF5EA888),
            radiusPanel = 2.dp,
            radiusControl = 2.dp,
            glow = false,
            grid = false,
            sharp = true,
            crtLines = false,
            segmented = false,
            letterSpacingSp = 0.5f,
            labelCaps = true,
            monoHeadings = false,
            isLight = false,
            panelFillAlpha = 0.76f,
            cardFillAlpha = 0.84f,
            dialogFillAlpha = 0.96f
        )

        ThemeId.LIGHT_CONCRETE -> AppThemeTokens(
            themeId = themeId,
            bg = Color(0xFFE2E0DA),
            panel = Color(0xFFF0EEE8),
            raised = Color(0xFFD6D2CA),
            border = Color(0xFFA8A298),
            text = Color(0xFF181614),
            textMuted = Color(0xFF585652),
            // Тёплый медно-бетонный акцент (часть 3); без голубого свечения.
            accent = Color(0xFFB87333),
            accentAlt = Color(0xFFC4894A),
            onAccent = Color(0xFFFFFFFF),
            danger = Color(0xFF7A2418),
            warning = Color(0xFF6A4E10),
            ok = Color(0xFF286038),
            radiusPanel = 4.dp,
            radiusControl = 2.dp,
            glow = false,
            grid = false,
            sharp = true,
            crtLines = false,
            segmented = true,
            letterSpacingSp = 0.3f,
            labelCaps = false,
            monoHeadings = false,
            isLight = true,
            panelFillAlpha = 0.90f,
            cardFillAlpha = 0.92f,
            dialogFillAlpha = 0.96f
        )

        ThemeId.BUNKER -> AppThemeTokens(
            themeId = themeId,
            bg = Color(0xFF050A05),
            panel = Color(0xFF0A140A),
            raised = Color(0xFF0F1C0F),
            border = Color(0xFF1E3A1E),
            text = Color(0xFF7CFF7C),
            textMuted = Color(0xFF3A8A3A),
            accent = Color(0xFF4CFF4C),
            accentAlt = Color(0xFFC8A020),
            onAccent = Color(0xFF031003),
            danger = Color(0xFFE06020),
            warning = Color(0xFFD0A018),
            ok = Color(0xFF40C040),
            radiusPanel = 2.dp,
            radiusControl = 1.dp,
            glow = true,
            grid = true,
            sharp = true,
            crtLines = true,
            segmented = true,
            letterSpacingSp = 1.0f,
            labelCaps = true,
            monoHeadings = true,
            isLight = false,
            panelFillAlpha = 0.80f,
            cardFillAlpha = 0.86f,
            dialogFillAlpha = 0.97f
        )
    }

    fun tokens(rawId: String?): AppThemeTokens = tokens(ThemeId.fromId(rawId))

    /** Типографика темы: цвета текста, трекинг и моно-начертание «Убежища». */
    fun typography(tokens: AppThemeTokens): Typography {
        val heading = if (tokens.monoHeadings) FontFamily.Monospace else FontFamily.Serif
        val body = if (tokens.monoHeadings) FontFamily.Monospace else FontFamily.SansSerif
        val tracking = tokens.letterSpacingSp.sp
        return Typography(
            displayLarge = MarsBaseTypography.displayLarge.copy(
                fontFamily = heading,
                color = tokens.text,
                letterSpacing = tracking
            ),
            headlineLarge = MarsBaseTypography.headlineLarge.copy(
                fontFamily = heading,
                color = tokens.text,
                letterSpacing = tracking
            ),
            headlineMedium = MarsBaseTypography.headlineMedium.copy(
                fontFamily = heading,
                color = tokens.text,
                letterSpacing = tracking
            ),
            titleLarge = MarsBaseTypography.titleLarge.copy(
                fontFamily = body,
                color = tokens.text,
                letterSpacing = tracking
            ),
            titleMedium = MarsBaseTypography.titleMedium.copy(
                fontFamily = body,
                color = tokens.text,
                letterSpacing = tracking
            ),
            bodyLarge = MarsBaseTypography.bodyLarge.copy(
                fontFamily = body,
                color = tokens.text
            ),
            bodyMedium = MarsBaseTypography.bodyMedium.copy(
                fontFamily = body,
                color = tokens.textMuted
            ),
            labelLarge = MarsBaseTypography.labelLarge.copy(
                fontFamily = body,
                color = tokens.text,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = tracking
            )
        )
    }
}
