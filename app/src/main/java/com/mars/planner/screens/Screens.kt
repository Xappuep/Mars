package com.mars.planner.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.mars.planner.domain.model.EnhancementIdea
import com.mars.planner.domain.model.EnhancementStatus
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.MotivatorMode
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.export.BackupCodec
import com.mars.planner.motivator.MarsMotivator
import com.mars.planner.reminder.nextReminderMillis
import com.mars.planner.sync.SyncResult
import com.mars.planner.ui.components.MarsEmptyState
import com.mars.planner.ui.components.MarsPrimaryButton
import com.mars.planner.ui.components.MarsReactionBanner
import com.mars.planner.ui.components.MarsSecondaryButton
import com.mars.planner.ui.components.NewTaskCtaBar
import com.mars.planner.ui.components.StatusDot
import com.mars.planner.ui.components.TaskCard
import com.mars.planner.ui.components.redactSyncSecrets
import com.mars.planner.ui.theme.MarsCardDark
import com.mars.planner.ui.theme.MarsMuted
import com.mars.planner.ui.theme.MarsOrange
import com.mars.planner.ui.theme.MarsPeach
import com.mars.planner.ui.theme.MarsWhite
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusNotDone
import com.mars.planner.voice.VoiceInputHelper
import com.mars.planner.voice.VoiceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MarsOrange,
    unfocusedBorderColor = MarsMuted.copy(alpha = 0.4f),
    focusedTextColor = MarsWhite,
    unfocusedTextColor = MarsWhite,
    cursorColor = MarsOrange,
    focusedLabelColor = MarsOrange,
    unfocusedLabelColor = MarsMuted
)

@Composable
internal fun TasksScreen(vm: AppViewModel, nav: NavHostController) {
    val tasks by vm.allRootTasks.collectAsState()
    val allTasks by vm.allTasks.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = tasks.filter {
        query.isBlank() ||
            it.title.contains(query, true) ||
            it.description.contains(query, true) ||
            it.category.contains(query, true)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
    ) {
        Text("Задачи", color = MarsWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск") },
            colors = fieldColors,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        MarsSecondaryButton("Идеи и улучшения", onClick = { nav.navigate(Routes.Ideas) })
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        MarsEmptyState(
                            mood = MarsMood.DEFAULT,
                            message = if (query.isBlank()) "Пока нет задач" else "Ничего не найдено"
                        )
                    }
                }
                items(filtered, key = { it.id }) { task ->
                    val subs = allTasks.filter { it.parentTaskId == task.id }
                    val progress = if (subs.isEmpty()) {
                        null
                    } else {
                        val done = subs.count { it.status == TaskStatus.DONE }
                        "Подзадачи: $done из ${subs.size} выполнено"
                    }
                    TaskCard(
                        task,
                        onClick = { nav.navigate(Routes.detail(task.id)) },
                        subtaskProgress = progress,
                        modifier = Modifier
                    )
                }
            }
            NewTaskCtaBar(
                onNewTask = { nav.navigate(Routes.edit()) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
internal fun CalendarScreen(vm: AppViewModel, nav: NavHostController) {
    val all by vm.allRootTasks.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    val counts = remember(all, month) {
        all.filter { it.dueDateEpochDay != null }
            .groupBy { it.dueDateEpochDay!! }
            .mapValues { it.value.size }
    }
    val dayTasks = all.filter { it.dueDateEpochDay == selectedDay.toEpochDay() }
    val firstDow = month.atDay(1).dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))),
                color = MarsWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("<", color = MarsOrange) }
            TextButton(onClick = {
                month = YearMonth.now()
                selectedDay = LocalDate.now()
            }) { Text("Сегодня", color = MarsOrange) }
            TextButton(onClick = { month = month.plusMonths(1) }) { Text(">", color = MarsOrange) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                Text(it, color = MarsMuted, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            }
        }
        val cells = buildList {
            repeat((firstDow + 6) % 7) { add(null) }
            for (d in 1..daysInMonth) add(month.atDay(d))
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    day == null -> Color.Transparent
                                    day == selectedDay -> MarsOrange.copy(alpha = 0.25f)
                                    day == LocalDate.now() -> MarsCardDark
                                    else -> Color.Transparent
                                }
                            )
                            .clickable(enabled = day != null) { selectedDay = day!! },
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.dayOfMonth.toString(), color = MarsWhite, fontSize = 13.sp)
                                val c = counts[day.toEpochDay()] ?: 0
                                if (c > 0) {
                                    Text(c.toString(), color = MarsOrange, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Задачи дня", color = MarsWhite, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (dayTasks.isEmpty()) {
            MarsEmptyState(
                mood = MarsMood.DEFAULT,
                message = "В этот день задач нет"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayTasks, key = { it.id }) { task ->
                    TaskCard(task, onClick = { nav.navigate(Routes.detail(task.id)) })
                }
            }
        }
    }
}

