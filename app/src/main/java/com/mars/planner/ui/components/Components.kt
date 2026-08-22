package com.mars.planner.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.planner.R
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.ui.theme.MarsCardDark
import com.mars.planner.ui.theme.MarsGraphite
import com.mars.planner.ui.theme.MarsMuted
import com.mars.planner.ui.theme.MarsOrange
import com.mars.planner.ui.theme.MarsOrangeSoft
import com.mars.planner.ui.theme.MarsPeach
import com.mars.planner.ui.theme.MarsWhite
import com.mars.planner.ui.theme.StatusCancelled
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusNew
import com.mars.planner.ui.theme.StatusNotDone
import com.mars.planner.ui.theme.StatusPostponed
import com.mars.planner.ui.theme.StatusProgress
import java.io.IOException

val LocalReduceAnimations = compositionLocalOf { false }

fun TaskStatus.color(): Color = when (this) {
    TaskStatus.DONE -> StatusDone
    TaskStatus.IN_PROGRESS -> StatusProgress
    TaskStatus.POSTPONED -> StatusPostponed
    TaskStatus.NOT_DONE -> StatusNotDone
    TaskStatus.CANCELLED -> StatusCancelled
    TaskStatus.NEW -> StatusNew
}

fun MarsMood.labelRu(): String = when (this) {
    MarsMood.DONE -> "Марс доволен"
    MarsMood.WORKING -> "Марс сосредоточен"
    MarsMood.POSTPONED -> "Марс озадачен"
    MarsMood.OVERDUE -> "Марс ждёт решения"
    MarsMood.STRICT -> "Марс настроен серьёзно"
    MarsMood.SUPPORTIVE -> "Марс поддерживает"
    MarsMood.DEFAULT -> "Марс спокоен"
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
        targetValue = if (!enabled || reduce) 1f else if (pressed) 0.97f else 1f,
        animationSpec = if (reduce) tween(0) else spring(stiffness = Spring.StiffnessMediumLow),
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
        val base = mood.assetBase
        // Реальные ассеты — PNG; webp/jpg остаются запасными вариантами.
        val candidates = listOf("$base.png", "$base.webp", "$base.jpg")
        val targetPx = with(context.resources.displayMetrics) {
            (size.value * density).toInt().coerceAtLeast(64)
        }
        candidates.firstNotNullOfOrNull { name ->
            try {
                // Сначала размеры, затем downsample — портреты ~2MB иначе легко съедают память.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open("mars/$name").use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                while (maxSide / sample > targetPx * 2) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                context.assets.open("mars/$name").use { BitmapFactory.decodeStream(it, null, opts) }
            } catch (_: IOException) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }

    // Портреты 1122×1402: лёгкий сдвиг вверх, чтобы уши/глаза/подбородок
    // оставались в квадратном кадре при ContentScale.Crop.
    val faceAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.14f)

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
                    contentScale = ContentScale.Crop,
                    alignment = faceAlignment,
                    modifier = Modifier.matchParentSize()
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MarsCardDark)
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarsAvatar(mood = mood, size = 96.dp, animateChange = false)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            color = MarsMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
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
        Text(text = value.toString(), color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, color = MarsMuted, fontSize = 11.sp)
    }
}

@Composable
fun StatusDot(status: TaskStatus, size: Dp = 10.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(status.color())
    )
}

@Composable
fun TaskCard(
    task: TaskItem,
    onClick: () -> Unit,
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
        targetValue = if (reduce) 1f else if (pressed) 0.98f else 1f,
        animationSpec = if (reduce) tween(0) else spring(stiffness = Spring.StiffnessMediumLow),
        label = "taskPress"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isDone) 0.78f else 1f,
        animationSpec = if (reduce) tween(0) else tween(220),
        label = "taskAlpha"
    )

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(22.dp),
        color = MarsCardDark,
        border = BorderStroke(1.dp, Color(0xFF30303A)),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(task.status, 12.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${task.status.labelRu} · ${task.priority.labelRu}" +
                        if (task.category.isNotBlank()) " · ${task.category}" else "",
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
                .background(MarsOrange)
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) accent.copy(alpha = 0.25f) else MarsCardDark)
            .border(1.dp, if (active) accent else Color.Transparent, RoundedCornerShape(16.dp))
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
