package com.mars.planner.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import com.mars.planner.R
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.ui.theme.AnimatedCounterText
import com.mars.planner.ui.theme.MarsAccentProgress
import com.mars.planner.ui.theme.MarsAccentProgressSoft
import com.mars.planner.ui.theme.MarsCardDark
import com.mars.planner.ui.theme.MarsGlass
import com.mars.planner.ui.theme.MarsGoldGlow
import com.mars.planner.ui.theme.MarsGraphite
import com.mars.planner.ui.theme.MarsMotion
import com.mars.planner.ui.theme.MarsMuted
import com.mars.planner.ui.theme.MarsOrange
import com.mars.planner.ui.theme.MarsOrangeSoft
import com.mars.planner.ui.theme.MarsOverdueGlow
import com.mars.planner.ui.theme.MarsOverdueSoft
import com.mars.planner.ui.theme.MarsPeach
import com.mars.planner.ui.theme.MarsWhite
import com.mars.planner.ui.theme.StatusCancelled
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusNew
import com.mars.planner.ui.theme.StatusNotDone
import com.mars.planner.ui.theme.StatusPostponed
import com.mars.planner.ui.theme.StatusProgress
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalReduceAnimations = compositionLocalOf { false }

fun TaskStatus.color(): Color = when (this) {
    TaskStatus.DONE -> StatusDone
    TaskStatus.IN_PROGRESS -> StatusProgress
    TaskStatus.POSTPONED -> StatusPostponed
    TaskStatus.NOT_DONE -> StatusNotDone
    TaskStatus.CANCELLED -> StatusCancelled
    TaskStatus.NEW -> StatusNew
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
    val reduce = LocalReduceAnimations.current
    val bitmap = remember(mood, size) {
        val targetPx = with(context.resources.displayMetrics) {
            (size.value * density).toInt().coerceAtLeast(64)
        }
        loadMarsBitmap(context, mood, targetPx)
    }

    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(28.dp))
                .background(MarsCardDark)
                .border(2.dp, MarsOrangeSoft, RoundedCornerShape(28.dp)),
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
        Box(modifier = modifier) { content() }
    } else {
        AnimatedContent(
            targetState = mood,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260)))
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "marsAvatar",
            modifier = modifier
        ) { _ ->
            content()
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
            val neutralHaloAlpha = 0.045f + (animatedAlpha.coerceIn(0.4f, 0.85f) - 0.4f) * 0.08f
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
fun MarsPresenceReaction(
    message: String,
    modifier: Modifier = Modifier
) {
    if (message.isBlank()) return
    val reduce = LocalReduceAnimations.current
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(message) {
        if (reduce) {
            appear.snapTo(1f)
        } else {
            appear.snapTo(0f)
            appear.animateTo(1f, tween(280))
        }
    }
    Text(
        text = message,
        color = MarsWhite.copy(alpha = 0.92f),
        fontSize = 13.sp,
        lineHeight = 17.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .graphicsLayer { alpha = appear.value }
            .clip(RoundedCornerShape(14.dp))
            .background(MarsCardDark.copy(alpha = 0.88f))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

@Composable
fun MarsMoodCard(
    mood: MarsMood,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MarsCardDark)
            .border(1.dp, Color(0xFF35353F), RoundedCornerShape(28.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarsAvatar(mood = mood, size = 128.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Настроение дня", color = MarsMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                mood.labelRu(),
                color = MarsWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 24.sp
            )
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (showMarsImage) {
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MarsCardDark)
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
            color = MarsMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = if (showMarsImage) TextAlign.Center else TextAlign.Start
        )
    }
}

@Composable
fun MarsReactionBanner(
    mood: MarsMood,
    message: String,
    modifier: Modifier = Modifier
) {
    if (message.isBlank()) return
    val reduce = LocalReduceAnimations.current
    val highlightTarget = when (mood) {
        MarsMood.DONE -> MarsPeach.copy(alpha = 0.22f)
        MarsMood.POSTPONED -> Color(0xFF3A3A28).copy(alpha = 0.65f)
        MarsMood.OVERDUE, MarsMood.STRICT -> Color(0xFF3A2A2A)
        MarsMood.WORKING -> Color(0xFF2A3340)
        else -> MarsCardDark
    }
    val bg by animateColorAsState(
        targetValue = highlightTarget,
        animationSpec = if (reduce) tween(0) else tween(320),
        label = "reactionBg"
    )
    val appear = remember { Animatable(if (reduce) 1f else 0.92f) }
    LaunchedEffect(mood, message) {
        if (reduce) {
            appear.snapTo(1f)
        } else {
            appear.snapTo(0.92f)
            appear.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
                alpha = if (reduce) 1f else appear.value.coerceIn(0.5f, 1f)
            }
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(
                1.dp,
                if (mood == MarsMood.DONE) MarsOrangeSoft else Color(0xFF30303A),
                RoundedCornerShape(24.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarsAvatar(mood = mood, size = 56.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = message, color = MarsWhite, fontSize = 14.sp)
    }
}

@Composable
fun SummaryChip(label: String, value: Int, accent: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MarsCardDark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedCounterText(value = value, color = accent, fontSize = 18.sp)
        Text(text = label, color = MarsMuted, fontSize = 11.sp)
    }
}

@Composable
fun StatusDot(status: TaskStatus, size: Dp = 10.dp) {
    val reduce = LocalReduceAnimations.current
    val color by animateColorAsState(
        targetValue = status.color(),
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
fun TaskCard(
    task: TaskItem,
    onClick: () -> Unit,
    isOverdue: Boolean = false,
    dueDateLabel: String? = null,
    @Suppress("UNUSED_PARAMETER") light: Boolean = false,
    subtaskProgress: String? = null,
    modifier: Modifier = Modifier
) {
    val isDone = task.status == TaskStatus.DONE
    val titleColor = MarsWhite.copy(alpha = if (isDone) 0.72f else 1f)
    val metaColor = MarsMuted.copy(alpha = if (isDone) 0.75f else 1f)
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
        isOverdue -> MarsOverdueGlow
        task.status == TaskStatus.IN_PROGRESS -> MarsAccentProgress
        isDone -> StatusDone
        else -> MarsOrange.copy(alpha = 0.55f)
    }
    val idleBorder = when {
        isOverdue -> MarsOverdueGlow.copy(alpha = 0.55f)
        task.status == TaskStatus.IN_PROGRESS -> MarsAccentProgress.copy(alpha = 0.45f)
        else -> Color(0xFF3A3A48)
    }
    val borderColor by animateColorAsState(
        targetValue = when {
            pressed && !reduce -> MarsOrange.copy(alpha = 0.55f)
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
    val glowAlpha = if (pressed && !reduce) 0.22f else if (isOverdue) 0.16f else 0.08f

    Box(
        modifier = modifier
            .fillMaxWidth()
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
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = cardShape,
            color = MarsGlass,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Transparent,
                                    statusAccent.copy(alpha = glowAlpha)
                                )
                            )
                        )
                )
                if (isOverdue) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(3.dp)
                            .height(48.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MarsOverdueGlow.copy(alpha = 0.85f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(task.status, 12.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (isOverdue) {
                            Text(
                                text = "Просрочено",
                                color = MarsOverdueGlow,
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
                                if (task.category.isNotBlank()) append(" · ${task.category}")
                                if (isOverdue && !dueDateLabel.isNullOrBlank()) {
                                    append(" · ")
                                    append(dueDateLabel)
                                }
                            },
                            color = metaColor,
                            fontSize = 12.sp
                        )
                        if (!subtaskProgress.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtaskProgress,
                                color = MarsPeach.copy(alpha = if (isDone) 0.8f else 1f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MarsGraphite.copy(alpha = 0.92f))
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
                            MarsOrange.copy(alpha = 1f),
                            Color(0xFFE85A00),
                            MarsOrange.copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(MarsGoldGlow.copy(alpha = 0.55f), MarsOrange.copy(alpha = 0.2f))
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .marsPressable(onClick = onNewTask),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "＋ Новая задача",
                color = MarsWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        if (onVoice != null) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MarsCardDark)
                    .border(1.dp, Color(0xFF30303A), CircleShape)
                    .marsPressable(onClick = onVoice),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Голосовой ввод",
                    tint = MarsOrange,
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) MarsOrange else MarsOrange.copy(alpha = 0.4f))
            .marsPressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = MarsWhite, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MarsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MarsOrange, RoundedCornerShape(20.dp))
            .marsPressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = MarsOrange, fontWeight = FontWeight.Medium)
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
            .border(1.5.dp, StatusNotDone, RoundedCornerShape(20.dp))
            .marsPressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = StatusNotDone, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FilterChipRow(
    selected: TaskStatus?,
    onSelect: (TaskStatus?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("Все", selected == null) { onSelect(null) }
        TaskStatus.entries.forEach { status ->
            FilterPill(status.labelRu, selected == status, status.color()) {
                onSelect(status)
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    active: Boolean,
    accent: Color = MarsOrange,
    onClick: () -> Unit
) {
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
                    Brush.horizontalGradient(listOf(MarsCardDark, MarsCardDark))
                }
            )
            .border(
                width = 1.dp,
                color = if (active) accent.copy(alpha = if (reduce) 0.7f else 0.85f) else Color(0xFF35353F),
                shape = RoundedCornerShape(16.dp)
            )
            .then(
                if (active && !reduce) {
                    Modifier.background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                            radius = 80f
                        )
                    )
                } else Modifier
            )
            .marsPressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = if (active) MarsWhite else MarsMuted, fontSize = 12.sp)
    }
}

@Composable
fun ProvideReduceAnimations(
    reduce: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalReduceAnimations provides reduce, content = content)
}

/** Не показывает ключ сопряжения в UI-сообщениях. */
fun redactSyncSecrets(message: String, syncKey: String): String {
    if (syncKey.isBlank()) return message
    return message.replace(syncKey, "••••", ignoreCase = false)
}
