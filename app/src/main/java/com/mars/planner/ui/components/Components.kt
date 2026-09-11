package com.mars.planner.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.planner.R
import com.mars.planner.domain.logic.TaskStatusToggle
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.TaskFilter
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.ui.theme.AnimatedCounterText
import com.mars.planner.ui.theme.LocalEffectIntensity
import com.mars.planner.ui.theme.LocalMarsPalette
import com.mars.planner.ui.theme.MarsMotion
import com.mars.planner.ui.theme.MarsOutline
import com.mars.planner.ui.theme.StatusDanger
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusOpen
import com.mars.planner.ui.theme.StatusOverdue
import java.io.IOException

val LocalReduceAnimations = compositionLocalOf { false }

fun TaskStatus.color(): Color = when (this) {
    TaskStatus.OPEN -> StatusOpen
    TaskStatus.DONE -> StatusDone
}

/** Цвет метки задачи с учётом просрочки. */
fun statusColor(status: TaskStatus, isOverdue: Boolean): Color =
    if (isOverdue && status == TaskStatus.OPEN) StatusOverdue else status.color()

fun TaskFilter.color(): Color {
    // Цвет «Все» берётся из палитры в FilterBar через palette.text.
    return when (this) {
        TaskFilter.ALL -> Color.Unspecified
        TaskFilter.OPEN -> StatusOpen
        TaskFilter.DONE -> StatusDone
        TaskFilter.OVERDUE -> StatusOverdue
    }
}

fun MarsMood.previewLabelRu(): String = when (this) {
    MarsMood.DEFAULT -> "Спокоен"
    MarsMood.DONE -> "Доволен"
    MarsMood.WORKING -> "Сосредоточен"
    MarsMood.POSTPONED -> "Озадачен"
    MarsMood.OVERDUE -> "Просрочено"
    MarsMood.SUPPORTIVE -> "Поддерживает"
    MarsMood.STRICT -> "Строгий"
}

data class MarsPreviewOption(val mood: MarsMood, val label: String)

val marsPreviewOptions: List<MarsPreviewOption> = listOf(
    MarsPreviewOption(MarsMood.DEFAULT, "Спокоен"),
    MarsPreviewOption(MarsMood.DONE, "Доволен"),
    MarsPreviewOption(MarsMood.WORKING, "Сосредоточен"),
    MarsPreviewOption(MarsMood.POSTPONED, "Озадачен"),
    MarsPreviewOption(MarsMood.OVERDUE, "Просрочено"),
    MarsPreviewOption(MarsMood.SUPPORTIVE, "Поддерживает"),
    MarsPreviewOption(MarsMood.STRICT, "Строгий")
)

fun MarsMood.labelRu(): String = when (this) {
    MarsMood.DEFAULT -> "Марс спокоен"
    MarsMood.DONE -> "Марс доволен"
    MarsMood.WORKING -> "Марс сосредоточен"
    MarsMood.POSTPONED -> "Марс озадачен"
    MarsMood.OVERDUE -> "Марс ждёт решения"
    MarsMood.STRICT -> "Марс настроен серьёзно"
    MarsMood.SUPPORTIVE -> "Марс поддерживает"
}

private fun loadMarsBitmap(context: Context, mood: MarsMood, targetPx: Int): Bitmap? {
    val assetPath = "mars/${mood.assetBase}.webp"
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.assets, assetPath)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxSide = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                var sample = 1
                while (maxSide / sample > targetPx * 2) sample *= 2
                decoder.setTargetSize(
                    (info.size.width / sample).coerceAtLeast(1),
                    (info.size.height / sample).coerceAtLeast(1)
                )
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            while (maxSide / sample > targetPx * 2) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (_: IOException) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

