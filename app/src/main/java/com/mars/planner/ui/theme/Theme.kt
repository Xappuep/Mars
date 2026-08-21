package com.mars.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MarsGraphite = Color(0xFF0E0E10)
val MarsGraphiteElevated = Color(0xFF1A1A1F)
val MarsOrange = Color(0xFFFF6A00)
val MarsOrangeSoft = Color(0x33FF6A00)
val MarsPeach = Color(0xFFFFB38A)
val MarsCardLight = Color(0xFFF7F2EC)
val MarsCardDark = Color(0xFF22222A)
val MarsWhite = Color(0xFFFFFFFF)
val MarsMuted = Color(0xFFB7B3AC)
val StatusDone = Color(0xFF3DDC97)
val StatusProgress = MarsOrange
val StatusPostponed = MarsPeach
val StatusNotDone = Color(0xFFFF5A36)
val StatusCancelled = Color(0xFF8A8A93)
val StatusNew = Color(0xFF6C8CFF)

private val MarsColors = darkColorScheme(
    primary = MarsOrange,
    onPrimary = MarsWhite,
    secondary = MarsPeach,
    onSecondary = MarsGraphite,
    background = MarsGraphite,
    onBackground = MarsWhite,
    surface = MarsGraphiteElevated,
    onSurface = MarsWhite,
    surfaceVariant = MarsCardDark,
    onSurfaceVariant = MarsMuted,
    error = StatusNotDone,
    outline = Color(0xFF3A3A44)
)

private val MarsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        color = MarsWhite
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        color = MarsWhite
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        color = MarsWhite
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = MarsWhite
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = MarsWhite
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = MarsWhite
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = MarsMuted
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = MarsWhite
    )
)

@Composable
fun MarsTheme(content: @Composable () -> Unit) {
    // Всегда тёмная графитовая тема — фирменный стиль ежедневника
    @Suppress("UNUSED_VARIABLE")
    val ignored = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = MarsColors,
        typography = MarsTypography,
        content = content
    )
}
