package com.mars.planner.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.mars.planner.domain.logic.TaskRules
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.reminder.dueAtMillis
import com.mars.planner.reminder.dueAtToDateTime
import com.mars.planner.ui.components.MarsChoiceChip
import com.mars.planner.ui.components.MarsDangerOutlineButton
import com.mars.planner.ui.components.MarsPrimaryButton
import com.mars.planner.ui.components.MarsSecondaryButton
import com.mars.planner.ui.components.StatusDot
import com.mars.planner.ui.theme.LocalMarsPalette
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusOverdue
import com.mars.planner.ui.theme.ThemeSceneScreen
import com.mars.planner.ui.theme.ThemeSceneShell
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar
import java.util.UUID

/** Экран создания и правки задачи: заголовок, описание, проект, приоритет, срок, статус. */
@Composable
internal fun TaskEditScreen(vm: AppViewModel, nav: NavHostController, taskId: Long?) {
    val scope = rememberCoroutineScope()
    val projects by vm.activeProjects.collectAsState()
    val palette = LocalMarsPalette.current

    var loaded by remember { mutableStateOf(taskId == null) }
    var existing by remember { mutableStateOf<TaskItem?>(null) }
    var title by remember { mutableStateOf(PendingVoiceTitle.value.orEmpty()) }
    var description by remember { mutableStateOf("") }
    var projectUuid by remember { mutableStateOf<String?>(null) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    var status by remember { mutableStateOf(TaskStatus.OPEN) }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var dueMinutes by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { PendingVoiceTitle.value = null }

    LaunchedEffect(taskId) {
        if (taskId == null) return@LaunchedEffect
        val task = vm.getTask(taskId)
        if (task != null) {
            existing = task
            title = task.title
            description = task.description
            projectUuid = task.projectSyncUuid
            priority = task.priority
            status = task.status
            task.dueAtEpochMillis?.let { millis ->
                val (date, minutes) = dueAtToDateTime(millis)
                dueDate = date
                dueMinutes = if (hasExplicitTime(millis)) minutes else null
            }
        }
        loaded = true
    }

    if (!loaded) return

    val formBody: @Composable ColumnScope.() -> Unit = {
        ScreenTitleRow(if (taskId == null) "Новая задача" else "Правка задачи", nav)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Что нужно сделать") },
            colors = marsFieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Подробности (необязательно)") },
            colors = marsFieldColors,
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        ProjectPicker(
            projects = projects,
            selectedUuid = projectUuid,
            onSelect = { projectUuid = it }
        )

        Text("Приоритет", color = palette.textMuted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskPriority.entries.forEach { value ->
                MarsChoiceChip(
                    label = value.labelRu,
                    selected = priority == value,
                    onClick = { priority = value }
                )
            }
        }

        Text("Статус", color = palette.textMuted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskStatus.entries.forEach { value ->
                MarsChoiceChip(
                    label = value.labelRu,
                    selected = status == value,
                    onClick = { status = value }
                )
            }
        }

        DueDateEditor(
            date = dueDate,
            minutes = dueMinutes,
            onDateChange = { dueDate = it },
            onMinutesChange = { dueMinutes = it }
        )

        if (error != null) Text(error!!, color = StatusOverdue, fontSize = 13.sp)

        MarsPrimaryButton(
            text = "Сохранить",
            onClick = {
                if (title.isBlank()) {
                    error = "Нужен заголовок задачи"
                    return@MarsPrimaryButton
                }
                val dueAt = dueDate?.let { dueAtMillis(it, dueMinutes) }
                val base = existing
                val task = base?.copy(
                    title = title.trim(),
                    description = description.trim(),
                    projectSyncUuid = projectUuid,
                    priority = priority,
                    status = status,
                    dueAtEpochMillis = dueAt
                ) ?: TaskItem(
                    syncUuid = UUID.randomUUID().toString(),
                    title = title.trim(),
                    description = description.trim(),
                    projectSyncUuid = projectUuid,
                    priority = priority,
                    status = status,
                    dueAtEpochMillis = dueAt
                )
                scope.launch {
                    vm.saveTask(task)
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        MarsSecondaryButton(
            text = "Отмена",
            onClick = { nav.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    ThemeSceneShell(
        screen = ThemeSceneScreen.TaskForm,
        fallback = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = formBody
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = formBody
            )
        }
    )
}

/** Экран задачи: детали, быстрые действия по статусу и переносу срока. */
@Composable
internal fun TaskDetailScreen(vm: AppViewModel, nav: NavHostController, taskId: Long) {
    val palette = LocalMarsPalette.current
    val scope = rememberCoroutineScope()
    val task by vm.taskFlow(taskId).collectAsState(initial = null)
    var confirmDelete by remember { mutableStateOf(false) }
    var showPostpone by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    val current = task
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            ScreenTitleRow("Задача", nav)
            Text("Задача не найдена", color = palette.textMuted)
        }
        return
    }

    val overdue = TaskRules.isOverdue(current, today)

    val detailBody: @Composable ColumnScope.() -> Unit = {
        ScreenTitleRow("Задача", nav)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.card)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(current.status, isOverdue = overdue)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = current.title,
                    color = palette.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (current.status == TaskStatus.DONE) TextDecoration.LineThrough else null
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            DetailRow("Статус", current.status.labelRu, if (current.status == TaskStatus.DONE) StatusDone else palette.accent)
            DetailRow("Приоритет", current.priority.labelRu)
            DetailRow("Проект", vm.projectName(current.projectSyncUuid) ?: "Без проекта")
            DetailRow(
                label = "Срок",
                value = dueLabelOrNoDue(current.dueAtEpochMillis) + if (overdue) " · просрочено" else "",
                accent = if (overdue) StatusOverdue else null
            )
            DetailRow("Создана", timestampLabel(current.createdAt))
            DetailRow("Изменена", timestampLabel(current.updatedAt))
            if (current.isDemo) {
                DetailRow("Демо", "Не отправляется на ПК")
            }
            if (current.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Подробности", color = palette.textMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(current.description, color = palette.text, fontSize = 14.sp)
            }
        }

        if (current.status == TaskStatus.OPEN) {
            MarsPrimaryButton(
                text = "Выполнено",
                onClick = { scope.launch { vm.changeStatus(current.id, TaskStatus.DONE) } },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            MarsSecondaryButton(
                text = "Вернуть в работу",
                onClick = { scope.launch { vm.changeStatus(current.id, TaskStatus.OPEN) } },
                modifier = Modifier.fillMaxWidth()
            )
        }
        MarsSecondaryButton(
            text = "Перенести срок",
            onClick = { showPostpone = true },
            modifier = Modifier.fillMaxWidth()
        )
        MarsSecondaryButton(
            text = "Изменить",
            onClick = { nav.navigate(Routes.edit(current.id)) },
            modifier = Modifier.fillMaxWidth()
        )
        MarsDangerOutlineButton(
            text = "Удалить задачу",
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    ThemeSceneShell(
        screen = ThemeSceneScreen.TaskDetail,
        fallback = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = detailBody
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = detailBody
            )
        }
    )

    if (showPostpone) {
        PostponeDialog(
            onDismiss = { showPostpone = false },
            onPick = { newDue ->
                showPostpone = false
                scope.launch { vm.postpone(current.id, newDue) }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = palette.backgroundElevated,
            title = { Text("Удалить задачу?") },
            text = { Text("Задача исчезнет и на ПК после следующей отправки изменений.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        vm.deleteTask(current.id)
                        nav.popBackStack()
                    }
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, accent: androidx.compose.ui.graphics.Color? = null) {
    val palette = LocalMarsPalette.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = palette.textMuted, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Text(value, color = accent ?: palette.text, fontSize = 13.sp)
    }
}

/** Дата и время срока: системные диалоги Android, чтобы не изобретать свой календарь. */
@Composable
private fun DueDateEditor(
    date: LocalDate?,
    minutes: Int?,
    onDateChange: (LocalDate?) -> Unit,
    onMinutesChange: (Int?) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMarsPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Срок", color = palette.textMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (date == null) {
                "Без срока"
            } else {
                buildString {
                    append(date.format(ruDateFull))
                    append(" · ")
                    append(minutes?.let { timeLabel(it) } ?: "весь день")
                }
            },
            color = palette.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarsSecondaryButton(
                text = "Дата",
                onClick = {
                    val base = date ?: LocalDate.now()
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, day -> onDateChange(LocalDate.of(year, month + 1, day)) },
                        base.year,
                        base.monthValue - 1,
                        base.dayOfMonth
                    ).show()
                }
            )
            MarsSecondaryButton(
                text = "Время",
                onClick = {
                    val base = minutes ?: (9 * 60)
                    android.app.TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            if (date == null) onDateChange(LocalDate.now())
                            onMinutesChange(hour * 60 + minute)
                        },
                        base / 60,
                        base % 60,
                        true
                    ).show()
                }
            )
            MarsSecondaryButton(
                text = "Убрать срок",
                onClick = {
                    onDateChange(null)
                    onMinutesChange(null)
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarsChoiceChip(
                label = "Сегодня",
                selected = date == LocalDate.now(),
                onClick = { onDateChange(LocalDate.now()) }
            )
            MarsChoiceChip(
                label = "Завтра",
                selected = date == LocalDate.now().plusDays(1),
                onClick = { onDateChange(LocalDate.now().plusDays(1)) }
            )
            MarsChoiceChip(
                label = "Через неделю",
                selected = date == LocalDate.now().plusWeeks(1),
                onClick = { onDateChange(LocalDate.now().plusWeeks(1)) }
            )
        }
    }
}