@Composable
fun Modifier.marsPressable(
    enabled: Boolean = true,
    reduce: Boolean = LocalReduceAnimations.current,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!enabled || reduce) 1f else if (pressed) MarsMotion.PRESS_SCALE else 1f,
        animationSpec = if (reduce) tween(0) else tween(MarsMotion.PressDurationMs, easing = FastOutSlowInEasing),
        label = "marsPress"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
fun MarsAvatar(
    mood: MarsMood,
    size: Dp = 88.dp,
    modifier: Modifier = Modifier,
    animateChange: Boolean = true
) {
    val context = LocalContext.current
    val palette = LocalMarsPalette.current
    val reduce = LocalReduceAnimations.current

    @Composable
    fun AvatarFrame(activeMood: MarsMood) {
        val bitmap = remember(activeMood, size) {
            val targetPx = with(context.resources.displayMetrics) {
                (size.value * density).toInt().coerceAtLeast(64)
            }
            loadMarsBitmap(context, activeMood, targetPx)
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(28.dp))
                .background(palette.card)
                .border(2.dp, palette.accentSoft, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Марс",
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(4.dp)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.mars_placeholder),
                    contentDescription = "Марс (заглушка)",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(size * 0.12f)
                )
            }
        }
    }

    if (!animateChange || reduce) {
        Box(modifier = modifier) { AvatarFrame(mood) }
    } else {
        AnimatedContent(
            targetState = mood,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260)))
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "marsAvatar",
            modifier = modifier
        ) { activeMood ->
            AvatarFrame(activeMood)
        }
    }
}

