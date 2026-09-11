package com.mars.planner.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.mars.planner.domain.logic.TaskGroupsLogic
import com.mars.planner.domain.logic.TaskRules
import com.mars.planner.domain.logic.TaskStatusToggle
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.domain.model.EffectIntensity
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.TaskFilter
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.ui.components.FilterChipRow
import com.mars.planner.ui.components.MarsBackgroundPresence
import com.mars.planner.ui.components.MarsChoiceChip
import com.mars.planner.ui.components.MarsEmptyState
import com.mars.planner.ui.components.MarsSecondaryButton
import com.mars.planner.ui.components.NewTaskCtaBar
import com.mars.planner.ui.components.StatusDot
import com.mars.planner.ui.components.SummaryChip
import com.mars.planner.ui.components.TaskCard
import com.mars.planner.ui.components.labelRu
import com.mars.planner.ui.components.marsListItemMotion
import com.mars.planner.ui.components.marsPreviewOptions
import com.mars.planner.ui.components.previewLabelRu
import com.mars.planner.ui.theme.LocalMarsPalette
import com.mars.planner.ui.theme.MarsOutline
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusOpen
import com.mars.planner.ui.theme.StatusOverdue
import com.mars.planner.ui.theme.ThemeCalendarPanel
import com.mars.planner.ui.theme.ThemeSceneBackgrounds
import com.mars.planner.ui.theme.ThemeSceneScreen
import com.mars.planner.ui.theme.ThemeSceneShell
import com.mars.planner.ui.theme.appTheme
import com.mars.planner.ui.theme.effects
import com.mars.planner.ui.theme.paletteFor
import com.mars.planner.ui.theme.withEffects
import com.mars.planner.ui.theme.withTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

internal val marsFieldColors: TextFieldColors
    @Composable get() {
        val palette = LocalMarsPalette.current
        return OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.textMuted.copy(alpha = 0.4f),
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            cursorColor = palette.accent,
            focusedLabelColor = palette.accent,
            unfocusedLabelColor = palette.textMuted
        )
    }

