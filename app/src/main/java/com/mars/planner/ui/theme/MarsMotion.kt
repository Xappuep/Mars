package com.mars.planner.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.planner.domain.model.DaySummary
import com.mars.planner.ui.components.LocalReduceAnimations

object MarsMotion {
    const val PRESS_SCALE = 0.985f
    const val PressDurationMs = 130
    const val ListStaggerMs = 28
    const val NavTransitionMs = 280
    const val MarsParallaxMaxDp = 10f
}

@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f ||
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.TRANSITION_ANIMATION_SCALE,
                    1f
                ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
fun MarsAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduce = LocalReduceAnimations.current
    val palette = LocalMarsPalette.current
    val effects = LocalEffectIntensity.current
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        palette.backgroundDeep,
                        palette.backgroundMid,
                        palette.background,
                        palette.backgroundElevated.copy(alpha = 0.6f)
                    )
                )
            )
            // Лёгкое осветление зоны контента (слева), без цветного пятна у Марса.
            val contentLift = effects.scale(if (!reduce) 0.04f else 0.025f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A2A32).copy(alpha = contentLift),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.22f, size.height * 0.18f),
                    radius = size.minDimension * 0.32f
                ),
                radius = size.minDimension * 0.32f,
                center = Offset(size.width * 0.22f, size.height * 0.18f)
            )
        }
        if (!reduce && effects.factor > 0f) {
            val infinite = rememberInfiniteTransition(label = "ambient")
            val drift by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(18_000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ambientDrift"
            )
            Canvas(Modifier.fillMaxSize()) {
                val gridStep = 48.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.018f + drift * 0.006f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += gridStep
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.014f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += gridStep
                }
                val dotStep = gridStep * 2
                var dx = dotStep / 2
                while (dx < size.width) {
                    var dy = dotStep / 2
                    while (dy < size.height) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.03f),
                            radius = 1.2f,
                            center = Offset(dx, dy)
                        )
                        dy += dotStep
                    }
                    dx += dotStep
                }
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val gridStep = 56.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.012f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += gridStep
                }
            }
        }
        content()
    }
}

@Composable
fun TodayStatsPanel(
    summary: DaySummary,
    modifier: Modifier = Modifier
) {
    val palette = LocalMarsPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.glass)
            .border(1.dp, MarsOutline.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TodayStatCell("Всего", summary.total, palette.text)
        TodayStatCell("Готово", summary.done, StatusDone)
        TodayStatCell("Открыто", summary.open, StatusOpen)
        TodayStatCell("Просрочено", summary.overdue, StatusOverdue)
    }
}

@Composable
private fun TodayStatCell(label: String, value: Int, accent: Color) {
    val palette = LocalMarsPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (palette.isLight) palette.card.copy(alpha = 0.92f)
                else Color(0xFF181820).copy(alpha = 0.55f)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedCounterText(value = value, color = accent, fontSize = 17.sp)
            Text(text = label, color = palette.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AnimatedCounterText(
    value: Int,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier
) {
    val reduce = LocalReduceAnimations.current
    if (reduce) {
        Text(
            text = value.toString(),
            color = color,
            fontWeight = fontWeight,
            fontSize = fontSize,
            modifier = modifier
        )
    } else {
        val animated by animateIntAsState(
            targetValue = value,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "counter"
        )
        Text(
            text = animated.toString(),
            color = color,
            fontWeight = fontWeight,
            fontSize = fontSize,
            modifier = modifier
        )
    }
}

@Composable
fun MarsBottomNavigationBar(
    route: String,
    items: List<Triple<String, String, ImageVector>>,
    onNavigate: (String) -> Unit
) {
    val reduce = LocalReduceAnimations.current
    val palette = LocalMarsPalette.current
    val selectedIndex = items.indexOfFirst { it.first == route }.coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (reduce) tween(0) else tween(MarsMotion.NavTransitionMs, easing = FastOutSlowInEasing),
        label = "navIndicator"
    )

    Box {
        NavigationBar(
            containerColor = palette.card.copy(alpha = 0.94f),
            modifier = Modifier.border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(MarsOutline.copy(alpha = 0.5f), Color.Transparent)
                ),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
            )
        ) {
            items.forEach { (r, label, icon) ->
                NavigationBarItem(
                    selected = route == r,
                    onClick = { onNavigate(r) },
                    icon = { androidx.compose.material3.Icon(icon, contentDescription = label) },
                    label = { Text(label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.accent,
                        selectedTextColor = palette.accent,
                        indicatorColor = palette.accent.copy(alpha = 0.14f),
                        unselectedIconColor = palette.textMuted,
                        unselectedTextColor = palette.textMuted
                    )
                )
            }
        }
        if (!reduce) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
            ) {
                val tabWidth = maxWidth / items.size
                val lineWidth = tabWidth * 0.46f
                val x = tabWidth * animatedIndex + (tabWidth - lineWidth) / 2f
                Box(
                    modifier = Modifier
                        .offset(x = x)
                        .width(lineWidth)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    palette.accent.copy(alpha = 0.35f),
                                    palette.highlight.copy(alpha = 0.95f),
                                    palette.accent.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
            ) {
                val tabWidth = maxWidth / items.size
                val lineWidth = tabWidth * 0.4f
                val x = tabWidth * selectedIndex + (tabWidth - lineWidth) / 2f
                Box(
                    modifier = Modifier
                        .offset(x = x)
                        .width(lineWidth)
                        .height(2.dp)
                        .background(palette.accent.copy(alpha = 0.75f))
                )
            }
        }
    }
}