@Composable
private fun PostponeDialog(onDismiss: () -> Unit, onPick: (Long?) -> Unit) {
    val palette = LocalMarsPalette.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.backgroundElevated,
        title = { Text("Перенести срок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarsSecondaryButton(
                    text = "На сегодня",
                    onClick = { onPick(dueAtMillis(LocalDate.now(), null)) },
                    modifier = Modifier.fillMaxWidth()
                )
                MarsSecondaryButton(
                    text = "На завтра",
                    onClick = { onPick(dueAtMillis(LocalDate.now().plusDays(1), null)) },
                    modifier = Modifier.fillMaxWidth()
                )
                MarsSecondaryButton(
                    text = "На следующую неделю",
                    onClick = { onPick(dueAtMillis(LocalDate.now().plusWeeks(1), null)) },
                    modifier = Modifier.fillMaxWidth()
                )
                MarsSecondaryButton(
                    text = "Выбрать дату…",
                    onClick = {
                        val now = Calendar.getInstance()
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                onPick(dueAtMillis(LocalDate.of(year, month + 1, day), null))
                            },
                            now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH),
                            now.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                MarsSecondaryButton(
                    text = "Убрать срок",
                    onClick = { onPick(null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

/** Пустой контейнер-заглушка на случай, если экран открыт без данных. */
@Composable
internal fun TaskScreenPlaceholder(message: String) {
    val palette = LocalMarsPalette.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = palette.textMuted)
    }
}