@Composable
fun MarsBackgroundPresence(
    mood: MarsMood,
    presenceAlpha: Float,
    modifier: Modifier = Modifier,
    scrollOffsetPx: Float = 0f,
    interactionNudge: Int = 0
) {
    val reduce = LocalReduceAnimations.current
    if (!reduce) {
        AnimatedContent(
            targetState = mood,
            transitionSpec = {
                (fadeIn(tween(320)) + scaleIn(initialScale = 0.98f, animationSpec = tween(320)))
                    .togetherWith(fadeOut(tween(220)))
            },
            label = "marsPresenceMood",
            modifier = modifier
        ) { activeMood ->
            MarsBackgroundPresenceLayer(
                mood = activeMood,
                presenceAlpha = presenceAlpha,
                scrollOffsetPx = scrollOffsetPx,
                interactionNudge = interactionNudge,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        MarsBackgroundPresenceLayer(
            mood = mood,
            presenceAlpha = presenceAlpha,
            scrollOffsetPx = 0f,
            interactionNudge = 0,
            modifier = modifier
        )
    }
}

@Composable
private fun MarsBackgroundPresenceLayer(
    mood: MarsMood,
    presenceAlpha: Float,
    scrollOffsetPx: Float = 0f,
    interactionNudge: Int = 0,
    modifier: Modifier = Modifier
) {
    val reduce = LocalReduceAnimations.current
    if (reduce) {
        MarsBackgroundPresenceBody(
            mood = mood,
            presenceAlpha = presenceAlpha,
            scrollOffsetPx = 0f,
            breathScale = 1f,
            driftPx = 0f,
            extraAlpha = 0f,
            interactionNudge = 0,
            modifier = modifier
        )
    } else {
        MarsBackgroundPresenceAnimated(
            mood = mood,
            presenceAlpha = presenceAlpha,
            scrollOffsetPx = scrollOffsetPx,
            interactionNudge = interactionNudge,
            modifier = modifier
        )
    }
}

@Composable
private fun MarsBackgroundPresenceAnimated(
    mood: MarsMood,
    presenceAlpha: Float,
    scrollOffsetPx: Float,
    interactionNudge: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val infinite = rememberInfiniteTransition(label = "marsBreath")
    val breath by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val driftY by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(6200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val opacityPulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 0.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val driftPx = with(density) { driftY.dp.toPx() }
    MarsBackgroundPresenceBody(
        mood = mood,
        presenceAlpha = presenceAlpha,
        scrollOffsetPx = scrollOffsetPx,
        breathScale = breath,
        driftPx = driftPx,
        extraAlpha = opacityPulse,
        interactionNudge = interactionNudge,
        modifier = modifier
    )
}

@Composable
private fun MarsBackgroundPresenceBody(
    mood: MarsMood,
    presenceAlpha: Float,
    scrollOffsetPx: Float,
    breathScale: Float,
    driftPx: Float,
    extraAlpha: Float,
    interactionNudge: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduce = LocalReduceAnimations.current
    val effects = LocalEffectIntensity.current
    val density = LocalDensity.current
    val animatedAlpha by animateFloatAsState(
        targetValue = presenceAlpha.coerceIn(0f, 1f),
        animationSpec = if (reduce) tween(0) else tween(900),
        label = "marsPresenceAlpha"
    )

    val nudgeRot = remember { Animatable(0f) }
    val nudgeX = remember { Animatable(0f) }
    LaunchedEffect(interactionNudge) {
        if (interactionNudge > 0 && !reduce) {
            nudgeRot.snapTo(0f)
            nudgeX.snapTo(0f)
            nudgeRot.animateTo(-2.2f, tween(160))
            nudgeRot.animateTo(0f, tween(420))
            nudgeX.animateTo(-5f, tween(160))
            nudgeX.animateTo(0f, tween(420))
        }
    }

    val parallaxPx = with(density) {
        (scrollOffsetPx * 0.025f).coerceIn(-MarsMotion.MarsParallaxMaxDp, MarsMotion.MarsParallaxMaxDp).dp.toPx()
    }
    val layerAlpha = (animatedAlpha + extraAlpha).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier) {
        val targetPx = remember(maxHeight, density) {
            with(density) { (maxHeight * 0.55f).roundToPx().coerceAtLeast(320) }
        }
        val bitmap = remember(mood, targetPx) {
            loadMarsBitmap(context, mood, targetPx)
        }
        val aspect = remember(bitmap) {
            bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (1122f / 1402f)
        }
        val maxCatWidth = maxWidth * 0.44f
        val maxCatHeight = maxHeight * 0.88f
        var catHeight = maxCatHeight
        var catWidth = catHeight * aspect
        if (catWidth > maxCatWidth) {
            catWidth = maxCatWidth
            catHeight = catWidth / aspect
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp)
                .width(catWidth)
                .height(catHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Едва заметный нейтральный ореол — отделяет силуэт от фона, без цветного пятна.
            val neutralHaloAlpha = effects.scale(
                0.045f + (animatedAlpha.coerceIn(0.4f, 0.85f) - 0.4f) * 0.08f
            )
            if (neutralHaloAlpha > 0f) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = breathScale * 1.02f
                            scaleY = breathScale * 1.02f
                            translationY = driftPx - parallaxPx
                            translationX = nudgeX.value
                        }
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF4A4A54).copy(alpha = neutralHaloAlpha),
                                Color(0xFF32323A).copy(alpha = neutralHaloAlpha * 0.45f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.54f, size.height * 0.64f),
                            radius = size.minDimension * 0.42f
                        ),
                        radius = size.minDimension * 0.42f,
                        center = Offset(size.width * 0.54f, size.height * 0.64f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = breathScale
                        scaleY = breathScale
                        translationY = driftPx - parallaxPx
                        translationX = nudgeX.value
                        rotationZ = nudgeRot.value
                    }
                    .alpha(layerAlpha),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.mars_placeholder),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun MarsEmptyState(
    mood: MarsMood,
    message: String,
    modifier: Modifier = Modifier,
    showMarsImage: Boolean = true
) {
    val palette = LocalMarsPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (showMarsImage) {
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.card)
                        .padding(vertical = 28.dp, horizontal = 20.dp)
                } else {
                    Modifier.padding(vertical = 20.dp, horizontal = 4.dp)
                }
            ),
        horizontalAlignment = if (showMarsImage) Alignment.CenterHorizontally else Alignment.Start
    ) {
        if (showMarsImage) {
            MarsAvatar(mood = mood, size = 96.dp, animateChange = false)
            Spacer(modifier = Modifier.height(14.dp))
        }
        Text(
            text = message,
            color = palette.textMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = if (showMarsImage) TextAlign.Center else TextAlign.Start
        )
    }
}

