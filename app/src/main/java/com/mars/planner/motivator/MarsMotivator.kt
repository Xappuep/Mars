package com.mars.planner.motivator

import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.MotivatorMode
import com.mars.planner.domain.model.TaskStatus

data class MarsReaction(
    val mood: MarsMood,
    val message: String
)

object MarsMotivator {
    /** Реакция на смену статуса: только «открыта» и «выполнено». */
    fun reactionForStatusChange(
        newStatus: TaskStatus,
        overdueCount: Int = 0,
        mode: MotivatorMode = MotivatorMode.ADAPTIVE
    ): MarsReaction {
        if (mode == MotivatorMode.OFF) return MarsReaction(MarsMood.DEFAULT, "")
        return when (newStatus) {
            TaskStatus.DONE -> MarsReaction(
                MarsMood.DONE,
                if (overdueCount > 0) {
                    "Задача закрыта, хотя срок уже прошёл. Это честная работа — горжусь."
                } else {
                    "Отлично! Марс доволен — ещё один шаг сделан."
                }
            )
            TaskStatus.OPEN -> MarsReaction(
                MarsMood.WORKING,
                "Задача снова открыта. Спокойно продолжим — один шаг за другим."
            )
        }
    }

    /** Реакция на перенос срока задачи. */
    fun reactionForPostpone(mode: MotivatorMode = MotivatorMode.ADAPTIVE): MarsReaction {
        if (mode == MotivatorMode.OFF) return MarsReaction(MarsMood.DEFAULT, "")
        return MarsReaction(
            MarsMood.POSTPONED,
            "Срок перенесён. Выбери реальную дату — я напомню вовремя."
        )
    }

    fun reactionForManyOverdue(overdueCount: Int, mode: MotivatorMode): MarsReaction {
        if (mode == MotivatorMode.OFF) return MarsReaction(MarsMood.DEFAULT, "")
        val strict = mode == MotivatorMode.STRICT ||
            (mode == MotivatorMode.ADAPTIVE && overdueCount >= 3)
        return if (strict) {
            MarsReaction(
                MarsMood.STRICT,
                "Просрочено задач: $overdueCount. Выбери 1–3 реально выполнимые на сегодня — остальное подождёт."
            )
        } else {
            MarsReaction(
                MarsMood.SUPPORTIVE,
                "Есть просроченные задачи. Давай спокойно выберем, с чего начать сегодня."
            )
        }
    }

    /** Реакция на завершение проекта: все задачи проекта выполнены. */
    fun reactionForProjectCompleted(projectName: String, mode: MotivatorMode): MarsReaction {
        if (mode == MotivatorMode.OFF) return MarsReaction(MarsMood.DEFAULT, "")
        return MarsReaction(MarsMood.DONE, "Проект «$projectName» закрыт полностью. Хорошая работа.")
    }

    fun greetingMessage(userName: String, done: Int, total: Int): String {
        val name = userName.ifBlank { "друг" }
        return when {
            total == 0 -> "Привет, $name. Сегодня можно начать с малого."
            done == total -> "Привет, $name. Все задачи дня закрыты — красиво!"
            else -> "Привет, $name. Сегодня $done из $total уже сделано."
        }
    }
}
