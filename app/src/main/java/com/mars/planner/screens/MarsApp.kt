package com.mars.planner.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mars.planner.MarsApplication
import com.mars.planner.data.TaskRepository
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.data.prefs.SettingsRepository
import com.mars.planner.domain.logic.DaySummaryCalculator
import com.mars.planner.domain.logic.MoodFromDay
import com.mars.planner.domain.logic.StatsCalculator
import com.mars.planner.domain.logic.TaskRules
import com.mars.planner.domain.model.DaySummary
import com.mars.planner.domain.model.EnhancementIdea
import com.mars.planner.domain.model.EnhancementStatus
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.MotivatorMode
import com.mars.planner.domain.model.StatsSnapshot
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.domain.model.TaskWithDetails
import com.mars.planner.export.BackupCodec
import com.mars.planner.export.toAppSettings
import com.mars.planner.export.toDomain
import com.mars.planner.motivator.MarsMotivator
import com.mars.planner.motivator.MarsReaction
import com.mars.planner.reminder.ReminderScheduler
import com.mars.planner.reminder.nextReminderMillis
import com.mars.planner.sync.SyncClient
import com.mars.planner.sync.SyncResult
import com.mars.planner.ui.components.FilterChipRow
import com.mars.planner.ui.components.MarsAvatar
import com.mars.planner.ui.components.MarsReactionBanner
import com.mars.planner.ui.components.MarsSecondaryButton
import com.mars.planner.ui.components.NewTaskCtaBar
import com.mars.planner.ui.components.SummaryChip
import com.mars.planner.ui.components.TaskCard
import com.mars.planner.ui.theme.MarsCardDark
import com.mars.planner.ui.theme.MarsGraphite
import com.mars.planner.ui.theme.MarsMuted
import com.mars.planner.ui.theme.MarsOrange
import com.mars.planner.ui.theme.MarsPeach
import com.mars.planner.ui.theme.MarsWhite
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusNotDone
import com.mars.planner.ui.theme.StatusProgress
import com.mars.planner.voice.VoiceInputHelper
import com.mars.planner.voice.VoiceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object Routes {
    const val Today = "today"
    const val Tasks = "tasks"
    const val Calendar = "calendar"
    const val Stats = "stats"
    const val Settings = "settings"
    const val Sync = "sync"
    const val Ideas = "ideas"

    fun edit(id: Long? = null) = if (id == null) "task_edit?id=-1" else "task_edit?id=$id"
    fun detail(id: Long) = "task_detail/$id"
}

