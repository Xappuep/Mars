package com.mars.planner.domain.model

enum class TaskStatus(val key: String, val labelRu: String) {
    NEW("new", "Новая"),
    IN_PROGRESS("in_progress", "В работе"),
    DONE("done", "Выполнено"),
    POSTPONED("postponed", "Перенесено"),
    NOT_DONE("not_done", "Не выполнено"),
    CANCELLED("cancelled", "Отменено");

    companion object {
        fun fromKey(key: String): TaskStatus =
            entries.find { it.key == key } ?: NEW
    }

    val isTerminal: Boolean get() = this == DONE || this == CANCELLED
    val isCompletedLike: Boolean get() = this == DONE
}

enum class TaskPriority(val key: String, val labelRu: String, val rank: Int) {
    LOW("low", "Низкий", 0),
    NORMAL("normal", "Обычный", 1),
    HIGH("high", "Высокий", 2);

    companion object {
        fun fromKey(key: String): TaskPriority =
            entries.find { it.key == key } ?: NORMAL
    }
}

enum class EnhancementStatus(val key: String, val labelRu: String) {
    IDEA("idea", "Идея"),
    PLANNED("planned", "Запланировано"),
    IN_PROGRESS("in_progress", "В работе"),
    REALIZED("realized", "Реализовано"),
    DEFERRED("deferred", "Отложено"),
    CANCELLED("cancelled", "Отменено");

    companion object {
        fun fromKey(key: String): EnhancementStatus =
            entries.find { it.key == key } ?: IDEA
    }
}

enum class MotivatorMode(val key: String, val labelRu: String) {
    OFF("off", "Выключен"),
    SOFT("soft", "Мягкий"),
    ADAPTIVE("adaptive", "Адаптивный"),
    STRICT("strict", "Строгий");

    companion object {
        fun fromKey(key: String): MotivatorMode =
            entries.find { it.key == key } ?: ADAPTIVE
    }
}

enum class MarsMood(val assetBase: String) {
    DEFAULT("mars_default"),
    DONE("mars_done"),
    WORKING("mars_working"),
    POSTPONED("mars_postponed"),
    OVERDUE("mars_overdue"),
    SUPPORTIVE("mars_supportive"),
    STRICT("mars_strict")
}

enum class ReminderSnoozeMinutes(val minutes: Int, val labelRu: String) {
    TEN(10, "10 минут"),
    THIRTY(30, "30 минут"),
    SIXTY(60, "60 минут")
}
