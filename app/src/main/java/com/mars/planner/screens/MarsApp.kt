package com.mars.planner.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.data.prefs.SettingsRepository
import com.mars.planner.domain.logic.DaySummaryCalculator
import com.mars.planner.domain.logic.MoodFromDay
import com.mars.planner.domain.logic.StatsCalculator
import com.mars.planner.domain.logic.TaskFiltering
import com.mars.planner.domain.logic.TaskRules
import com.mars.planner.domain.logic.TodayTasksSelector
import com.mars.planner.domain.model.DaySummary
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.MigrationReport
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.StatsSnapshot
import com.mars.planner.domain.model.TaskFilter
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.reminder.ReminderScheduler
import com.mars.planner.sync.SyncCoordinator
import com.mars.planner.sync.SyncStepResult
import com.mars.planner.ui.components.FilterChipRow
import com.mars.planner.ui.components.MarsBackgroundPresence
import com.mars.planner.ui.components.MarsSecondaryButton
import com.mars.planner.ui.components.NewTaskCtaBar
import com.mars.planner.ui.components.ProvideReduceAnimations
import com.mars.planner.ui.components.TaskCard
import com.mars.planner.ui.components.marsListItemMotion
import com.mars.planner.ui.theme.LocalMarsPalette
import com.mars.planner.ui.theme.MarsAmbientBackground
import com.mars.planner.ui.theme.MarsBottomNavigationBar
import com.mars.planner.ui.theme.MarsMotion
import com.mars.planner.ui.theme.ProvideMarsAppearance
import com.mars.planner.ui.theme.StatusOverdue
import com.mars.planner.ui.theme.TodayStatsPanel
import com.mars.planner.ui.theme.appTheme
import com.mars.planner.ui.theme.effects
import com.mars.planner.ui.theme.rememberSystemReduceMotion
import com.mars.planner.voice.VoiceInputHelper
import com.mars.planner.voice.VoiceResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

internal object Routes {
    const val Today = "today"
    const val Tasks = "tasks"
    const val Projects = "projects"
    const val Calendar = "calendar"
    const val Stats = "stats"
    const val Settings = "settings"
    const val Sync = "sync"
    const val SyncGuide = "sync_guide"
    const val Conflicts = "conflicts"
    const val MarsImages = "mars_images"

    fun edit(id: Long? = null) = if (id == null) "task_edit?id=-1" else "task_edit?id=$id"
    fun detail(id: Long) = "task_detail/$id"
}

