package com.mars.planner.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.ProjectWithStats
import com.mars.planner.ui.components.MarsChoiceChip
import com.mars.planner.ui.components.MarsEmptyState
import com.mars.planner.ui.components.MarsPrimaryButton
import com.mars.planner.ui.components.MarsProgressBar
import com.mars.planner.ui.components.marsListItemMotion
import com.mars.planner.ui.theme.LocalAppTheme
import com.mars.planner.ui.theme.LocalMarsPalette
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.ThemeSceneScreen
import com.mars.planner.ui.theme.ThemeSceneShell
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun ProjectsScreen(vm: AppViewModel, nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val projects by vm.projectsWithStats.collectAsState()
    val scope = rememberCoroutineScope()
    var showArchive by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProjectItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ProjectItem?>(null) }

    val visible = projects.filter { it.project.archived == showArchive }

    val projectsBody: @Composable ColumnScope.() -> Unit = {
        Text("Проекты", color = palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Задачи можно группировать по проектам. Проект без задач не мешает — его можно убрать в архив.",
            color = palette.textMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarsChoiceChip(
                label = "Активные (${projects.count { !it.project.archived }})",
                selected = !showArchive,
                onClick = { showArchive = false }
            )
            MarsChoiceChip(
                label = "Архив (${projects.count { it.project.archived }})",
                selected = showArchive,
                onClick = { showArchive = true }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        MarsPrimaryButton(
            text = "＋ Новый проект",
            onClick = {
                editing = null
                showEditor = true
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (visible.isEmpty()) {
                    item {
                        MarsEmptyState(
                            mood = MarsMood.DEFAULT,
                            message = if (showArchive) "Архив пуст" else "Пока нет проектов"
                        )
                    }
                }
                itemsIndexed(visible, key = { _, it -> it.project.id }) { index, stats ->
                    CompactProjectCard(
                        stats = stats,
                        modifier = Modifier.marsListItemMotion(index),
                        onOpen = { nav.navigate(Routes.Tasks) },
                        onEdit = {
                            editing = stats.project
                            showEditor = true
                        },
                        onArchive = {
                            scope.launch {
                                vm.archiveProject(stats.project.id, !stats.project.archived)
                            }
                        },
                        onDelete = { confirmDelete = stats.project }
                    )
                }
            }
        }
    }

    ThemeSceneShell(
        screen = ThemeSceneScreen.Projects,
        fallback = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp),
                content = projectsBody
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp),
                content = projectsBody
            )
        }
    )

    if (showEditor) {
        ProjectEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { name, description ->
                showEditor = false
                val base = editing
                scope.launch {
                    vm.saveProject(
                        base?.copy(name = name, description = description)
                            ?: ProjectItem(
                                syncUuid = UUID.randomUUID().toString(),
                                name = name,
                                description = description
                            )
                    )
                }
            }
        )
    }

    confirmDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = palette.backgroundElevated,
            title = { Text("Удалить проект «${project.name}»?") },
            text = {
                Text(
                    "Вместе с проектом удалятся его задачи. Если нужно только убрать проект " +
                        "из списка — используйте архив."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { vm.deleteProject(project.id) }
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun CompactProjectCard(
    stats: ProjectWithStats,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalMarsPalette.current
    val archived = stats.project.archived
    var menuOpen by remember { mutableStateOf(false) }
    val openCount = stats.openTasks
    val doneCount = stats.doneTasks
    val percent = stats.progressPercent
    val cardAlpha = if (archived) 0.78f else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(16.dp))
            .background(if (archived) palette.card.copy(alpha = 0.72f) else palette.card)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(start = 14.dp, top = 12.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stats.project.name,
                    color = palette.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stats.project.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stats.project.description,
                        color = palette.textMuted,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .semantics { contentDescription = "Меню проекта ${stats.project.name}" }
                        .size(44.dp)
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
                    DropdownMenuItem(
                        text = { Text("Изменить") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (archived) "Вернуть" else "В архив") },
                        onClick = {
                            menuOpen = false
                            onArchive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
        ) {
            Text(
                text = "Открыто $openCount · Выполнено $doneCount · $percent%",
                color = if (stats.totalTasks > 0 && doneCount == stats.totalTasks) {
                    StatusDone
                } else {
                    palette.textMuted
                },
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            MarsProgressBar(percent = percent)
        }
    }
}

@Composable
private fun ProjectEditorDialog(
    initial: ProjectItem?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val palette = LocalMarsPalette.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.backgroundElevated,
        title = { Text(if (initial == null) "Новый проект" else "Проект") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    colors = marsFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    colors = marsFieldColors,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), description.trim()) }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/** Выпадающий выбор проекта для задачи; null — «Без проекта». */
@Composable
internal fun ProjectPicker(
    projects: List<ProjectItem>,
    selectedUuid: String?,
    onSelect: (String?) -> Unit
) {
    val palette = LocalMarsPalette.current
    val surface = LocalAppTheme.current.raised.copy(alpha = 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Проект", color = palette.textMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surface)
        ) {
            ProjectPickerRow("Без проекта", selectedUuid == null) { onSelect(null) }
            projects.forEach { project ->
                ProjectPickerRow(project.name, selectedUuid == project.syncUuid) {
                    onSelect(project.syncUuid)
                }
            }
        }
    }
}

@Composable
private fun ProjectPickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalMarsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) palette.accent.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.accent else palette.textMuted.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = if (selected) palette.text else palette.textMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