class AppViewModel(
    private val tasks: TaskRepository,
    private val settingsRepo: SettingsRepository,
    private val syncClient: SyncClient
) : ViewModel() {
    private val today = LocalDate.now().toEpochDay()

    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val todayTasks = tasks.observeDay(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allRootTasks = tasks.observeRootTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Все задачи включая подзадачи — только для прогресса и поиска по id. */
    val allTasks = tasks.observeEveryTask().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ideas = tasks.observeIdeas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val daySummary = todayTasks.map { DaySummaryCalculator.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DaySummary())

    fun subtaskProgressLabel(rootId: Long): String? {
        val subs = allTasks.value.filter { it.parentTaskId == rootId }
        if (subs.isEmpty()) return null
        val done = subs.count { it.status == TaskStatus.DONE }
        return "Подзадачи: $done из ${subs.size} выполнено"
    }

    val mood = combine(daySummary, settings) { summary, s ->
        if (s.motivatorMode == MotivatorMode.OFF) MarsMood.DEFAULT else MoodFromDay.resolve(summary)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarsMood.DEFAULT)

    var reaction by mutableStateOf<MarsReaction?>(null)
        private set

    fun showReaction(value: MarsReaction?) {
        reaction = value
    }

    fun clearReaction() {
        reaction = null
    }

    suspend fun saveTask(
        task: TaskItem,
        scheduleReminder: Boolean,
        context: android.content.Context,
        snoozeMinutes: Int = 30
    ): Long {
        val id = tasks.saveTask(task)
        if (scheduleReminder && task.reminderAtEpochMillis != null) {
            ReminderScheduler.scheduleTaskReminder(
                context, id, task.title, task.reminderAtEpochMillis, snoozeMinutes
            )
            updateSettings { it.copy(defaultSnoozeMinutes = snoozeMinutes) }
        } else if (task.reminderAtEpochMillis == null) {
            ReminderScheduler.cancelTaskReminder(context, id)
        }
        return id
    }

    suspend fun deleteTask(id: Long, context: android.content.Context) {
        ReminderScheduler.cancelTaskReminder(context, id)
        tasks.deleteTask(id)
    }

    /** Удаляет задачу вместе с прямыми подзадачами (с явным подтверждением в UI). */
    suspend fun deleteTaskCascade(id: Long, context: android.content.Context) {
        val children = tasks.exportSnapshot().first.filter { it.parentTaskId == id }
        children.forEach { child ->
            ReminderScheduler.cancelTaskReminder(context, child.id)
            tasks.deleteTask(child.id)
        }
        deleteTask(id, context)
    }

    suspend fun changeStatus(
        id: Long,
        status: TaskStatus,
        forceComplete: Boolean = false
    ): Pair<Boolean, String?> {
        val details = tasks.getTask(id) ?: return false to null
        val subtasks = withContext(Dispatchers.IO) {
            // details via export + filter
            tasks.exportSnapshot().first.filter { it.parentTaskId == id }
        }
        val incomplete = subtasks.any { it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED }
        if (status == TaskStatus.DONE && incomplete && !forceComplete) {
            return false to "Есть незавершённые подзадачи. Вы действительно хотите завершить основную задачу?"
        }
        val updated = tasks.updateStatus(id, status) ?: return false to null
        val mode = settings.value.motivatorMode
        reaction = MarsMotivator.reactionForStatusChange(status, updated.postponeCount, mode)
        return true to null
    }

    suspend fun postpone(id: Long, day: Long, time: Int?, reason: String?) {
        val updated = tasks.postponeTask(id, day, time, reason) ?: return
        reaction = MarsMotivator.reactionForStatusChange(
            TaskStatus.POSTPONED,
            updated.postponeCount,
            settings.value.motivatorMode
        )
    }

    fun taskDetails(id: Long) = tasks.observeTaskDetails(id)

    suspend fun addSubtask(parentId: Long, title: String) = tasks.addSubtask(parentId, title)
    suspend fun saveEnhancement(idea: EnhancementIdea) = tasks.saveEnhancement(idea)
    suspend fun deferEnhancement(id: Long, reason: String?) {
        tasks.deferEnhancement(id, reason)
        reaction = MarsMotivator.reactionForDeferredEnhancement()
    }
    suspend fun convertEnhancement(id: Long) = tasks.convertEnhancementToTask(id)

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsRepo.update(transform)

    suspend fun exportJson(): String {
        val (t, e) = tasks.exportSnapshot()
        return BackupCodec.toJson(t, e, settings.value)
    }

    suspend fun exportCsv(): String {
        val (t, _) = tasks.exportSnapshot()
        // CSV — только основные задачи верхнего уровня (без подзадач).
        return BackupCodec.toCsv(TaskRules.onlyRootTasks(t))
    }

    suspend fun importJson(json: String, replace: Boolean) {
        val payload = BackupCodec.fromJson(json)
        val taskModels = payload.tasks.map { it.toDomain() }
        val enhModels = payload.enhancements.map { it.toDomain() }
        if (replace) {
            // локальная резервная копия уже должна быть создана вызывающим кодом
            tasks.replaceAll(taskModels, enhModels)
        } else {
            tasks.mergeImport(taskModels, enhModels)
        }
        payload.settings?.let { dto ->
            settingsRepo.update { dto.toAppSettings(it) }
        }
    }

    suspend fun loadDemo() {
        tasks.loadDemoIfNeeded()
        settingsRepo.update { it.copy(demoLoaded = true) }
    }

    suspend fun clearDemo() {
        tasks.clearDemo()
        settingsRepo.update { it.copy(demoLoaded = false) }
    }

    fun stats(): StatsSnapshot = StatsCalculator.compute(allRootTasks.value)

    fun syncCheck(): com.mars.planner.sync.SyncServerInfo {
        val s = settings.value
        return syncClient.checkConnection(s.syncHost, s.syncPort, s.syncKey)
    }

    suspend fun syncUpload(): SyncResult {
        val s = settings.value
        val json = exportJson()
        val result = syncClient.uploadBackup(s.syncHost, s.syncPort, s.syncKey, json)
        if (result is SyncResult.Success) {
            settingsRepo.update { it.copy(lastSyncAt = System.currentTimeMillis()) }
        }
        return result
    }

    suspend fun syncDownload(replace: Boolean): SyncResult {
        val s = settings.value
        val (result, json) = syncClient.downloadBackup(s.syncHost, s.syncPort, s.syncKey)
        if (result is SyncResult.Success && json != null) {
            importJson(json, replace)
            settingsRepo.update { it.copy(lastSyncAt = System.currentTimeMillis()) }
        }
        return result
    }

    companion object {
        fun factory(app: MarsApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(app.container.tasks, app.container.settings, app.container.sync) as T
                }
            }
    }
}