@Composable
fun SummaryChip(label: String, value: Int, accent: Color) {
    val palette = LocalMarsPalette.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(palette.card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedCounterText(value = value, color = accent, fontSize = 18.sp)
        Text(text = label, color = palette.textMuted, fontSize = 11.sp)
    }
}

@Composable
fun StatusDot(status: TaskStatus, size: Dp = 10.dp, isOverdue: Boolean = false) {
    val reduce = LocalReduceAnimations.current
    val color by animateColorAsState(
        targetValue = statusColor(status, isOverdue),
        animationSpec = if (reduce) tween(0) else tween(220),
        label = "statusColor"
    )
    val scale = remember { Animatable(1f) }
    LaunchedEffect(status) {
        if (!reduce) {
            scale.snapTo(1f)
            scale.animateTo(1.18f, tween(100))
            scale.animateTo(1f, tween(130))
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun MarsProgressBar(
    percent: Int,
    modifier: Modifier = Modifier
) {
    val palette = LocalMarsPalette.current
    val reduce = LocalReduceAnimations.current
    val target = (percent.coerceIn(0, 100)) / 100f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduce) tween(0) else tween(420, easing = FastOutSlowInEasing),
        label = "projectProgress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(palette.text.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(palette.accent, palette.highlight)
                    )
                )
        )
    }
}

@Composable
fun TaskCard(
    task: TaskItem,
    onClick: () -> Unit,
    isOverdue: Boolean = false,
    dueDateLabel: String? = null,
    projectName: String? = null,
    modifier: Modifier = Modifier,
    /** Отдельный жест выполнения; null — декоративная точка без действия (Сегодня/Календарь). */
    onToggleDone: (() -> Unit)? = null,
    toggleEnabled: Boolean = true
) {
    val palette = LocalMarsPalette.current
    val effects = LocalEffectIntensity.current
    val isDone = task.status == TaskStatus.DONE
    val titleColor = palette.text.copy(alpha = if (isDone) 0.72f else 1f)
    val metaColor = palette.textMuted.copy(alpha = if (isDone) 0.75f else 1f)
    val reduce = LocalReduceAnimations.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (reduce) 1f else if (pressed) MarsMotion.PRESS_SCALE else 1f,
        animationSpec = if (reduce) tween(0) else tween(MarsMotion.PressDurationMs, easing = FastOutSlowInEasing),
        label = "taskPress"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isDone) 0.78f else 1f,
        animationSpec = if (reduce) tween(0) else tween(220),
        label = "taskAlpha"
    )
    val statusAccent = when {
        isOverdue && !isDone -> StatusOverdue
        isDone -> StatusDone
        else -> palette.accent.copy(alpha = 0.55f)
    }
    val idleBorder = when {
        isOverdue && !isDone -> StatusOverdue.copy(alpha = 0.55f)
        isDone -> StatusDone.copy(alpha = 0.28f)
        else -> MarsOutline
    }
    val borderColor by animateColorAsState(
        targetValue = when {
            pressed && !reduce -> palette.accent.copy(alpha = 0.55f)
            else -> idleBorder
        },
        animationSpec = if (reduce) tween(0) else tween(MarsMotion.PressDurationMs),
        label = "taskBorder"
    )
    val overdueReveal = remember { Animatable(if (reduce || !isOverdue) 1f else 0.65f) }
    LaunchedEffect(isOverdue, task.id) {
        if (!reduce && isOverdue) {
            overdueReveal.snapTo(0.65f)
            overdueReveal.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        } else {
            overdueReveal.snapTo(1f)
        }
    }
    val cardShape = RoundedCornerShape(22.dp)
    val glowAlpha = effects.scale(
        if (pressed && !reduce) 0.22f else if (isOverdue) 0.16f else 0.08f
    )
    val splitClicks = onToggleDone != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TaskCardTestTags.ROW)
            .semantics { contentDescription = "Строка задачи ${task.title}" }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha * overdueReveal.value
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .clip(cardShape)
                .background(Color.Black.copy(alpha = 0.28f))
        )
        // Важно: при splitClicks НЕ использовать Surface(onClick) —
        // кликабельный оверлоад вешает clickable на весь контейнер и
        // ломает независимые зоны круга и тела карточки (см. Material3 Surface).
        if (splitClicks) {
            Surface(
                shape = cardShape,
                color = palette.glass,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                TaskCardBody(
                    task = task,
                    isOverdue = isOverdue,
                    isDone = isDone,
                    titleColor = titleColor,
                    metaColor = metaColor,
                    statusAccent = statusAccent,
                    glowAlpha = glowAlpha,
                    dueDateLabel = dueDateLabel,
                    projectName = projectName,
                    effectsScale = { effects.scale(it) },
                    splitClicks = true,
                    onOpen = onClick,
                    onToggleDone = onToggleDone,
                    toggleEnabled = toggleEnabled,
                    openInteraction = interaction
                )
            }
        } else {
            Surface(
                onClick = onClick,
                interactionSource = interaction,
                shape = cardShape,
                color = palette.glass,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                TaskCardBody(
                    task = task,
                    isOverdue = isOverdue,
                    isDone = isDone,
                    titleColor = titleColor,
                    metaColor = metaColor,
                    statusAccent = statusAccent,
                    glowAlpha = glowAlpha,
                    dueDateLabel = dueDateLabel,
                    projectName = projectName,
                    effectsScale = { effects.scale(it) },
                    splitClicks = false,
                    onOpen = onClick,
                    onToggleDone = null,
                    toggleEnabled = true,
                    openInteraction = interaction
                )
            }
        }
    }
}

