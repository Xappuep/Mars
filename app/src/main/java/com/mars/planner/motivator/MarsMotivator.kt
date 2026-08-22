package com.mars.planner.motivator

import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.MotivatorMode
import com.mars.planner.domain.model.TaskStatus

data class MarsReaction(
    val mood: MarsMood,
    val message: String
)

object MarsMotivator {
    fun reactionForStatusChange(
        newStatus: TaskStatus,
        postponeCount: Int = 0,
        mode: MotivatorMode = MotivatorMode.ADAPTIVE
    ): MarsReaction {
        if (mode == MotivatorMode.OFF) {
            return MarsReaction(MarsMood.DEFAULT, "")
        }
        return when (newStatus) {
            TaskStatus.DONE -> MarsReaction(
                MarsMood.DONE,
                if (postponeCount >= 2) {
                    "Ты закрыл задачу, которую переносил $postponeCount раз${plural(postponeCount)}. Горжусь тобой."
                } else {
                    "Отлично! Марс доволен — ещё один шаг сделан."
                }
            )
            TaskStatus.IN_PROGRESS -> MarsReaction(
                MarsMood.WORKING,
                "Спокойно продолжай. Один шаг за другим — и получится."
            )
            TaskStatus.POSTPONED -> postponeReaction(postponeCount, mode)
            TaskStatus.NOT_DONE -> MarsReaction(
                MarsMood.OVERDUE,
                "Честно. Давай выберем: выполнить сейчас, перенести с причиной или отменить."
            )
            TaskStatus.CANCELLED -> MarsReaction(
                MarsMood.DEFAULT,
                "Хорошо. Иногда отказ — тоже решение. Идём дальше."
            )
            TaskStatus.NEW -> MarsReaction(
                MarsMood.DEFAULT,
                "Новая задача записана. Когда будешь готов — начнём."
            )
        }
    }

    fun reactionForDeferredEnhancement(): MarsReaction = MarsReaction(
        MarsMood.SUPPORTIVE,
        "Основное дело важнее. Я сохраню идею — вернёмся к ней, когда будет подходящий момент."
    )

    fun reactionForManyOverdue(overdueCount: Int, mode: MotivatorMode): MarsReaction {
        if (mode == MotivatorMode.OFF) return MarsReaction(MarsMood.DEFAULT, "")
        val strict = mode == MotivatorMode.STRICT ||
            (mode == MotivatorMode.ADAPTIVE && overdueCount >= 3)
        return if (strict) {
            MarsReaction(
                MarsMood.STRICT,
                "Много открытых дел. Выбери 1–3 реально выполнимые задачи на сегодня — остальное подождёт."
            )
        } else {
            MarsReaction(
                MarsMood.SUPPORTIVE,
                "Есть просроченные задачи. Давай спокойно выберем, с чего начать сегодня."
            )
        }
    }

    fun greetingMessage(userName: String, summaryDone: Int, summaryTotal: Int): String {
        val name = userName.ifBlank { "друг" }
        return when {
            summaryTotal == 0 -> "Привет, $name. Сегодня можно начать с малого."
            summaryDone == summaryTotal -> "Привет, $name. Все задачи дня закрыты — красиво!"
            else -> "Привет, $name. Сегодня $summaryDone из $summaryTotal уже сделано."
        }
    }

    private fun postponeReaction(postponeCount: Int, mode: MotivatorMode): MarsReaction {
        val soft = mode == MotivatorMode.SOFT ||
            (mode == MotivatorMode.ADAPTIVE && postponeCount <= 1) ||
            mode == MotivatorMode.OFF
        return if (soft || postponeCount <= 1) {
            MarsReaction(
                MarsMood.POSTPONED,
                "Ничего страшного. Выбери реальное новое время — я напомню."
            )
        } else {
            MarsReaction(
                MarsMood.STRICT,
                "Эту задачу уже переносили $postponeCount раз${plural(postponeCount)}. Давай решим: выполнить, перенести с причиной, разбить на шаги или отменить."
            )
        }
    }

    private fun plural(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> ""
        n % 10 in 2..4 && n % 100 !in 12..14 -> "а"
        else -> "а"
    }
}