class AppViewModel(
    private val app: MarsApplication,
    private val planner: PlannerRepository,
    private val settingsRepo: SettingsRepository,
    private val sync: SyncCoordinator
) : ViewModel() {
    val settings = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val projects = planner.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeProjects = planner.observeActiveProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projectsWithStats = planner.observeProjectsWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTasks = planner.observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** «Сегодня»: открытые с сроком до сегодня плюс закрытые сегодня. */
    val todayTasks = allTasks
        .map { TodayTasksSelector.selectDayBoard(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val daySummary = todayTasks
        .map { DaySummaryCalculator.summarize(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DaySummary())

    val conflicts = planner.observeConflicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingSyncCount = planner.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val mood = daySummary
        .map { MoodFromDay.resolve(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarsMood.DEFAULT)

    var migrationReport by mutableStateOf<MigrationReport?>(null)
        private set

    private var migrationChecked = false

    // ——— Задачи ———

    fun taskFlow(id: Long) = planner.observeTask(id)

    suspend fun getTask(id: Long): TaskItem? = planner.getTask(id)

    suspend fun saveTask(task: TaskItem): Long {
        val id = planner.saveTask(task)
        planner.getTask(id)?.let { saved ->
            ReminderScheduler.applyTaskReminder(app, saved, settings.value.defaultSnoozeMinutes)
        }
        return id
    }

    suspend fun deleteTask(id: Long) {
        ReminderScheduler.cancelTaskReminder(app, id)
        planner.deleteTask(id)
    }

    suspend fun changeStatus(id: Long, status: TaskStatus) {
        val updated = planner.setTaskStatus(id, status) ?: return
        ReminderScheduler.applyTaskReminder(app, updated, settings.value.defaultSnoozeMinutes)
    }

    /** Перенос срока задачи: новый dueAt либо null (без срока). */
    suspend fun postpone(id: Long, dueAtEpochMillis: Long?) {
        val updated = planner.setTaskDueAt(id, dueAtEpochMillis) ?: return
        ReminderScheduler.applyTaskReminder(app, updated, settings.value.defaultSnoozeMinutes)
    }

    fun projectName(uuid: String?): String? {
        if (uuid.isNullOrBlank()) return null
        return projects.value.find { it.syncUuid == uuid }?.name
    }

    fun filterTasks(tasks: List<TaskItem>, filter: TaskFilter): List<TaskItem> =
        TaskFiltering.apply(tasks, filter, LocalDate.now())

    // ——— Проекты ———

    suspend fun saveProject(project: ProjectItem): Long = planner.saveProject(project)

    suspend fun archiveProject(id: Long, archived: Boolean) = planner.archiveProject(id, archived)

    suspend fun deleteProject(id: Long) {
        planner.getProject(id)?.let { project ->
            allTasks.value.filter { it.projectSyncUuid == project.syncUuid }.forEach {
                ReminderScheduler.cancelTaskReminder(app, it.id)
            }
        }
        planner.deleteProject(id)
    }

    // ——— Настройки ———

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsRepo.update(transform)

    fun stats(): StatsSnapshot = StatsCalculator.compute(allTasks.value)

    // ——— Отчёт о миграции ———

    suspend fun checkMigrationReportOnce() {
        if (migrationChecked) return
        migrationChecked = true
        val report = planner.migrationReport()
        if (report != null && !report.shown) migrationReport = report
    }

    suspend fun dismissMigrationReport() {
        migrationReport = null
        planner.markMigrationReportShown()
    }

    suspend fun migrationArchiveJson(): String? = planner.exportMigrationArchiveJson()

    suspend fun hasMigrationArchive(): Boolean = planner.hasMigrationArchive()

    // ——— Синхронизация «Рубеж» ———

    /** Незавершённая заявка на сопряжение: ждём подтверждения на ПК. */
    val pairing = sync.pairing

    val isPaired: Boolean get() = sync.isPaired

    suspend fun checkSyncServer(): SyncStepResult = sync.checkServer()

    suspend fun startPairing(qrRaw: String): SyncStepResult = sync.startPairing(qrRaw)

    suspend fun pollPairStatus(): SyncStepResult = sync.pollPairStatus()

    fun cancelPairing() = sync.cancelPairing()

    suspend fun unpair() = sync.unpair()

    suspend fun pushChanges(): SyncStepResult = sync.pushChanges()

    suspend fun pullChanges(): SyncStepResult = sync.pullChanges()

    suspend fun preparePcPrimarySnapshot() = sync.preparePcPrimarySnapshot()

    suspend fun applyPcPrimarySnapshot(
        preview: SyncCoordinator.SnapshotPreview,
        onStage: (SyncCoordinator.SnapshotStage) -> Unit = {}
    ): SyncStepResult = sync.applyPcPrimarySnapshot(preview, onStage)

    suspend fun retryPendingSnapshotAck(): SyncStepResult = sync.retryPendingSnapshotAck()

    suspend fun resolvePendingSnapshotAckState(): SyncCoordinator.PendingSnapshotAckState =
        sync.resolvePendingSnapshotAckState()

    suspend fun hydratePendingSnapshotAck(): String? = sync.hydratePendingSnapshotAck()

    suspend fun restorePhoneBackup(): SyncStepResult = sync.restorePhoneBackup()

    suspend fun resolveConflict(conflictId: Long, keepLocal: Boolean) =
        sync.resolveConflict(conflictId, keepLocal)

    companion object {
        fun factory(app: MarsApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        app = app,
                        planner = app.container.planner,
                        settingsRepo = app.container.settings,
                        sync = app.container.sync
                    ) as T
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
        vm.checkMigrationReportOnce()
        when ((context as? android.app.Activity)?.intent?.getStringExtra("mars_open")) {
            "mars_images" -> nav.navigate(Routes.MarsImages)
            "settings" -> nav.navigate(Routes.Settings)
            "sync" -> nav.navigate(Routes.Sync)
        }
    }

    val showBottomBar = route in setOf(
        Routes.Today, Routes.Tasks, Routes.Projects, Routes.Calendar, Routes.Settings
    )

    val settingsGlobal by vm.settings.collectAsState()
    val systemReduceMotion = rememberSystemReduceMotion()
    val reduceMotion = settingsGlobal.reduceAnimations || systemReduceMotion

    ProvideMarsAppearance(settingsGlobal.appTheme, settingsGlobal.effects) {
        ProvideReduceAnimations(reduceMotion) {
            val palette = LocalMarsPalette.current
            Scaffold(
                containerColor = palette.background,
                bottomBar = {
                    if (showBottomBar) {
                        val navItems = listOf(
                            Triple(Routes.Today, "Сегодня", Icons.Filled.Home),
                            Triple(Routes.Tasks, "Задачи", Icons.Filled.TaskAlt),
                            Triple(Routes.Projects, "Проекты", Icons.Filled.Folder),
                            Triple(Routes.Calendar, "Календарь", Icons.Filled.CalendarMonth),
                            Triple(Routes.Settings, "Настройки", Icons.Filled.Settings)
                        )
                        MarsBottomNavigationBar(
                            route = route,
                            items = navItems,
                            onNavigate = { r -> nav.navigate(r) { launchSingleTop = true } }
                        )
                    }
                }
            ) { padding ->
                val taskEnter = fadeIn(tween(MarsMotion.NavTransitionMs)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(MarsMotion.NavTransitionMs)) +
                    slideInHorizontally(animationSpec = tween(MarsMotion.NavTransitionMs)) { it / 12 }
                val taskExit = fadeOut(tween(200)) +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(200)) +
                    slideOutHorizontally(animationSpec = tween(200)) { it / 14 }
                val taskPopEnter = fadeIn(tween(240)) +
                    slideInHorizontally(animationSpec = tween(240)) { -it / 14 }
                val taskPopExit = fadeOut(tween(200)) +
                    scaleOut(targetScale = 0.96f, animationSpec = tween(200)) +
                    slideOutHorizontally(animationSpec = tween(200)) { it / 12 }
                val noMotionEnter: EnterTransition = EnterTransition.None
                val noMotionExit: ExitTransition = ExitTransition.None

                NavHost(
                    navController = nav,
                    startDestination = Routes.Today,
                    modifier = Modifier.padding(padding)
                ) {
                    composable(Routes.Today) { TodayScreen(vm, nav) }
                    composable(Routes.Tasks) { TasksScreen(vm, nav) }
                    composable(Routes.Projects) { ProjectsScreen(vm, nav) }
                    composable(Routes.Calendar) { CalendarScreen(vm, nav) }
                    composable(Routes.Stats) { StatsScreen(vm, nav) }
                    composable(Routes.Settings) { SettingsScreen(vm, nav) }
                    composable(Routes.MarsImages) { MarsImagesPreviewScreen(nav) }
                    composable(Routes.Sync) { SyncScreen(vm, nav) }
                    composable(Routes.SyncGuide) { SyncGuideScreen(nav) }
                    composable(Routes.Conflicts) { ConflictsScreen(vm, nav) }
                    composable(
                        route = "task_edit?id={id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                        enterTransition = { if (reduceMotion) noMotionEnter else taskEnter },
                        exitTransition = { if (reduceMotion) noMotionExit else taskExit },
                        popEnterTransition = { if (reduceMotion) noMotionEnter else taskPopEnter },
                        popExitTransition = { if (reduceMotion) noMotionExit else taskPopExit }
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: -1L
                        TaskEditScreen(vm, nav, if (id < 0) null else id)
                    }
                    composable(
                        route = "task_detail/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        enterTransition = { if (reduceMotion) noMotionEnter else taskEnter },
                        exitTransition = { if (reduceMotion) noMotionExit else taskExit },
                        popEnterTransition = { if (reduceMotion) noMotionEnter else taskPopEnter },
                        popExitTransition = { if (reduceMotion) noMotionExit else taskPopExit }
                    ) { entry ->
                        TaskDetailScreen(vm, nav, entry.arguments!!.getLong("id"))
                    }
                }
            }

            MigrationReportDialog(vm)
        }
    }
}

@Composable
private fun MigrationReportDialog(vm: AppViewModel) {
    val report = vm.migrationReport ?: return
    val palette = LocalMarsPalette.current
    val scope = rememberCoroutineScope()
    val dismiss = { scope.launch { vm.dismissMigrationReport() } }
    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text("Данные перенесены в новую модель") },
        text = {
            Column {
                Text(
                    "Ежедневник перешёл на модель «Рубежа»: проекты и задачи со статусами " +
                        "«Открыта» и «Выполнено».",
                    color = palette.textMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Создано проектов из категорий: ${report.projectsCreated}", color = palette.text, fontSize = 13.sp)
                Text("Перенесено задач: ${report.tasksMigrated}", color = palette.text, fontSize = 13.sp)
                Text("Подзадач стали отдельными задачами: ${report.subtasksConverted}", color = palette.text, fontSize = 13.sp)
                Text("Дополнений стали задачами: ${report.enhancementsConverted}", color = palette.text, fontSize = 13.sp)
                Text("Отменённых задач помечено выполненными: ${report.cancelledToDone}", color = palette.text, fontSize = 13.sp)
                Text("Задач со сроком на 23:59: ${report.dueAt2359Count}", color = palette.text, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Архив старых данных сохранён внутри приложения. При наличии его можно выгрузить " +
                        "в Настройках → «Восстановление».",
                    color = palette.textMuted,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { dismiss() }) { Text("Понятно") }
        }
    )
}

@Composable
internal fun ScreenBackground(content: @Composable () -> Unit) {
    MarsAmbientBackground(content = content)
}

@Composable
internal fun TodayScreen(vm: AppViewModel, nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val tasks by vm.todayTasks.collectAsState()
    val summary by vm.daySummary.collectAsState()
    val moodByLogic by vm.mood.collectAsState()
    val pendingSync by vm.pendingSyncCount.collectAsState()
    val conflicts by vm.conflicts.collectAsState()
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    val dateLabel = remember { LocalDate.now().format(ruDateFull) }
    val todayDate = remember { LocalDate.now() }
    val filtered = remember(tasks, filter) { vm.filterTasks(tasks, filter) }
    val context = LocalContext.current
    // QA: adb am start … --es mars_mood mars_postponed (или done/working/supportive/…)
    val activity = context as? android.app.Activity
    val moodOverride = activity?.intent?.getStringExtra("mars_mood")?.let { raw ->
        MarsMood.entries.find {
            it.assetBase.equals(raw, ignoreCase = true) ||
                it.name.equals(raw, ignoreCase = true) ||
                it.assetBase.removePrefix("mars_").equals(raw, ignoreCase = true)
        }
    }
    val mood = moodOverride ?: moodByLogic
    var voiceMessage by remember { mutableStateOf<String?>(null) }
    val voiceHelper = remember { VoiceInputHelper(context) }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceHelper.startListening { result ->
                when (result) {
                    is VoiceResult.Success -> {
                        PendingVoiceTitle.value = result.text
                        nav.navigate(Routes.edit())
                    }
                    is VoiceResult.Error -> voiceMessage = result.message
                    VoiceResult.Unavailable -> voiceMessage = "Распознавание недоступно"
                }
            }
        } else {
            voiceMessage = "Нужно разрешение на микрофон"
        }
    }

    val displayMood = mood
    val marsAlpha = if (tasks.isEmpty()) 0.58f else 0.40f
    val contentWidth = Modifier.fillMaxWidth(0.64f)
    var marsNudge by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scrollOffsetPx by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex * 120f + listState.firstVisibleItemScrollOffset
        }
    }

    ScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            MarsBackgroundPresence(
                mood = displayMood,
                presenceAlpha = marsAlpha,
                scrollOffsetPx = scrollOffsetPx,
                interactionNudge = marsNudge,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 76.dp)
            )
            val pagePad = Modifier.padding(horizontal = 20.dp)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(modifier = pagePad.padding(top = 16.dp, bottom = 4.dp)) {
                        Text(
                            text = "Ежедневник Марса",
                            color = palette.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Сегодня",
                            color = palette.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            lineHeight = 36.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = dateLabel, color = palette.textMuted, fontSize = 14.sp)
                    }
                }
                item {
                    TodayStatsPanel(summary = summary, modifier = pagePad)
                }
                item {
                    Row(modifier = pagePad.horizontalScroll(rememberScrollState())) {
                        FilterChipRow(filter) { filter = it }
                    }
                }
                if (conflicts.isNotEmpty()) {
                    item {
                        Column(modifier = pagePad) {
                            Text(
                                text = "Расхождений с ПК: ${conflicts.size}",
                                color = StatusOverdue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            MarsSecondaryButton(
                                "Разобрать расхождения",
                                onClick = { nav.navigate(Routes.Conflicts) }
                            )
                        }
                    }
                }
                item {
                    Row(modifier = pagePad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarsSecondaryButton(
                            text = if (pendingSync > 0) "Синхронизация ($pendingSync)" else "Синхронизация",
                            onClick = { nav.navigate(Routes.Sync) }
                        )
                        MarsSecondaryButton("Статистика", onClick = { nav.navigate(Routes.Stats) })
                    }
                }
                if (voiceMessage != null) {
                    item {
                        Text(voiceMessage!!, color = palette.highlight, fontSize = 13.sp, modifier = pagePad)
                    }
                }
                if (tasks.isEmpty()) {
                    item {
                        Column(
                            modifier = pagePad
                                .then(contentWidth)
                                .padding(top = 12.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = "На сегодня задач нет",
                                color = palette.textMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "День можно оставить свободным или добавить важное.",
                                color = palette.textMuted.copy(alpha = 0.78f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                itemsIndexed(filtered, key = { _, task -> task.id }) { index, task ->
                    val overdue = TaskRules.isOverdue(task, todayDate)
                    Column(
                        modifier = pagePad
                            .then(contentWidth)
                            .marsListItemMotion(index)
                    ) {
                        TaskCard(
                            task = task,
                            isOverdue = overdue,
                            dueDateLabel = dueLabel(task.dueAtEpochMillis),
                            projectName = vm.projectName(task.projectSyncUuid),
                            onClick = { nav.navigate(Routes.detail(task.id)) }
                        )
                    }
                }
            }
            NewTaskCtaBar(
                onNewTask = {
                    marsNudge++
                    nav.navigate(Routes.edit())
                },
                onVoice = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

object PendingVoiceTitle {
    var value: String? = null
}