@Composable
internal fun StatsScreen(vm: AppViewModel) {
    val all by vm.allRootTasks.collectAsState()
    val stats = remember(all) { vm.stats() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Статистика", color = MarsWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        StatCard("Выполнено за неделю", stats.completedWeek.toString())
        StatCard("Выполнено за месяц", stats.completedMonth.toString())
        StatCard("Переносов", stats.postponeCount.toString())
        StatCard("Просрочено сейчас", stats.overdueCount.toString(), StatusNotDone)
        StatCard("Процент выполнения (месяц)", "${stats.completionPercent}%", StatusDone)
        StatCard("Серия продуктивных дней", stats.productiveStreak.toString(), MarsOrange)
        Text(
            "День продуктивный, если выполнена хотя бы одна запланированная задача.",
            color = MarsMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, accent: Color = MarsPeach) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MarsCardDark)
            .padding(18.dp)
    ) {
        Text(label, color = MarsMuted, fontSize = 13.sp)
        Text(value, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var importCount by remember { mutableStateOf(0) }
    var confirmClearDemo by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            }
            if (json.isBlank()) {
                message = "Файл пуст"
                return@launch
            }
            importCount = BackupCodec.parseTaskCount(json)
            pendingImportJson = json
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки", color = MarsWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = settings.userName,
            onValueChange = { v -> scope.launch { vm.updateSettings { it.copy(userName = v) } } },
            label = { Text("Как к вам обращаться") },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors
        )
        Text("Мотиватор Марса", color = MarsWhite, fontWeight = FontWeight.SemiBold)
        MotivatorMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (settings.motivatorMode == mode) MarsOrange.copy(0.2f) else MarsCardDark)
                    .clickable { scope.launch { vm.updateSettings { it.copy(motivatorMode = mode) } } }
                    .padding(14.dp)
            ) {
                Text(mode.labelRu, color = MarsWhite)
            }
        }
        MarsSecondaryButton("Синхронизация с ПК", onClick = { nav.navigate(Routes.Sync) }, modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MarsCardDark)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Уменьшить анимации", color = MarsWhite, fontWeight = FontWeight.Medium)
                Text("Мгновенная смена состояний без декоративного движения", color = MarsMuted, fontSize = 12.sp)
            }
            Switch(
                checked = settings.reduceAnimations,
                onCheckedChange = { v ->
                    scope.launch { vm.updateSettings { it.copy(reduceAnimations = v) } }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MarsWhite,
                    checkedTrackColor = MarsOrange,
                    uncheckedThumbColor = MarsMuted,
                    uncheckedTrackColor = MarsCardDark
                )
            )
        }
        MarsPrimaryButton("Экспорт JSON", onClick = {
            scope.launch {
                val json = vm.exportJson()
                val file = File(context.getExternalFilesDir(null), "mars_backup_${System.currentTimeMillis()}.json")
                withContext(Dispatchers.IO) { file.writeText(json) }
                message = "JSON сохранён: ${file.absolutePath}"
            }
        })
        MarsSecondaryButton("Экспорт CSV", onClick = {
            scope.launch {
                val csv = vm.exportCsv()
                val file = File(context.getExternalFilesDir(null), "mars_tasks_${System.currentTimeMillis()}.csv")
                withContext(Dispatchers.IO) { file.writeText(csv) }
                message = "CSV сохранён: ${file.absolutePath}"
            }
        })
        MarsSecondaryButton("Импорт JSON", onClick = {
            importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        })
        MarsSecondaryButton("Загрузить демо-данные", onClick = {
            scope.launch {
                vm.loadDemo()
                message = "Демо-задачи добавлены"
            }
        })
        MarsSecondaryButton("Очистить демо-данные", onClick = { confirmClearDemo = true })
        Text(
            "Уведомления: если разрешение не выдано, включите его в настройках Android → Приложения → Ежедневник Марса → Уведомления.",
            color = MarsMuted,
            fontSize = 12.sp
        )
        if (message != null) Text(message!!, color = MarsPeach, fontSize = 13.sp)
    }

    if (pendingImportJson != null && !confirmReplace) {
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text("Импорт: $importCount задач") },
            text = { Text("Объединить с текущими данными или заменить? Замена потребует подтверждения и создаст локальную резервную копию.") },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingImportJson!!
                    pendingImportJson = null
                    scope.launch {
                        vm.importJson(json, replace = false)
                        message = "Данные объединены"
                    }
                }) { Text("Объединить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmReplace = true
                }) { Text("Заменить…") }
            }
        )
    }
    if (confirmReplace && pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Заменить все локальные данные?") },
            text = { Text("Перед заменой будет создана локальная резервная копия. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingImportJson!!
                    confirmReplace = false
                    pendingImportJson = null
                    scope.launch {
                        val backup = vm.exportJson()
                        val file = File(context.filesDir, "pre_replace_backup_${System.currentTimeMillis()}.json")
                        withContext(Dispatchers.IO) { file.writeText(backup) }
                        vm.importJson(json, replace = true)
                        message = "Данные заменены. Резервная копия: ${file.name}"
                    }
                }) { Text("Заменить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("Отмена") }
            }
        )
    }
    if (confirmClearDemo) {
        AlertDialog(
            onDismissRequest = { confirmClearDemo = false },
            title = { Text("Очистить демо?") },
            text = { Text("Будут удалены только задачи, помеченные как демо.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearDemo = false
                    scope.launch {
                        vm.clearDemo()
                        message = "Демо удалено"
                    }
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDemo = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
internal fun SyncScreen(vm: AppViewModel, nav: NavHostController) {
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var host by remember(settings.syncHost) { mutableStateOf(settings.syncHost) }
    var port by remember(settings.syncPort) { mutableStateOf(settings.syncPort.toString()) }
    var key by remember(settings.syncKey) { mutableStateOf(settings.syncKey) }
    var status by remember { mutableStateOf("Укажите IP компьютера в одной Wi‑Fi-сети") }
    var conflictChoice by remember { mutableStateOf(false) }
    var downloadedJson by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MarsWhite)
            }
            Text("Синхронизация с ПК", color = MarsWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Локальная сеть только. Без облака. Сначала запустите desktop-sync-server на компьютере.",
            color = MarsMuted,
            fontSize = 13.sp
        )
        OutlinedTextField(host, { host = it }, label = { Text("IP компьютера") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        OutlinedTextField(port, { port = it.filter { ch -> ch.isDigit() } }, label = { Text("Порт") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        var keyVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Ключ сопряжения") },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            singleLine = true,
            visualTransformation = if (keyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Скрыть ключ" else "Показать ключ",
                        tint = MarsMuted
                    )
                }
            }
        )
        MarsPrimaryButton("Сохранить настройки", onClick = {
            scope.launch {
                vm.updateSettings {
                    it.copy(
                        syncHost = host.trim(),
                        syncPort = port.toIntOrNull() ?: 8765,
                        syncKey = key.trim()
                    )
                }
                status = "Настройки сохранены"
            }
        })
        MarsSecondaryButton("Проверить подключение", onClick = {
            scope.launch {
                vm.updateSettings {
                    it.copy(syncHost = host.trim(), syncPort = port.toIntOrNull() ?: 8765, syncKey = key.trim())
                }
                val info = withContext(Dispatchers.IO) { vm.syncCheck() }
                val raw = if (info.ok) {
                    val last = info.lastBackupAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                    } ?: "нет"
                    "${info.message}. Последняя копия на ПК: $last"
                } else info.message
                status = redactSyncSecrets(raw, key.trim())
            }
        })
        val lastSync = if (settings.lastSyncAt > 0) {
            Instant.ofEpochMilli(settings.lastSyncAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        } else "ещё не было"
        Text("Последняя синхронизация с телефона: $lastSync", color = MarsMuted, fontSize = 12.sp)
        MarsPrimaryButton("Отправить копию на ПК", onClick = {
            scope.launch {
                val result = withContext(Dispatchers.IO) { vm.syncUpload() }
                val raw = when (result) {
                    is SyncResult.Success -> result.message
                    is SyncResult.Error -> result.message
                    is SyncResult.Conflict -> "Конфликт данных"
                }
                status = redactSyncSecrets(raw, key.trim())
            }
        })
        MarsSecondaryButton("Восстановить с ПК", onClick = {
            scope.launch {
                val s = settings.copy(syncHost = host.trim(), syncPort = port.toIntOrNull() ?: 8765, syncKey = key.trim())
                vm.updateSettings { s }
                val (result, json) = withContext(Dispatchers.IO) {
                    vm.let { /* download without applying */ 
                        val client = com.mars.planner.sync.SyncClient()
                        client.downloadBackup(s.syncHost, s.syncPort, s.syncKey)
                    }
                }
                when (result) {
                    is SyncResult.Success -> {
                        downloadedJson = json
                        conflictChoice = true
                    }
                    is SyncResult.Error -> status = redactSyncSecrets(result.message, key.trim())
                    else -> status = "Не удалось получить копию"
                }
            }
        })
        Text(status, color = MarsPeach, fontSize = 13.sp)
        Text(
            "При любой ошибке локальные данные телефона остаются нетронутыми.",
            color = MarsMuted,
            fontSize = 12.sp
        )
    }

    if (conflictChoice && downloadedJson != null) {
        AlertDialog(
            onDismissRequest = { conflictChoice = false },
            title = { Text("Как восстановить?") },
            text = {
                Text("Оставить данные телефона, заменить данными с ПК или сохранить обе версии (объединить). Перед заменой создаётся локальная резервная копия.")
            },
            confirmButton = {
                TextButton(onClick = {
                    conflictChoice = false
                    scope.launch {
                        val backup = vm.exportJson()
                        File(context.filesDir, "pre_restore_${System.currentTimeMillis()}.json").writeText(backup)
                        vm.importJson(downloadedJson!!, replace = true)
                        vm.updateSettings { it.copy(lastSyncAt = System.currentTimeMillis()) }
                        status = "Данные телефона заменены копией с ПК"
                        downloadedJson = null
                    }
                }) { Text("Оставить ПК") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        conflictChoice = false
                        downloadedJson = null
                        status = "Данные телефона сохранены без изменений"
                    }) { Text("Оставить телефон") }
                    TextButton(onClick = {
                        conflictChoice = false
                        scope.launch {
                            vm.importJson(downloadedJson!!, replace = false)
                            vm.updateSettings { it.copy(lastSyncAt = System.currentTimeMillis()) }
                            status = "Версии объединены"
                            downloadedJson = null
                        }
                    }) { Text("Сохранить обе") }
                }
            }
        )
    }
}

@Composable
internal fun IdeasScreen(vm: AppViewModel, nav: NavHostController) {
    val ideas by vm.ideas.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MarsWhite)
            }
            Text("Идеи и улучшения", color = MarsWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (ideas.isEmpty()) {
            MarsEmptyState(
                mood = MarsMood.SUPPORTIVE,
                message = "Идей пока нет"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ideas, key = { it.id }) { idea ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MarsCardDark)
                            .padding(14.dp)
                    ) {
                        Text(idea.title, color = MarsWhite, fontWeight = FontWeight.SemiBold)
                        Text(idea.status.labelRu, color = MarsPeach, fontSize = 12.sp)
                        if (idea.description.isNotBlank()) {
                            Text(idea.description, color = MarsMuted, fontSize = 13.sp)
                        }
                        TextButton(onClick = { nav.navigate(Routes.detail(idea.sourceTaskId)) }) {
                            Text("К исходной задаче", color = MarsOrange)
                        }
                    }
                }
            }
        }
    }
}