@Composable
private fun TaskCardBody(
    task: TaskItem,
    isOverdue: Boolean,
    isDone: Boolean,
    titleColor: Color,
    metaColor: Color,
    statusAccent: Color,
    glowAlpha: Float,
    dueDateLabel: String?,
    projectName: String?,
    effectsScale: (Float) -> Float,
    splitClicks: Boolean,
    onOpen: () -> Unit,
    onToggleDone: (() -> Unit)?,
    toggleEnabled: Boolean,
    openInteraction: MutableInteractionSource
) {
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = effectsScale(0.06f)),
                            Color.Transparent,
                            statusAccent.copy(alpha = glowAlpha)
                        )
                    )
                )
        )
        if (isOverdue && !isDone) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                StatusOverdue.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (splitClicks) 4.dp else 16.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (splitClicks && onToggleDone != null) {
                TaskDoneToggle(
                    status = task.status,
                    enabled = toggleEnabled,
                    onToggle = onToggleDone,
                    modifier = Modifier.testTag(TaskCardTestTags.DONE_TOGGLE)
                )
            } else {
                StatusDot(task.status, 12.dp, isOverdue = isOverdue && !isDone)
                Spacer(modifier = Modifier.width(12.dp))
            }
            // Вся оставшаяся площадь строки — единая зона открытия (не только буквы).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .testTag(TaskCardTestTags.OPEN_AREA)
                    .semantics { contentDescription = "Открыть задачу ${task.title}" }
                    .then(
                        if (splitClicks) {
                            Modifier.clickable(
                                interactionSource = openInteraction,
                                indication = null,
                                onClick = onOpen
                            )
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)) {
                    if (isOverdue && !isDone) {
                        Text(
                            text = "Просрочено",
                            color = StatusOverdue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = task.title,
                        color = titleColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(task.status.labelRu)
                            append(" · ")
                            append(task.priority.labelRu)
                            if (!projectName.isNullOrBlank()) append(" · $projectName")
                            if (!dueDateLabel.isNullOrBlank()) {
                                append(" · ")
                                append(dueDateLabel)
                            }
                        },
                        color = metaColor,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

object TaskCardTestTags {
    const val ROW = "task_card_row"
    const val OPEN_AREA = "task_card_open_area"
    const val DONE_TOGGLE = "task_card_done_toggle"
}

/** Круглый элемент выполнения (зона нажатия ≥ 44 dp). */
@Composable
fun TaskDoneToggle(
    status: TaskStatus,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalMarsPalette.current
    val isDone = status == TaskStatus.DONE
    val label = if (!enabled) {
        "Элемент временно недоступен"
    } else {
        TaskStatusToggle.accessibilityLabel(status)
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isDone) StatusDone else palette.accent.copy(alpha = 0.85f),
                    shape = CircleShape
                )
                .background(
                    if (isDone) StatusDone.copy(alpha = 0.92f) else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun NewTaskCtaBar(
    onNewTask: () -> Unit,
    modifier: Modifier = Modifier,
    onVoice: (() -> Unit)? = null
) {
    val palette = LocalMarsPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.accent,
                            palette.accentStrong,
                            palette.accent.copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(palette.highlight.copy(alpha = 0.55f), palette.accent.copy(alpha = 0.2f))
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .marsPressable(onClick = onNewTask),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "＋ Новая задача",
                color = palette.text,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        if (onVoice != null) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(palette.card)
                    .border(1.dp, MarsOutline, CircleShape)
                    .marsPressable(onClick = onVoice),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Голосовой ввод",
                    tint = palette.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MarsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalMarsPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) palette.accent else palette.accent.copy(alpha = 0.4f))
            .marsPressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = palette.text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MarsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalMarsPalette.current
    val tint = if (enabled) palette.accent else palette.accent.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, tint, RoundedCornerShape(20.dp))
            .marsPressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MarsDangerOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, StatusDanger, RoundedCornerShape(20.dp))
            .marsPressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = StatusDanger, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MarsChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalMarsPalette.current
    Text(
        text = label,
        color = if (selected) palette.text else palette.textMuted,
        fontSize = 13.sp,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.3f) else palette.card)
            .border(
                1.dp,
                if (selected) palette.accent.copy(alpha = 0.7f) else MarsOutline.copy(alpha = 0.7f),
                RoundedCornerShape(14.dp)
            )
            .marsPressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
