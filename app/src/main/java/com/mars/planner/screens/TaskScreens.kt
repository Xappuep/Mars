package com.mars.planner.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.mars.planner.domain.model.EnhancementIdea
import com.mars.planner.domain.model.EnhancementStatus
import com.mars.planner.domain.model.ReminderSnoozeMinutes
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.reminder.nextReminderMillis
import com.mars.planner.ui.components.MarsDangerOutlineButton
import com.mars.planner.ui.components.MarsPrimaryButton
import com.mars.planner.ui.components.MarsReactionBanner
import com.mars.planner.ui.components.MarsSecondaryButton
import com.mars.planner.ui.components.StatusDot
import com.mars.planner.ui.components.TaskCard
import com.mars.planner.ui.theme.MarsCardDark
import com.mars.planner.ui.theme.MarsMuted
import com.mars.planner.ui.theme.MarsOrange
import com.mars.planner.ui.theme.MarsPeach
import com.mars.planner.ui.theme.MarsWhite
import com.mars.planner.ui.theme.StatusNotDone
import com.mars.planner.voice.VoiceInputHelper
import com.mars.planner.voice.VoiceResult
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val editFieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MarsOrange,
    unfocusedBorderColor = MarsMuted.copy(alpha = 0.4f),
    focusedTextColor = MarsWhite,
    unfocusedTextColor = MarsWhite,
    cursorColor = MarsOrange,
    focusedLabelColor = MarsOrange,
    unfocusedLabelColor = MarsMuted
)

private val dateRuFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

