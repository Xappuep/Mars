package com.mars.planner.domain.logic

import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus

/**
 * Логика отдельного элемента выполнения в списке задач.
 * UI вызывает [AppViewModel.changeStatus] / [PlannerRepository.setTaskStatus] —
 * тот же путь, что и экран редактирования.
 */
object TaskStatusToggle {

    /** Защита UI от двойного касания (не замена ожидания завершения операции). */
    const val DOUBLE_TAP_COOLDOWN_MS: Long = 500L

    fun nextStatus(current: TaskStatus): TaskStatus = when (current) {
        TaskStatus.OPEN -> TaskStatus.DONE
        TaskStatus.DONE -> TaskStatus.OPEN
    }

    fun accessibilityLabel(current: TaskStatus): String = when (current) {
        TaskStatus.OPEN -> "Отметить задачу выполненной"
        TaskStatus.DONE -> "Вернуть задачу в открытые"
    }

    /**
     * Решает, принимать ли жест.
     *
     * 1. Пока [pendingKeys] содержит ключ задачи (syncUuid) — отказ
     *    (операция ещё выполняется).
     * 2. Если с прошлого принятого жеста по тому же ключу прошло меньше
     *    [cooldownMs] (по монотонным [nowElapsedMs]) — отказ (двойное касание).
     *
     * [nowElapsedMs] должен быть монотонным (например SystemClock.elapsedRealtime()),
     * не календарным временем.
     */
    fun acceptGesture(
        taskKey: String,
        pendingKeys: Set<String>,
        lastAcceptedAtMs: Map<String, Long> = emptyMap(),
        nowElapsedMs: Long = 0L,
        cooldownMs: Long = DOUBLE_TAP_COOLDOWN_MS
    ): Boolean {
        if (taskKey.isBlank() || taskKey in pendingKeys) return false
        val last = lastAcceptedAtMs[taskKey] ?: return true
        return nowElapsedMs - last >= cooldownMs
    }

    /** Фиксирует момент принятого жеста для последующей защиты 500 мс. */
    fun recordAccepted(
        lastAcceptedAtMs: Map<String, Long>,
        taskKey: String,
        nowElapsedMs: Long
    ): Map<String, Long> = lastAcceptedAtMs + (taskKey to nowElapsedMs)

    /** Результат применения переключения без записи в БД (для тестов и UI-предпросмотра). */
    fun applyLocally(task: TaskItem): TaskItem = task.copy(status = nextStatus(task.status))
}
