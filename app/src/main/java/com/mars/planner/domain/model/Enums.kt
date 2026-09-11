package com.mars.planner.domain.model

enum class TaskStatus(val key: String, val labelRu: String) {
    OPEN("open", "Открыта"),
    DONE("done", "Выполнено");

    companion object {
        fun fromKey(key: String): TaskStatus =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: OPEN

        /** Преобразование старых статусов Mars v1. */
        fun fromLegacyKey(key: String): TaskStatus = when (key.lowercase()) {
            "done", "cancelled" -> DONE
            else -> OPEN
        }
    }

    val isTerminal: Boolean get() = this == DONE
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

/** Фильтр списков задач: только открытые/выполненные плюс просроченные. */
enum class TaskFilter(val key: String, val labelRu: String) {
    ALL("all", "Все"),
    OPEN("open", "Открыты"),
    DONE("done", "Выполнено"),
    OVERDUE("overdue", "Просрочено");

    companion object {
        fun fromKey(key: String): TaskFilter =
            entries.find { it.key == key } ?: ALL
    }
}

/** Семь тем «Рубежа», адаптированных для телефона. */
enum class AppTheme(val key: String, val labelRu: String) {
    ORBIT("orbit", "Орбита"),
    NEBULA("nebula", "Туманность"),
    WHITE_STATION("white-station", "Белая станция"),
    ASH_AMBER("ash-amber", "Пепел и янтарь"),
    POLAR_NIGHT("polar-night", "Полярная ночь"),
    LIGHT_CONCRETE("light-concrete", "Светлый бетон"),
    SHELTER("shelter-terminal", "Убежище");

    companion object {
        fun fromKey(key: String): AppTheme {
            val k = key.trim().lowercase()
            if (k == "bunker") return SHELTER
            return entries.find { it.key == k } ?: ORBIT
        }
    }
}

/**
 * Насыщенность декоративных эффектов: свечения, градиенты, ореол Марса.
 * [factor] лежит в 0f..1f — в этом же виде значение хранится в настройках.
 */
enum class EffectIntensity(val key: String, val labelRu: String, val factor: Float) {
    OFF("off", "Без эффектов", 0f),
    SOFT("soft", "Мягкие", 0.5f),
    NORMAL("normal", "Обычные", 1f);

    /** Масштабирует декоративную альфу/величину; при OFF возвращает 0. */
    fun scale(value: Float): Float = value * factor

    companion object {
        fun fromKey(key: String): EffectIntensity =
            entries.find { it.key == key } ?: NORMAL

        /** Ближайший уровень к сохранённому числовому значению. */
        fun fromFactor(factor: Float): EffectIntensity =
            entries.minByOrNull { kotlin.math.abs(it.factor - factor) } ?: NORMAL
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

enum class SyncOp(val key: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete");

    companion object {
        fun fromKey(key: String): SyncOp =
            entries.find { it.key == key } ?: UPDATE
    }
}

enum class SyncEntityType(val key: String) {
    PROJECT("project"),
    TASK("task");

    companion object {
        fun fromKey(key: String): SyncEntityType =
            entries.find { it.key == key } ?: TASK
    }
}