@Composable
internal fun TaskEditScreen(vm: AppViewModel, nav: NavHostController, taskId: Long?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val isNew = taskId == null
    var title by remember { mutableStateOf(PendingVoiceTitle.value.orEmpty()) }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    var status by remember { mutableStateOf(TaskStatus.NEW) }
    var dueDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var dueTimeMinutes by remember { mutableStateOf<Int?>(null) }
    var reminderEnabled by remember { mutableStateOf(false) }
    var snoozeMinutes by remember { mutableIntStateOf(settings.defaultSnoozeMinutes.coerceIn(10, 60)) }
    var voiceMsg by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<TaskItem?>(null) }
    val voiceHelper = remember { VoiceInputHelper(context) }

    LaunchedEffect(taskId) {
        PendingVoiceTitle.value?.let { pending ->
            title = pending
            PendingVoiceTitle.value = null
        }
        if (taskId != null) {
            val task = vm.allTasks.value.find { it.id == taskId }
            if (task != null) {
                existing = task
                title = task.title
                description = task.description
                category = task.category
                priority = task.priority
                status = task.status
                dueDate = task.dueDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                dueTimeMinutes = task.dueTimeMinutes
                reminderEnabled = task.reminderAtEpochMillis != null
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            voiceMsg = "Нужно разрешение на микрофон. Уже введённый текст сохранён."
            return@rememberLauncherForActivityResult
        }
        voiceHelper.startListening { result ->
            when (result) {
                is VoiceResult.Success -> {
                    title = result.text
                    voiceMsg = "Распознано. Проверьте название и при необходимости исправьте."
                }
                is VoiceResult.Error -> voiceMsg = result.message
                VoiceResult.Unavailable -> voiceMsg = "Распознавание недоступно"
            }
        }
    }

    val availableStatuses = if (isNew) {
        listOf(TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.DONE)
    } else {
        TaskStatus.entries.toList()
    }

    fun openDatePicker() {
        val initial = dueDate ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, day -> dueDate = LocalDate.of(year, month + 1, day) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    fun openTimePicker() {
        val initial = dueTimeMinutes ?: (9 * 60)
        TimePickerDialog(
            context,
            { _, hour, minute -> dueTimeMinutes = hour * 60 + minute },
            initial / 60,
            initial % 60,
            true
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MarsWhite)
            }
            Text(
                if (isNew) "Новая задача" else "Редактирование",
                color = MarsWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Название *") },
            modifier = Modifier.fillMaxWidth(),
            colors = editFieldColors,
            trailingIcon = {
                IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Голосовой ввод", tint = MarsOrange)
                }
            }
        )
        if (voiceMsg != null) Text(voiceMsg!!, color = MarsPeach, fontSize = 12.sp)
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Описание") },
            modifier = Modifier.fillMaxWidth(),
            colors = editFieldColors,
            minLines = 3
        )
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Категория") },
            modifier = Modifier.fillMaxWidth(),
            colors = editFieldColors
        )
        Text("Приоритет", color = MarsMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskPriority.entries.forEach { p ->
                ChoiceChip(p.labelRu, priority == p) { priority = p }
            }
        }
        Text("Статус", color = MarsMuted)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableStatuses.forEach { s ->
                ChoiceChip(s.labelRu, status == s) { status = s }
            }
        }
        Text("Дата", color = MarsMuted)
        MarsSecondaryButton(
            text = dueDate?.format(dateRuFormat) ?: "Выбрать дату",
            onClick = { openDatePicker() },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Время", color = MarsMuted)
        MarsSecondaryButton(
            text = dueTimeMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "Выбрать время",
            onClick = { openTimePicker() },
            modifier = Modifier.fillMaxWidth()
        )
        if (dueTimeMinutes != null) {
            TextButton(onClick = { dueTimeMinutes = null }) {
                Text("Сбросить время", color = MarsMuted)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MarsCardDark)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Напомнить", color = MarsWhite, fontWeight = FontWeight.SemiBold)
                Text("Локальное уведомление в выбранное время", color = MarsMuted, fontSize = 12.sp)
            }
            Switch(
                checked = reminderEnabled,
                onCheckedChange = { enabled ->
                    reminderEnabled = enabled
                    if (enabled && dueTimeMinutes == null) {
                        dueTimeMinutes = 9 * 60
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MarsWhite,
                    checkedTrackColor = MarsOrange,
                    uncheckedThumbColor = MarsMuted,
                    uncheckedTrackColor = MarsCardDark
                )
            )
        }

        if (reminderEnabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, MarsOrange.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Время напоминания: " +
                        (dueTimeMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "не задано"),
                    color = MarsPeach,
                    fontWeight = FontWeight.Medium
                )
                Text("Повторное напоминание через", color = MarsMuted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderSnoozeMinutes.entries.forEach { option ->
                        ChoiceChip(option.labelRu, snoozeMinutes == option.minutes) {
                            snoozeMinutes = option.minutes
                        }
                    }
                }
            }
        }

        MarsPrimaryButton("Сохранить", onClick = {
            if (title.isBlank()) return@MarsPrimaryButton
            scope.launch {
                val dueDay = dueDate?.toEpochDay()
                val reminderAt = if (reminderEnabled && dueDay != null && dueTimeMinutes != null) {
                    nextReminderMillis(dueDay, dueTimeMinutes!!)
                } else null
                val base = existing
                val task = TaskItem(
                    id = base?.id ?: 0L,
                    title = title.trim(),
                    description = description.trim(),
                    dueDateEpochDay = dueDay,
                    dueTimeMinutes = dueTimeMinutes,
                    reminderAtEpochMillis = reminderAt,
                    priority = priority,
                    category = category.trim(),
                    status = status,
                    createdAt = base?.createdAt ?: System.currentTimeMillis(),
                    postponeCount = base?.postponeCount ?: 0,
                    postponeReason = base?.postponeReason,
                    parentTaskId = base?.parentTaskId,
                    nestingLevel = base?.nestingLevel ?: 0,
                    relatedToTaskId = base?.relatedToTaskId,
                    isDemo = base?.isDemo ?: false
                )
                val id = vm.saveTask(
                    task,
                    scheduleReminder = reminderAt != null,
                    context = context,
                    snoozeMinutes = snoozeMinutes
                )
                nav.popBackStack()
                nav.navigate(Routes.detail(id))
            }
        })
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) MarsWhite else MarsMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MarsOrange.copy(alpha = 0.3f) else MarsCardDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        fontSize = 13.sp
    )
}

