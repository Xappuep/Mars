package com.mars.planner.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.logic.TaskStatusToggle
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.ui.components.TaskCard
import com.mars.planner.ui.components.TaskCardTestTags
import com.mars.planner.ui.theme.LocalMarsPalette
import com.mars.planner.ui.theme.paletteFor
import com.mars.planner.domain.model.AppTheme
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI: независимые зоны круга и открытия карточки.
 * Запуск на устройстве опционален; компиляция обязательна.
 */
@RunWith(AndroidJUnit4::class)
class TaskCardClickZonesInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sample(status: TaskStatus = TaskStatus.OPEN) = TaskItem(
        id = 1L,
        syncUuid = "uuid-test-circle",
        title = "ТЕСТ КРУГА",
        status = status
    )

    @Test
    fun doneTogglePresentAndDoesNotOpenWhenClicked() {
        var opens = 0
        var toggles = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalMarsPalette provides paletteFor(AppTheme.ORBIT)) {
                TaskCard(
                    task = sample(),
                    onClick = { opens++ },
                    onToggleDone = { toggles++ }
                )
            }
        }
        composeRule.onNodeWithTag(TaskCardTestTags.DONE_TOGGLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TaskCardTestTags.DONE_TOGGLE).performClick()
        composeRule.runOnIdle {
            assertThat(toggles).isEqualTo(1)
            assertThat(opens).isEqualTo(0)
        }
    }

    @Test
    fun openAreaOpensAndDoesNotToggle() {
        var opens = 0
        var toggles = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalMarsPalette provides paletteFor(AppTheme.ORBIT)) {
                TaskCard(
                    task = sample(),
                    onClick = { opens++ },
                    onToggleDone = { toggles++ }
                )
            }
        }
        composeRule.onNodeWithTag(TaskCardTestTags.OPEN_AREA).assertIsDisplayed()
        composeRule.onNodeWithTag(TaskCardTestTags.OPEN_AREA).performClick()
        composeRule.runOnIdle {
            assertThat(opens).isEqualTo(1)
            assertThat(toggles).isEqualTo(0)
        }
    }

    @Test
    fun titleClickOpensViaOpenArea() {
        var opens = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalMarsPalette provides paletteFor(AppTheme.ORBIT)) {
                TaskCard(
                    task = sample(),
                    onClick = { opens++ },
                    onToggleDone = { }
                )
            }
        }
        composeRule.onNodeWithText("ТЕСТ КРУГА").performClick()
        composeRule.runOnIdle { assertThat(opens).isEqualTo(1) }
    }

    @Test
    fun pendingDisablesSecondToggle() {
        var toggles = 0
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalMarsPalette provides paletteFor(AppTheme.ORBIT)) {
                TaskCard(
                    task = sample(),
                    onClick = { },
                    onToggleDone = {
                        toggles++
                        enabled = false
                    },
                    toggleEnabled = enabled
                )
            }
        }
        composeRule.onNodeWithTag(TaskCardTestTags.DONE_TOGGLE).performClick()
        composeRule.onNodeWithTag(TaskCardTestTags.DONE_TOGGLE).performClick()
        composeRule.runOnIdle {
            assertThat(toggles).isEqualTo(1)
            assertThat(TaskStatusToggle.acceptGesture("uuid-test-circle", setOf("uuid-test-circle")))
                .isFalse()
        }
        composeRule.onNodeWithContentDescription("Элемент временно недоступен").assertIsDisplayed()
    }

    @Test
    fun accessibilityDependsOnStatus() {
        composeRule.setContent {
            CompositionLocalProvider(LocalMarsPalette provides paletteFor(AppTheme.ORBIT)) {
                TaskCard(
                    task = sample(TaskStatus.OPEN),
                    onClick = { },
                    onToggleDone = { }
                )
            }
        }
        composeRule.onNodeWithContentDescription("Отметить задачу выполненной").assertIsDisplayed()
    }

    @Test
    fun doneStatusHasDifferentAccessibility() {
        composeRule.setContent {
            CompositionLocalProvider(LocalMarsPalette provides paletteFor(AppTheme.ORBIT)) {
                TaskCard(
                    task = sample(TaskStatus.DONE),
                    onClick = { },
                    onToggleDone = { }
                )
            }
        }
        composeRule.onNodeWithContentDescription("Вернуть задачу в открытые").assertIsDisplayed()
        composeRule.onAllNodesWithTag(TaskCardTestTags.DONE_TOGGLE).assertCountEquals(1)
    }

    @Test
    fun longTitleDoesNotRemoveToggleOrOpenArea() {
        val longTitle = "Очень длинное название задачи для проверки перекрытия круга и зоны открытия " +
            "на узком экране Redmi Note 10 Pro без горизонтальной прокрутки"
        composeRule.setContent {
            CompositionLocalProvider(LocalMarsPalette provides paletteFor(AppTheme.ORBIT)) {
                TaskCard(
                    task = sample().copy(title = longTitle),
                    onClick = { },
                    onToggleDone = { }
                )
            }
        }
        composeRule.onNodeWithTag(TaskCardTestTags.DONE_TOGGLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TaskCardTestTags.OPEN_AREA).assertIsDisplayed()
        composeRule.onNodeWithTag(TaskCardTestTags.ROW).assertIsDisplayed()
    }
}
