package com.mars.planner.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.domain.model.EffectIntensity
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.ui.components.MarsEmptyState
import com.mars.planner.ui.components.MarsProgressBar
import com.mars.planner.ui.components.ProvideReduceAnimations
import com.mars.planner.ui.components.TaskCard
import com.mars.planner.ui.theme.LocalMarsPalette
import com.mars.planner.ui.theme.MarsBottomNavigationBar
import com.mars.planner.ui.theme.ProvideMarsAppearance

@Preview(name = "White Station screens", showBackground = true, widthDp = 420, heightDp = 1200)
@Composable
private fun WhiteStationPreview() = LightThemePreviewScaffold(AppTheme.WHITE_STATION)

@Preview(name = "Light Concrete screens", showBackground = true, widthDp = 420, heightDp = 1200)
@Composable
private fun LightConcretePreview() = LightThemePreviewScaffold(AppTheme.LIGHT_CONCRETE)

@Composable
private fun LightThemePreviewScaffold(theme: AppTheme) {
    ProvideMarsAppearance(theme, EffectIntensity.NORMAL) {
        ProvideReduceAnimations(true) {
            val palette = LocalMarsPalette.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Проверка светлой темы", color = palette.text)
                    TaskCard(
                        task = TaskItem(
                            syncUuid = "task-1",
                            title = "Купить краску",
                            priority = TaskPriority.NORMAL,
                            status = TaskStatus.OPEN
                        ),
                        projectName = "Дом",
                        dueDateLabel = "10 сент · 15:00",
                        onClick = {}
                    )
                    PreviewCard("Проекты", "Дом · 2/4") { MarsProgressBar(percent = 50) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.card, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Синхронизация", color = palette.text)
                        Text("Инструкция и статусы читаемы на светлом фоне.", color = palette.textMuted, fontSize = 13.sp)
                    }
                    PreviewCard("Расхождения", "Версия телефона и ПК различимы визуально") {}
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.backgroundElevated, RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Text("Отчёт миграции", color = palette.text)
                        Text("Белый текст здесь не должен исчезать.", color = palette.textMuted, fontSize = 13.sp)
                    }
                    MarsEmptyState(MarsMood.DEFAULT, "Пустое состояние тоже читабельно")
                }
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    MarsBottomNavigationBar(
                        route = "today",
                        items = listOf(
                            Triple("today", "Сегодня", Icons.Outlined.Home),
                            Triple("tasks", "Задачи", Icons.Outlined.CheckCircle),
                            Triple("calendar", "Календарь", Icons.Outlined.CalendarMonth),
                            Triple("settings", "Ещё", Icons.Outlined.Settings)
                        ),
                        onNavigate = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    val palette = LocalMarsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = palette.text)
        Text(subtitle, color = palette.textMuted, fontSize = 13.sp)
        content()
    }
}