@Composable
internal fun TaskDetailScreen(vm: AppViewModel, nav: NavHostController, taskId: Long) {
    val detailsFlow = remember(taskId) { vm.taskDetails(taskId) }
    val details by detailsFlow.collectAsState(initial = null)
    val reaction = vm.reaction
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmComplete by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showPostpone by remember { mutableStateOf(false) }
    var postponeDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var postponeReason by remember { mutableStateOf("") }
    var newSubTitle by remember { mutableStateOf("") }
    var showSubDialog by remember { mutableStateOf(false) }
    var showEnhDialog by remember { mutableStateOf(false) }
    var enhTitle by remember { mutableStateOf("") }
    var enhDesc by remember { mutableStateOf("") }
    var enhStatus by remember { mutableStateOf(EnhancementStatus.IDEA) }

    val task = details?.task
    if (task == null) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Загрузка…", color = MarsMuted)
        }
        return
    }

    val subtasks = details?.subtasks.orEmpty()
    val enhancements = details?.enhancements.orEmpty()
    val progressLabel = details?.subtaskProgressLabel().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MarsWhite)
            }
            Text(
                "Задача",
                color = MarsWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { nav.navigate(Routes.edit(task.id)) }) {
                Text("Изменить", color = MarsOrange)
            }
        }
        if (reaction != null) {
            MarsReactionBanner(reaction.mood, reaction.message)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusDot(task.status, 14.dp)
            Text(task.status.labelRu, color = MarsWhite, fontWeight = FontWeight.SemiBold)
        }
        Text(task.title, color = MarsWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (task.description.isNotBlank()) {
            Text(task.description, color = MarsMuted)
        }
        Text(
            "Приоритет: ${task.priority.labelRu}" +
                (if (task.category.isNotBlank()) " · ${task.category}" else "") +
                " · переносов: ${task.postponeCount}",
            color = MarsMuted,
            fontSize = 13.sp
        )

        Text("Сменить статус", color = MarsWhite, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                TaskStatus.NEW,
                TaskStatus.IN_PROGRESS,
                TaskStatus.DONE,
                TaskStatus.POSTPONED,
                TaskStatus.NOT_DONE,
                TaskStatus.CANCELLED
            ).forEach { st ->
                ChoiceChip(st.labelRu, task.status == st) {
                    if (st == TaskStatus.POSTPONED) {
                        showPostpone = true
                    } else {
                        scope.launch {
                            val (ok, msg) = vm.changeStatus(task.id, st, forceComplete = false)
                            if (!ok && msg != null) confirmComplete = msg
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MarsCardDark)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Подзадачи",
                    color = MarsWhite,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                MarsSecondaryButton("Добавить", onClick = { showSubDialog = true })
            }
            Text(progressLabel, color = MarsPeach, fontSize = 13.sp)
            Text(
                "Обязательные незавершённые подзадачи учитываются только в прогрессе этой задачи и не попадают в общий список.",
                color = MarsMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (subtasks.isEmpty()) {
                Text("Пока нет подзадач", color = MarsMuted, fontSize = 13.sp)
            } else {
                subtasks.forEach { sub ->
                    TaskCard(sub, onClick = { nav.navigate(Routes.detail(sub.id)) })
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MarsCardDark)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Дополнения и идеи",
                    color = MarsWhite,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                MarsSecondaryButton("Добавить", onClick = { showEnhDialog = true })
            }
            Text(
                "Не влияют на прогресс и завершение основной задачи.",
                color = MarsMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (enhancements.isEmpty()) {
                Text("Пока нет дополнений", color = MarsMuted, fontSize = 13.sp)
            } else {
                enhancements.forEach { idea ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MarsWhite.copy(alpha = 0.04f))
                            .padding(12.dp)
                    ) {
                        Text(idea.title, color = MarsWhite, fontWeight = FontWeight.SemiBold)
                        if (idea.description.isNotBlank()) {
                            Text(idea.description, color = MarsMuted, fontSize = 13.sp)
                        }
                        Text("Статус: ${idea.status.labelRu}", color = MarsPeach, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EnhancementStatus.entries.forEach { st ->
                                ChoiceChip(st.labelRu, idea.status == st) {
                                    scope.launch {
                                        if (st == EnhancementStatus.DEFERRED) {
                                            vm.deferEnhancement(idea.id, idea.deferredReason)
                                        } else {
                                            vm.saveEnhancement(idea.copy(status = st))
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val created = vm.convertEnhancement(idea.id)
                                if (created != null) nav.navigate(Routes.detail(created.id))
                            }
                        }) {
                            Text("Превратить в отдельную задачу", color = MarsOrange)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        MarsDangerOutlineButton(
            text = "Удалить задачу",
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (confirmComplete != null) {
        AlertDialog(
            onDismissRequest = { confirmComplete = null },
            title = { Text("Подтверждение") },
            text = { Text(confirmComplete!!) },
            confirmButton = {
                TextButton(onClick = {
                    confirmComplete = null
                    scope.launch { vm.changeStatus(task.id, TaskStatus.DONE, forceComplete = true) }
                }) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmComplete = null }) { Text("Отмена") }
            }
        )
    }

    if (confirmDelete) {
        val childWarning = buildString {
            append("Задача «${task.title}» будет удалена.")
            if (subtasks.isNotEmpty()) {
                append("\n\nВместе с ней будут удалены все её подзадачи (${subtasks.size}).")
            }
            if (enhancements.isNotEmpty()) {
                append("\nТакже будут удалены связанные дополнения и идеи (${enhancements.size}).")
            }
            if (subtasks.isNotEmpty() || enhancements.isNotEmpty()) {
                append("\n\nЭто отдельное явное подтверждение удаления связанных элементов.")
            } else {
                append("\n\nСвязанных подзадач и дополнений нет.")
            }
        }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить задачу?") },
            text = { Text(childWarning) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        if (subtasks.isNotEmpty() || enhancements.isNotEmpty()) {
                            vm.deleteTaskCascade(task.id, context)
                        } else {
                            vm.deleteTask(task.id, context)
                        }
                        nav.popBackStack()
                    }
                }) { Text("Удалить", color = StatusNotDone) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }

    if (showSubDialog) {
        AlertDialog(
            onDismissRequest = { showSubDialog = false },
            title = { Text("Новая подзадача") },
            text = {
                Column {
                    Text(
                        "Обязательная часть основной задачи. Учитывается в прогрессе.",
                        color = MarsMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newSubTitle,
                        onValueChange = { newSubTitle = it },
                        label = { Text("Название") },
                        colors = editFieldColors
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = newSubTitle.trim()
                    if (t.isBlank()) return@TextButton
                    showSubDialog = false
                    scope.launch {
                        vm.addSubtask(task.id, t)
                        newSubTitle = ""
                    }
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showSubDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showEnhDialog) {
        AlertDialog(
            onDismissRequest = { showEnhDialog = false },
            title = { Text("Новое дополнение") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Необязательное улучшение. Не блокирует завершение основной задачи.",
                        color = MarsMuted,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        enhTitle,
                        { enhTitle = it },
                        label = { Text("Название") },
                        colors = editFieldColors
                    )
                    OutlinedTextField(
                        enhDesc,
                        { enhDesc = it },
                        label = { Text("Краткое описание") },
                        colors = editFieldColors
                    )
                    Text("Статус", color = MarsMuted, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EnhancementStatus.entries.forEach { st ->
                            ChoiceChip(st.labelRu, enhStatus == st) { enhStatus = st }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (enhTitle.isBlank()) return@TextButton
                    showEnhDialog = false
                    scope.launch {
                        vm.saveEnhancement(
                            EnhancementIdea(
                                sourceTaskId = task.id,
                                title = enhTitle.trim(),
                                description = enhDesc.trim(),
                                status = enhStatus
                            )
                        )
                        if (enhStatus == EnhancementStatus.DEFERRED) {
                            // мягкая реакция Марса при откладывании идеи
                        }
                        enhTitle = ""
                        enhDesc = ""
                        enhStatus = EnhancementStatus.IDEA
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showEnhDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showPostpone) {
        AlertDialog(
            onDismissRequest = { showPostpone = false },
            title = { Text("Перенос задачи") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarsSecondaryButton(
                        text = postponeDate.format(dateRuFormat),
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> postponeDate = LocalDate.of(y, m + 1, d) },
                                postponeDate.year,
                                postponeDate.monthValue - 1,
                                postponeDate.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        postponeReason,
                        { postponeReason = it },
                        label = { Text("Причина переноса") },
                        colors = editFieldColors
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPostpone = false
                    scope.launch {
                        vm.postpone(
                            task.id,
                            postponeDate.toEpochDay(),
                            task.dueTimeMinutes,
                            postponeReason.ifBlank { null }
                        )
                    }
                }) { Text("Перенести") }
            },
            dismissButton = {
                TextButton(onClick = { showPostpone = false }) { Text("Отмена") }
            }
        )
    }
}