fun FilterChipRow(
    selected: TaskFilter,
    onSelect: (TaskFilter) -> Unit
) {
    val palette = LocalMarsPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskFilter.entries.forEach { filter ->
            val accent = when (filter) {
                TaskFilter.ALL -> palette.text
                else -> filter.color()
            }
            FilterPill(filter.labelRu, selected == filter, accent) {
                onSelect(filter)
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val palette = LocalMarsPalette.current
    val effects = LocalEffectIntensity.current
    val reduce = LocalReduceAnimations.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) {
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.14f))
                    )
                } else {
                    Brush.horizontalGradient(listOf(palette.card, palette.card))
                }
            )
            .border(
                width = 1.dp,
                color = if (active) accent.copy(alpha = if (reduce) 0.7f else 0.85f) else MarsOutline,
                shape = RoundedCornerShape(16.dp)
            )
            .then(
                if (active && !reduce && effects.factor > 0f) {
                    Modifier.background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = effects.scale(0.12f)), Color.Transparent),
                            radius = 80f
                        )
                    )
                } else Modifier
            )
            .marsPressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = if (active) palette.text else palette.textMuted, fontSize = 12.sp)
    }
}

@Composable
fun ProvideReduceAnimations(
    reduce: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalReduceAnimations provides reduce, content = content)
}