@Composable
fun MarsApp() {
    val context = LocalContext.current
    val app = context.applicationContext as MarsApplication
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(app))
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.Today

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val settings = vm.settings.value
        if (!settings.demoLoaded) {
            // демо не грузим автоматически — только из настроек
        }
    }

    val showBottomBar = route in setOf(
        Routes.Today, Routes.Tasks, Routes.Calendar, Routes.Stats, Routes.Settings
    )

    Scaffold(
        containerColor = MarsGraphite,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MarsCardDark) {
                    val items = listOf(
                        Triple(Routes.Today, "Сегодня", Icons.Filled.Home),
                        Triple(Routes.Tasks, "Задачи", Icons.Filled.TaskAlt),
                        Triple(Routes.Calendar, "Календарь", Icons.Filled.CalendarMonth),
                        Triple(Routes.Stats, "Статистика", Icons.Outlined.Insights),
                        Triple(Routes.Settings, "Настройки", Icons.Filled.Settings)
                    )
                    items.forEach { (r, label, icon) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = { nav.navigate(r) { launchSingleTop = true } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MarsOrange,
                                selectedTextColor = MarsOrange,
                                indicatorColor = MarsOrange.copy(alpha = 0.15f),
                                unselectedIconColor = MarsMuted,
                                unselectedTextColor = MarsMuted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.Today,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.Today) { TodayScreen(vm, nav) }
            composable(Routes.Tasks) { TasksScreen(vm, nav) }
            composable(Routes.Calendar) { CalendarScreen(vm, nav) }
            composable(Routes.Stats) { StatsScreen(vm) }
            composable(Routes.Settings) { SettingsScreen(vm, nav) }
            composable(Routes.Sync) { SyncScreen(vm, nav) }
            composable(Routes.Ideas) { IdeasScreen(vm, nav) }
            composable(
                route = "task_edit?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                TaskEditScreen(vm, nav, if (id < 0) null else id)
            }
            composable(
                route = "task_detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                TaskDetailScreen(vm, nav, entry.arguments!!.getLong("id"))
            }
        }
    }
}

@Composable
private fun ScreenBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MarsGraphite, MarsGraphite, MarsCardDark.copy(alpha = 0.35f))
                )
            )
    ) { content() }
}