@Composable
internal fun ScreenTitleRow(
    title: String,
    nav: NavHostController?,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (nav != null) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = LocalMarsPalette.current.text)
            }
        }
        Text(
            text = title,
            color = LocalMarsPalette.current.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
internal fun TasksScreen(vm: AppViewModel, nav: NavHostController) {
    val allTasks by vm.allTasks.collectAsState()
    val projects by vm.projects.collectAsState()
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    val today = remember { LocalDate.now() }
    val palette = LocalMarsPalette.current

    val searched = allTasks.filter { task ->
        val projectName = projects.find { it.syncUuid == task.projectSyncUuid }?.name.orEmpty()
        query.isBlank() ||
            task.title.contains(query, true) ||
            task.description.contains(query, true) ||
            projectName.contains(query, true)
    }
    val filtered = remember(searched, filter) { vm.filterTasks(searched, filter) }
    val doneCount = searched.count { it.status == TaskStatus.DONE }
    val openCount = searched.count { it.status == TaskStatus.OPEN }
    val overdueCount = searched.count { TaskRules.isOverdue(it, today) }

    val groups = remember(filtered, projects, settings.tasksNoProjectPinnedTop) {
        TaskGroupsLogic.buildGroups(
            projects = projects,
            filteredTasks = filtered,
            noProjectPinnedTop = settings.tasksNoProjectPinnedTop
        )
    }
    val storedExpanded = remember(settings.tasksGroupExpandedKeys) {
        TaskGroupsLogic.decodeExpandedKeys(settings.tasksGroupExpandedKeys)
    }
    var pendingToggleKeys by remember { mutableStateOf(setOf<String>()) }
    /** Монотонные метки принятых жестов (SystemClock.elapsedRealtime), по syncUuid. */
    var lastToggleAcceptedAtMs by remember { mutableStateOf(mapOf<String, Long>()) }

    // Drop stale expand keys for deleted projects without writing sync data.
    LaunchedEffect(groups, settings.tasksGroupsUserConfigured, settings.tasksGroupExpandedKeys) {
        if (!settings.tasksGroupsUserConfigured) return@LaunchedEffect
        val pruned = TaskGroupsLogic.pruneExpandedKeys(storedExpanded, groups)
        if (pruned != storedExpanded) {
            vm.updateSettings {
                it.copy(tasksGroupExpandedKeys = TaskGroupsLogic.encodeExpandedKeys(pruned))
            }
        }
    }

    fun persistToggle(key: String) {
        scope.launch {
            val (configured, next) = TaskGroupsLogic.toggleExpanded(
                key = key,
                groups = groups,
                userConfigured = settings.tasksGroupsUserConfigured,
                storedExpandedKeys = storedExpanded
            )
            vm.updateSettings {
                it.copy(
                    tasksGroupsUserConfigured = configured,
                    tasksGroupExpandedKeys = TaskGroupsLogic.encodeExpandedKeys(next)
                )
            }
        }
    }

    fun toggleDone(task: TaskItem) {
        val key = task.syncUuid
        val now = android.os.SystemClock.elapsedRealtime()
        if (!TaskStatusToggle.acceptGesture(
                taskKey = key,
                pendingKeys = pendingToggleKeys,
                lastAcceptedAtMs = lastToggleAcceptedAtMs,
                nowElapsedMs = now
            )
        ) {
            return
        }
        lastToggleAcceptedAtMs = TaskStatusToggle.recordAccepted(lastToggleAcceptedAtMs, key, now)
        pendingToggleKeys = pendingToggleKeys + key
        scope.launch {
            try {
                vm.changeStatus(task.id, TaskStatusToggle.nextStatus(task.status))
            } finally {
                pendingToggleKeys = pendingToggleKeys - key
            }
        }
    }

    val tasksBody: @Composable ColumnScope.() -> Unit = {
        Text("Задачи", color = palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск по задачам и проектам") },
            colors = marsFieldColors,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryChip("Всего", searched.size, palette.text)
            SummaryChip("Готово", doneCount, StatusDone)
            SummaryChip("Открыто", openCount, StatusOpen)
            SummaryChip("Просрочено", overdueCount, StatusOverdue)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChipRow(filter) { filter = it }
        }
        Spacer(modifier = Modifier.height(12.dp))
        MarsSecondaryButton("Проекты", onClick = { nav.navigate(Routes.Projects) })
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        MarsEmptyState(
                            mood = MarsMood.DEFAULT,
                            message = when {
                                query.isNotBlank() -> "Ничего не найдено"
                                filter != TaskFilter.ALL -> "Нет задач в этом фильтре"
                                else -> "Пока нет задач"
                            }
                        )
                    }
                }
                groups.forEach { group ->
                    val expanded = TaskGroupsLogic.isExpanded(
                        key = group.key,
                        groups = groups,
                        userConfigured = settings.tasksGroupsUserConfigured,
                        storedExpandedKeys = storedExpanded
                    )
                    item(key = "hdr-${group.key}") {
                        TaskGroupHeader(
                            title = group.title,
                            count = group.count,
                            expanded = expanded,
                            showNoProjectMenu = group.isNoProject,
                            pinnedTop = settings.tasksNoProjectPinnedTop,
                            onToggle = { persistToggle(group.key) },
                            onPinTop = {
                                scope.launch {
                                    vm.updateSettings { it.copy(tasksNoProjectPinnedTop = true) }
                                }
                            },
                            onUnpin = {
                                scope.launch {
                                    vm.updateSettings { it.copy(tasksNoProjectPinnedTop = false) }
                                }
                            }
                        )
                    }
                    if (expanded) {
                        items(group.tasks, key = { "task-${group.key}-${it.id}" }) { task ->
                            TaskCard(
                                task = task,
                                isOverdue = TaskRules.isOverdue(task, today),
                                dueDateLabel = dueLabel(task.dueAtEpochMillis),
                                projectName = null,
                                onClick = { nav.navigate(Routes.detail(task.id)) },
                                onToggleDone = { toggleDone(task) },
                                toggleEnabled = TaskStatusToggle.acceptGesture(
                                    task.syncUuid,
                                    pendingToggleKeys
                                ),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
            NewTaskCtaBar(
                onNewTask = { nav.navigate(Routes.edit()) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    ThemeSceneShell(
        screen = ThemeSceneScreen.Tasks,
        fallback = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp),
                content = tasksBody
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp),
                content = tasksBody
            )
        }
    )
}

@Composable
private fun TaskGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    showNoProjectMenu: Boolean,
    pinnedTop: Boolean,
    onToggle: () -> Unit,
    onPinTop: () -> Unit,
    onUnpin: () -> Unit
) {
    val palette = LocalMarsPalette.current
    var menuOpen by remember { mutableStateOf(false) }
    val expandLabel = if (expanded) "Свернуть группу $title" else "Развернуть группу $title"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.backgroundElevated.copy(alpha = 0.92f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { contentDescription = expandLabel },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = palette.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            color = palette.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        if (showNoProjectMenu) {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Меню группы Без проекта" }
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = palette.textMuted
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    if (!pinnedTop) {
                        DropdownMenuItem(
                            text = { Text("Закрепить наверху") },
                            onClick = {
                                menuOpen = false
                                onPinTop()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Вернуть вниз") },
                            onClick = {
                                menuOpen = false
                                onUnpin()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CalendarScreen(vm: AppViewModel, nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val all by vm.allTasks.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    val today = remember { LocalDate.now() }

    val byDay = remember(all) {
        all.filter { it.dueAtEpochMillis != null }.groupBy { dueDate(it.dueAtEpochMillis!!) }
    }
    val dayTasks = byDay[selectedDay].orEmpty()
    val firstDow = month.atDay(1).dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()

    val sceneTheme = ThemeSceneBackgrounds.hasScene(palette.theme)

    val calendarBody: @Composable ColumnScope.() -> Unit = {
        val grid: @Composable ColumnScope.() -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", java.util.Locale("ru"))),
                    color = palette.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("<", color = palette.accent) }
                TextButton(onClick = {
                    month = YearMonth.now()
                    selectedDay = LocalDate.now()
                }) {
                    Text(
                        "Сегодня",
                        color = palette.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = { month = month.plusMonths(1) }) { Text(">", color = palette.accent) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                    Text(it, color = palette.textMuted, modifier = Modifier.width(40.dp), fontSize = 12.sp)
                }
            }
            val cells = buildList {
                repeat((firstDow + 6) % 7) { add(null) }
                for (d in 1..daysInMonth) add(month.atDay(d))
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { day ->
                        val dayList = if (day == null) emptyList() else byDay[day].orEmpty()
                        val hasOverdue = dayList.any { TaskRules.isOverdue(it, today) }
                        val allDone = dayList.isNotEmpty() && dayList.all { it.status == TaskStatus.DONE }
                        val selected = day == selectedDay
                        val isToday = day == today
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        day == null -> Color.Transparent
                                        selected -> palette.accent.copy(alpha = if (sceneTheme) 0.42f else 0.25f)
                                        isToday -> if (sceneTheme) palette.accent.copy(alpha = 0.18f) else palette.card
                                        else -> if (sceneTheme) palette.card.copy(alpha = 0.92f) else Color.Transparent
                                    }
                                )
                                .then(
                                    if (sceneTheme && (selected || isToday)) {
                                        Modifier.border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = palette.accent.copy(alpha = if (selected) 1f else 0.55f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(enabled = day != null) { selectedDay = day!! },
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        day.dayOfMonth.toString(),
                                        color = palette.text,
                                        fontSize = 13.sp,
                                        fontWeight = if (sceneTheme && (selected || isToday)) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                    )
                                    if (dayList.isNotEmpty()) {
                                        Text(
                                            text = dayList.size.toString(),
                                            color = when {
                                                hasOverdue -> StatusOverdue
                                                allDone -> StatusDone
                                                else -> palette.accent
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = if (sceneTheme) FontWeight.SemiBold else FontWeight.Normal
                                        )
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
        }

        if (sceneTheme) {
            ThemeCalendarPanel(content = grid)
        } else {
            grid()
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Задачи дня", color = palette.text, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        if (dayTasks.isEmpty()) {
            MarsEmptyState(mood = MarsMood.DEFAULT, message = "В этот день задач нет")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        isOverdue = TaskRules.isOverdue(task, today),
                        dueDateLabel = dueLabel(task.dueAtEpochMillis),
                        projectName = vm.projectName(task.projectSyncUuid),
                        onClick = { nav.navigate(Routes.detail(task.id)) }
                    )
                }
            }
        }
    }

    ThemeSceneShell(
        screen = ThemeSceneScreen.Calendar,
        fallback = {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                content = calendarBody
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                content = calendarBody
            )
        }
    )
}

@Composable
internal fun StatsScreen(vm: AppViewModel, nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val all by vm.allTasks.collectAsState()
    val stats = remember(all) { vm.stats() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitleRow("Статистика", nav)
        StatCard("Выполнено за неделю", stats.completedWeek.toString(), StatusDone)
        StatCard("Выполнено за месяц", stats.completedMonth.toString(), StatusDone)
        StatCard("Открытых задач", stats.openCount.toString(), StatusOpen)
        StatCard("Просрочено сейчас", stats.overdueCount.toString(), StatusOverdue)
        StatCard("Процент выполнения (месяц)", "${stats.completionPercent}%", palette.highlight)
        StatCard("Серия продуктивных дней", stats.productiveStreak.toString(), palette.accent)
        Text(
            "День продуктивный, если выполнена хотя бы одна задача.",
            color = palette.textMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, accent: Color) {
    val palette = LocalMarsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.card)
            .padding(18.dp)
    ) {
        Text(label, color = palette.textMuted, fontSize = 13.sp)
        Text(value, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val settings by vm.settings.collectAsState()
    val pendingSync by vm.pendingSyncCount.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var pendingArchiveJson by remember { mutableStateOf<String?>(null) }
    var hasMigrationArchive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasMigrationArchive = vm.hasMigrationArchive()
    }

    val archiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingArchiveJson
        pendingArchiveJson = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            }
            message = "Архив миграции сохранён"
        }
    }

    val settingsBody: @Composable ColumnScope.() -> Unit = {
        Text("Настройки", color = palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = settings.userName,
            onValueChange = { v -> scope.launch { vm.updateSettings { it.copy(userName = v) } } },
            label = { Text("Как к вам обращаться") },
            modifier = Modifier.fillMaxWidth(),
            colors = marsFieldColors
        )

        Text("Оформление", color = palette.text, fontWeight = FontWeight.SemiBold)
        AppTheme.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { theme ->
                    val active = settings.appTheme == theme
                    val themePalette = paletteFor(theme)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) themePalette.accent.copy(alpha = 0.22f) else palette.card)
                            .border(
                                1.dp,
                                if (active) themePalette.accent else MarsOutline,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { scope.launch { vm.updateSettings { it.withTheme(theme) } } }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(themePalette.accent)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = theme.labelRu,
                            color = if (active) palette.text else palette.textMuted,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        Text("Насыщенность эффектов", color = palette.text, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EffectIntensity.entries.forEach { level ->
                MarsChoiceChip(
                    label = level.labelRu,
                    selected = settings.effects == level,
                    onClick = { scope.launch { vm.updateSettings { it.withEffects(level) } } }
                )
            }
        }
        Text(
            "Влияет на свечения, градиенты и ореол Марса. Не влияет на читаемость текста.",
            color = palette.textMuted,
            fontSize = 12.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.card)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Уменьшить анимации", color = palette.text, fontWeight = FontWeight.Medium)
                Text(
                    "Мгновенная смена состояний без декоративного движения",
                    color = palette.textMuted,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = settings.reduceAnimations,
                onCheckedChange = { v ->
                    scope.launch { vm.updateSettings { it.copy(reduceAnimations = v) } }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.text,
                    checkedTrackColor = palette.accent,
                    uncheckedThumbColor = palette.textMuted,
                    uncheckedTrackColor = palette.card
                )
            )
        }

        MarsSecondaryButton(
            "Образы Марса",
            onClick = { nav.navigate(Routes.MarsImages) },
            modifier = Modifier.fillMaxWidth()
        )
        MarsSecondaryButton(
            text = if (settings.syncPaired) {
                "Синхронизация с ПК · ${if (pendingSync > 0) "к отправке $pendingSync" else "всё отправлено"}"
            } else {
                "Синхронизация с ПК · не сопряжено"
            },
            onClick = { nav.navigate(Routes.Sync) },
            modifier = Modifier.fillMaxWidth()
        )
        MarsSecondaryButton(
            "Как связать телефон и ПК",
            onClick = { nav.navigate(Routes.SyncGuide) },
            modifier = Modifier.fillMaxWidth()
        )
        MarsSecondaryButton(
            "Статистика",
            onClick = { nav.navigate(Routes.Stats) },
            modifier = Modifier.fillMaxWidth()
        )

        if (hasMigrationArchive) {
            Text("Восстановление", color = palette.text, fontWeight = FontWeight.SemiBold)
            MarsSecondaryButton(
                "Выгрузить архив миграции",
                onClick = {
                    scope.launch {
                        val json = vm.migrationArchiveJson()
                        if (!json.isNullOrBlank()) {
                            pendingArchiveJson = json
                            archiveLauncher.launch("mars_migration_archive_${System.currentTimeMillis()}.json")
                        } else {
                            hasMigrationArchive = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            "Уведомления: если разрешение не выдано, включите его в настройках Android → " +
                "Приложения → Ежедневник Марса → Уведомления.",
            color = palette.textMuted,
            fontSize = 12.sp
        )
        if (message != null) Text(message!!, color = palette.highlight, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(24.dp))
    }

    ThemeSceneShell(
        screen = ThemeSceneScreen.Settings,
        fallback = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = settingsBody
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
                content = settingsBody
            )
        }
    )
}

@Composable
internal fun MarsImagesPreviewScreen(nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val moodFromIntent = activity?.intent?.getStringExtra("mars_preview_mood")?.let { raw ->
        MarsMood.entries.find {
            it.assetBase.equals(raw, ignoreCase = true) ||
                it.name.equals(raw, ignoreCase = true) ||
                it.assetBase.removePrefix("mars_").equals(raw, ignoreCase = true)
        }
    }
    var selectedMood by remember { mutableStateOf(moodFromIntent ?: MarsMood.DEFAULT) }
    LaunchedEffect(moodFromIntent) {
        if (moodFromIntent != null) selectedMood = moodFromIntent
    }
    val dateLabel = remember { LocalDate.now().format(ruDateFull) }
    val pagePad = Modifier.padding(horizontal = 20.dp)
    val contentWidth = Modifier.fillMaxWidth(0.64f)
    val scrollState = rememberScrollState()

    ScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            MarsBackgroundPresence(
                mood = selectedMood,
                presenceAlpha = 0.40f,
                scrollOffsetPx = scrollState.value.toFloat(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 76.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = palette.text)
                    }
                    Text("Образы Марса", color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Предпросмотр. Задачи и статистика не меняются.",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    modifier = pagePad
                )
                Column(modifier = pagePad.padding(top = 4.dp)) {
                    Text("Ежедневник Марса", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Сегодня", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(dateLabel, color = palette.textMuted, fontSize = 14.sp)
                }
                Row(modifier = pagePad, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryChip("Всего", 2, palette.text)
                    SummaryChip("Готово", 1, StatusDone)
                    SummaryChip("Открыто", 1, StatusOpen)
                    SummaryChip("Просрочено", 0, StatusOverdue)
                }
                Column(
                    modifier = pagePad
                        .then(contentWidth)
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.card)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(TaskStatus.OPEN)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Пример задачи", color = palette.text, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Открыта · Обычный", color = palette.textMuted, fontSize = 12.sp)
                }
                Column(modifier = pagePad.then(contentWidth)) {
                    Text("Состояние", color = palette.textMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedMood.previewLabelRu(),
                        color = palette.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(selectedMood.labelRu(), color = palette.textMuted, fontSize = 14.sp)
                }
                Text(
                    text = "Выберите образ",
                    color = palette.text,
                    fontWeight = FontWeight.SemiBold,
                    modifier = pagePad.padding(top = 4.dp)
                )
                marsPreviewOptions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = pagePad.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { option ->
                            val active = selectedMood == option.mood
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (active) palette.accent.copy(alpha = 0.28f) else palette.card)
                                    .border(
                                        width = 1.dp,
                                        color = if (active) palette.accent else MarsOutline,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedMood = option.mood }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (active) palette.text else palette.textMuted,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        if (rowOptions.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                MarsSecondaryButton(
                    text = "Вернуться к автоматическому режиму",
                    onClick = { nav.popBackStack() },
                    modifier = pagePad.padding(top = 8.dp)
                )
            }
        }
    }
}
