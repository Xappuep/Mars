package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.logic.TaskStatusToggle
import com.mars.planner.domain.model.TaskStatus
import org.junit.Test

/**
 * Защита от быстрого двойного касания круга (500 мс) + pending.
 * Часы управляемые — без Thread.sleep.
 */
class Stage9TaskToggleDebounceTest {

    /** Имитация пути списка: accept → changeStatus (+ sync op) без Room. */
    private class TogglePipeline {
        var clockMs: Long = 1_000L
        var pending = setOf<String>()
        var lastAccepted = mapOf<String, Long>()
        var changeStatusCalls = 0
        var syncOps = 0
        var statusByUuid = mutableMapOf<String, TaskStatus>()

        fun onToggle(uuid: String): Boolean {
            if (!TaskStatusToggle.acceptGesture(
                    taskKey = uuid,
                    pendingKeys = pending,
                    lastAcceptedAtMs = lastAccepted,
                    nowElapsedMs = clockMs
                )
            ) {
                return false
            }
            lastAccepted = TaskStatusToggle.recordAccepted(lastAccepted, uuid, clockMs)
            pending = pending + uuid
            val current = statusByUuid.getOrPut(uuid) { TaskStatus.OPEN }
            statusByUuid[uuid] = TaskStatusToggle.nextStatus(current)
            changeStatusCalls++
            syncOps++
            // Быстрое завершение операции (как на устройстве) — pending снимается,
            // но cooldown 500 мс всё ещё блокирует повтор.
            pending = pending - uuid
            return true
        }

        fun onToggleKeepPending(uuid: String): Boolean {
            if (!TaskStatusToggle.acceptGesture(
                    taskKey = uuid,
                    pendingKeys = pending,
                    lastAcceptedAtMs = lastAccepted,
                    nowElapsedMs = clockMs
                )
            ) {
                return false
            }
            lastAccepted = TaskStatusToggle.recordAccepted(lastAccepted, uuid, clockMs)
            pending = pending + uuid
            changeStatusCalls++
            syncOps++
            return true
        }
    }

    @Test
    fun firstTapAccepted() {
        val p = TogglePipeline()
        assertThat(p.onToggle("u-1")).isTrue()
        assertThat(p.changeStatusCalls).isEqualTo(1)
        assertThat(p.syncOps).isEqualTo(1)
        assertThat(p.statusByUuid["u-1"]).isEqualTo(TaskStatus.DONE)
    }

    @Test
    fun secondTapAfter100MsIgnored() {
        val p = TogglePipeline()
        assertThat(p.onToggle("u-1")).isTrue()
        p.clockMs += 100
        assertThat(p.onToggle("u-1")).isFalse()
        assertThat(p.changeStatusCalls).isEqualTo(1)
        assertThat(p.syncOps).isEqualTo(1)
        assertThat(p.statusByUuid["u-1"]).isEqualTo(TaskStatus.DONE)
    }

    @Test
    fun secondTapAfter499MsIgnored() {
        val p = TogglePipeline()
        assertThat(p.onToggle("u-1")).isTrue()
        p.clockMs += 499
        assertThat(p.onToggle("u-1")).isFalse()
        assertThat(p.changeStatusCalls).isEqualTo(1)
        assertThat(p.syncOps).isEqualTo(1)
    }

    @Test
    fun tapAfter500MsAccepted() {
        val p = TogglePipeline()
        assertThat(p.onToggle("u-1")).isTrue()
        p.clockMs += 500
        assertThat(p.onToggle("u-1")).isTrue()
        assertThat(p.changeStatusCalls).isEqualTo(2)
        assertThat(p.syncOps).isEqualTo(2)
        assertThat(p.statusByUuid["u-1"]).isEqualTo(TaskStatus.OPEN)
    }

    @Test
    fun cooldownIsPerUuid() {
        val p = TogglePipeline()
        assertThat(p.onToggle("u-a")).isTrue()
        p.clockMs += 100
        assertThat(p.onToggle("u-b")).isTrue()
        assertThat(p.onToggle("u-a")).isFalse()
        assertThat(p.changeStatusCalls).isEqualTo(2)
        assertThat(p.statusByUuid["u-a"]).isEqualTo(TaskStatus.DONE)
        assertThat(p.statusByUuid["u-b"]).isEqualTo(TaskStatus.DONE)
    }

    @Test
    fun pendingGuardStillBlocks() {
        val p = TogglePipeline()
        assertThat(p.onToggleKeepPending("u-1")).isTrue()
        p.clockMs += 1_000 // cooldown истёк, но pending ещё держит
        assertThat(p.onToggle("u-1")).isFalse()
        assertThat(p.changeStatusCalls).isEqualTo(1)
        p.pending = p.pending - "u-1"
        assertThat(p.onToggle("u-1")).isTrue()
        assertThat(p.changeStatusCalls).isEqualTo(2)
    }

    @Test
    fun ignoredTapDoesNotCallChangeStatus() {
        val p = TogglePipeline()
        p.onToggle("u-1")
        val before = p.changeStatusCalls
        p.clockMs += 50
        p.onToggle("u-1")
        assertThat(p.changeStatusCalls).isEqualTo(before)
    }

    @Test
    fun ignoredTapDoesNotCreateSyncOp() {
        val p = TogglePipeline()
        p.onToggle("u-1")
        val before = p.syncOps
        p.clockMs += 200
        p.onToggle("u-1")
        assertThat(p.syncOps).isEqualTo(before)
    }

    @Test
    fun textOpenPathIndependentOfToggleGate() {
        // Модель TaskCard: open и toggle — разные коллбеки; open не проходит через acceptGesture.
        val p = TogglePipeline()
        var opens = 0
        val onOpen: () -> Unit = { opens++ }
        val onToggle: () -> Unit = { p.onToggle("u-1") }
        onToggle()
        p.clockMs += 50
        onToggle() // ignored
        onOpen()
        onOpen()
        assertThat(p.changeStatusCalls).isEqualTo(1)
        assertThat(opens).isEqualTo(2)
    }

    @Test
    fun accessibilityActivationUsesSameGateWithoutBreakingLabels() {
        assertThat(TaskStatusToggle.accessibilityLabel(TaskStatus.OPEN))
            .isEqualTo("Отметить задачу выполненной")
        assertThat(TaskStatusToggle.accessibilityLabel(TaskStatus.DONE))
            .isEqualTo("Вернуть задачу в открытые")

        // TalkBack / semantics click → тот же onToggleDone → тот же acceptGesture.
        val p = TogglePipeline()
        fun a11yActivate() = p.onToggle("u-a11y")
        assertThat(a11yActivate()).isTrue()
        p.clockMs += 100
        assertThat(a11yActivate()).isFalse()
        p.clockMs += 400
        assertThat(a11yActivate()).isTrue()
        assertThat(p.changeStatusCalls).isEqualTo(2)
        assertThat(TaskStatusToggle.accessibilityLabel(p.statusByUuid.getValue("u-a11y")))
            .isEqualTo("Отметить задачу выполненной")
    }
}
