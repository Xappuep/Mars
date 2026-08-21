package com.mars.planner.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

fun TaskStatus.color(): Color = when (this) {
    TaskStatus.DONE -> StatusDone
    TaskStatus.IN_PROGRESS -> StatusProgress
    TaskStatus.POSTPONED -> StatusPostponed
    TaskStatus.NOT_DONE -> StatusNotDone
    TaskStatus.CANCELLED -> StatusCancelled
    TaskStatus.NEW -> StatusNew
}

@Composable
fun MarsAvatar(
    mood: MarsMood,
    size: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(mood) {
        val base = mood.assetBase
        val candidates = listOf("$base.webp", "$base.png", "$base.jpg")
        candidates.firstNotNullOfOrNull { name ->
            try {
                context.assets.open("mars/$name").use { BitmapFactory.decodeStream(it) }
            } catch (_: IOException) {
                null
            }
        }
    }

    Box(
        modifier = modifier
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
                modifier = Modifier.matchParentSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🐱", fontSize = (size.value / 3).sp)
                Text(
                    text = mood.name.lowercase(),
                    color = MarsPeach,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun MarsReactionBanner(
    mood: MarsMood,
    message: String,
    modifier: Modifier = Modifier
) {
    if (message.isBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MarsCardDark)
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
    subtaskProgress: String? = null
) {
    val isDone = task.status == TaskStatus.DONE
    val titleColor = MarsWhite.copy(alpha = if (isDone) 0.72f else 1f)
    val metaColor = MarsMuted.copy(alpha = if (isDone) 0.75f else 1f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MarsCardDark,
        border = BorderStroke(1.dp, Color(0xFF30303A)),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDone) 0.78f else 1f)
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
                .clickable(onClick = onNewTask),
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
                    .clickable(onClick = onVoice),
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
            .clickable(enabled = enabled, onClick = onClick)
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
            .clickable(onClick = onClick)
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
            .clickable(onClick = onClick)
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = if (active) MarsWhite else MarsMuted, fontSize = 12.sp)
    }
}
