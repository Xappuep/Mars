package com.mars.planner.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mars.planner.R
import com.mars.planner.domain.model.AppTheme

/**
 * Режимы композиции мобильных сценовых тем (этап 9, часть 3 / corr1).
 * Полноэкранный Crop под всем UI заменён явным разделением зон.
 */
enum class ThemeSceneMode {
    /** «Сегодня»: верхняя художественная зона + рабочая подложка ниже. */
    Hero,
    /** «Задачи» / «Проекты»: компактная шапка, списки на однотонной поверхности. */
    CompactHeader,
    /** Календарь, формы, настройки, sync: плотная рабочая панель. */
    ContentSurface
}

data class ThemeSceneConfig(
    @DrawableRes val drawableRes: Int,
    val alignment: Alignment = Alignment.TopCenter,
    val contentScale: ContentScale = ContentScale.Crop
)

object ThemeSceneBackgrounds {

    fun config(theme: AppTheme): ThemeSceneConfig? = when (theme) {
        AppTheme.WHITE_STATION -> ThemeSceneConfig(
            drawableRes = R.drawable.theme_white_station_mobile
        )
        AppTheme.LIGHT_CONCRETE -> ThemeSceneConfig(
            drawableRes = R.drawable.theme_light_concrete_mobile
        )
        else -> null
    }

    fun hasScene(theme: AppTheme): Boolean = config(theme) != null

    fun hasEmbeddedMars(theme: AppTheme): Boolean = hasScene(theme)

    fun modeForScreen(theme: AppTheme, screen: ThemeSceneScreen): ThemeSceneMode? {
        if (!hasScene(theme)) return null
        return when (screen) {
            ThemeSceneScreen.Today -> ThemeSceneMode.Hero
            ThemeSceneScreen.Tasks, ThemeSceneScreen.Projects -> ThemeSceneMode.CompactHeader
            ThemeSceneScreen.Calendar,
            ThemeSceneScreen.Settings,
            ThemeSceneScreen.Sync,
            ThemeSceneScreen.TaskForm,
            ThemeSceneScreen.TaskDetail,
            ThemeSceneScreen.Other -> ThemeSceneMode.ContentSurface
        }
    }
}

enum class ThemeSceneScreen {
    Today,
    Tasks,
    Projects,
    Calendar,
    Settings,
    Sync,
    TaskForm,
    TaskDetail,
    Other
}

@Composable
fun ThemeSceneImage(
    config: ThemeSceneConfig,
    modifier: Modifier = Modifier,
    alignment: Alignment = config.alignment,
    contentScale: ContentScale = config.contentScale
) {
    Image(
        painter = painterResource(config.drawableRes),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment
    )
}

/**
 * Оболочка экрана для сценовых тем; для остальных тем вызывает [fallback].
 */
@Composable
fun ThemeSceneShell(
    screen: ThemeSceneScreen,
    heroOverlay: @Composable BoxScope.() -> Unit = {},
    fallback: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    when (ThemeSceneBackgrounds.modeForScreen(LocalMarsPalette.current.theme, screen)) {
        ThemeSceneMode.Hero -> ThemeHeroLayout(heroOverlay = heroOverlay, workContent = content)
        ThemeSceneMode.CompactHeader -> ThemeCompactHeaderLayout(content = content)
        ThemeSceneMode.ContentSurface -> ThemeContentSurfaceLayout(content = content)
        null -> fallback()
    }
}

/** Рабочая поверхность темы (не прозрачное «стекло» поверх фото). */
@Composable
fun themeWorkSurfaceColor(): Color {
    val palette = LocalMarsPalette.current
    val tokens = LocalAppTheme.current
    return tokens.panel.copy(alpha = 1f).let {
        if (palette.isLight) it else tokens.bg
    }
}

@Composable
fun ThemeHeroLayout(
    heroOverlay: @Composable BoxScope.() -> Unit,
    workContent: @Composable ColumnScope.() -> Unit
) {
    val config = ThemeSceneBackgrounds.config(LocalMarsPalette.current.theme) ?: return
    val surface = themeWorkSurfaceColor()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.42f).coerceIn(200.dp, 320.dp)
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
            ) {
                ThemeSceneImage(
                    config = config,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                    contentScale = ContentScale.Crop
                )
                // Переход к рабочей зоне без резкого края.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, surface.copy(alpha = 0.85f), surface)
                            )
                        )
                )
                heroOverlay()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(surface),
                content = workContent
            )
        }
    }
}

@Composable
fun ThemeCompactHeaderLayout(
    content: @Composable ColumnScope.() -> Unit
) {
    val config = ThemeSceneBackgrounds.config(LocalMarsPalette.current.theme) ?: run {
        Column(modifier = Modifier.fillMaxSize(), content = content)
        return
    }
    val surface = themeWorkSurfaceColor()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val headerHeight = (maxHeight * 0.20f).coerceIn(112.dp, 168.dp)
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
            ) {
                ThemeSceneImage(
                    config = config,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, surface)
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(surface),
                content = content
            )
        }
    }
}

/**
 * Плотная рабочая панель поверх спокойного фона темы.
 * Декор изображения — только узкая верхняя полоса (не сквозь цифры/поля).
 */
@Composable
fun ThemeContentSurfaceLayout(
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalMarsPalette.current
    val config = ThemeSceneBackgrounds.config(palette.theme)
    val surface = themeWorkSurfaceColor()
    val tokens = LocalAppTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        if (config != null) {
            ThemeSceneImage(
                config = config,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.TopCenter),
                alignment = Alignment.TopCenter,
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, tokens.bg.copy(alpha = 0.55f), tokens.bg)
                        )
                    )
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            color = surface,
            shape = RoundedCornerShape(tokens.radiusPanel),
            border = BorderStroke(
                1.dp,
                tokens.border.copy(alpha = 0.55f)
            ),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                content = content
            )
        }
    }
}

/** Плотная панель для календарной сетки внутри ContentSurface. */
@Composable
fun ThemeCalendarPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalAppTheme.current
    val panel = tokens.raised.copy(alpha = 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusPanel))
            .background(panel)
            .border(1.dp, tokens.border.copy(alpha = 0.65f), RoundedCornerShape(tokens.radiusPanel))
            .padding(12.dp),
        content = content
    )
}
