package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.prefs.AppSettings
import org.junit.Test

/**
 * Устаревший ключ motivator_mode не входит в модель и не читается —
 * наличие значения в DataStore не может вызвать ошибку маппинга.
 */
class Stage9Part2SettingsCompatTest {

    @Test
    fun appSettingsConstructsWithoutMotivatorOrDemoLoaded() {
        val s = AppSettings(
            userName = "Михаил",
            morningReminderEnabled = true,
            reduceAnimations = false
        )
        assertThat(s.userName).isEqualTo("Михаил")
        assertThat(s.copy(userName = "X").userName).isEqualTo("X")
    }
}
