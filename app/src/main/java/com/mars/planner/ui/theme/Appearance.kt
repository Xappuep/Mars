package com.mars.planner.ui.theme

import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.domain.model.EffectIntensity

/**
 * Оформление хранится в общих настройках как строка темы и число 0f..1f,
 * а в UI используется перечислениями. Здесь только перевод между ними —
 * дублировать состояние темы в настройках не нужно.
 */
val AppSettings.appTheme: AppTheme
    get() = AppTheme.fromKey(themeId)

val AppSettings.effects: EffectIntensity
    get() = EffectIntensity.fromFactor(effectIntensity)

fun AppSettings.withTheme(theme: AppTheme): AppSettings = copy(themeId = theme.key)

fun AppSettings.withEffects(intensity: EffectIntensity): AppSettings =
    copy(effectIntensity = intensity.factor)