@Composable
private fun TodayScreen(vm: AppViewModel, nav: NavHostController) {
    val tasks by vm.todayTasks.collectAsState()
    val allTasks by vm.allTasks.collectAsState()
    val summary by vm.daySummary.collectAsState()
    val mood by vm.mood.collectAsState()
    val settings by vm.settings.collectAsState()
    val reaction = vm.reaction
    var filter by remember { mutableStateOf<TaskStatus?>(null) }
    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
    }
    val filtered = tasks.filter { filter == null || it.status == filter }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var voiceMessage by remember { mutableStateOf<String?>(null) }
    val voiceHelper = remember { VoiceInputHelper(context) }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceHelper.startListening { result ->
                when (result) {
                    is VoiceResult.Success -> {
                        nav.navigate(Routes.edit())
                        // title will be passed via savedState later; store in static holder
                        PendingVoiceTitle.value = result.text
                    }
                    is VoiceResult.Error -> voiceMessage = result.message
                    VoiceResult.Unavailable -> voiceMessage = "Распознавание недоступно"
                }
            }
        } else {
            voiceMessage = "Нужно разрешение на микрофон"
        }
    }

    LaunchedEffect(summary.overdue, settings.motivatorMode) {
        if (summary.overdue >= 2 && settings.motivatorMode != MotivatorMode.OFF && reaction == null) {
            vm.showReaction(MarsMotivator.reactionForManyOverdue(summary.overdue, settings.motivatorMode))
        }
    }

    ScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                Text("Ежедневник Марса", color = MarsOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = MarsMotivator.greetingMessage(settings.userName, summary.done, summary.total),
                    color = MarsWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 32.sp
                )
                Text(dateLabel, color = MarsMuted, fontSize = 14.sp)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarsAvatar(mood = mood, size = 96.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Настроение дня", color = MarsMuted, fontSize = 12.sp)
                        Text(
                            when (mood) {
                                MarsMood.DONE -> "Марс доволен"
                                MarsMood.WORKING -> "Марс сосредоточен"
                                MarsMood.POSTPONED -> "Марс озадачен"
                                MarsMood.OVERDUE -> "Марс ждёт решения"
                                MarsMood.STRICT -> "Марс настроен серьёзно"
                                MarsMood.SUPPORTIVE -> "Марс поддерживает"
                                MarsMood.DEFAULT -> "Марс спокоен"
                            },
                            color = MarsWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
            if (reaction != null) {
                item {
                    MarsReactionBanner(reaction.mood, reaction.message, Modifier.clickable { vm.clearReaction() })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryChip("Всего", summary.total, MarsWhite)
                    SummaryChip("Готово", summary.done, StatusDone)
                    SummaryChip("В работе", summary.inProgress, StatusProgress)
                    SummaryChip("Просрочено", summary.overdue, StatusNotDone)
                }
            }
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChipRow(filter) { filter = it }
                }
            }
            item {
                MarsSecondaryButton("Синхронизировать с ПК", onClick = { nav.navigate(Routes.Sync) })
            }
            if (voiceMessage != null) {
                item {
                    Text(voiceMessage!!, color = MarsPeach, fontSize = 13.sp)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text("На сегодня задач нет. Добавь первую — Марс рядом.", color = MarsMuted)
                }
            }
            items(filtered, key = { it.id }) { task ->
                val progress = remember(task.id, allTasks) {
                    val subs = allTasks.filter { it.parentTaskId == task.id }
                    if (subs.isEmpty()) null
                    else {
                        val done = subs.count { it.status == TaskStatus.DONE }
                        "Подзадачи: $done из ${subs.size} выполнено"
                    }
                }
                TaskCard(
                    task = task,
                    onClick = { nav.navigate(Routes.detail(task.id)) },
                    subtaskProgress = progress
                )
            }
            }
            NewTaskCtaBar(
                onNewTask = { nav.navigate(Routes.edit()) },
                onVoice = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

object PendingVoiceTitle {
    var value: String? = null
}
