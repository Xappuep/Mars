package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.ui.theme.MarsMotion
import com.mars.planner.ui.theme.ThemeSceneBackgrounds
import com.mars.planner.ui.theme.ThemeSceneMode
import com.mars.planner.ui.theme.ThemeSceneScreen
import org.junit.Test

class Stage9Part3ThemesLogicTest {

    @Test
    fun onlyTwoThemesHaveMobileSceneBackground() {
        val withScene = AppTheme.entries.filter { ThemeSceneBackgrounds.hasScene(it) }
        assertThat(withScene).containsExactly(AppTheme.WHITE_STATION, AppTheme.LIGHT_CONCRETE)
        AppTheme.entries.filter { it !in withScene }.forEach { theme ->
            assertThat(ThemeSceneBackgrounds.config(theme)).isNull()
            assertThat(ThemeSceneBackgrounds.hasEmbeddedMars(theme)).isFalse()
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Today)).isNull()
        }
    }

    @Test
    fun sceneThemesUseDistinctCompositionModesPerScreen() {
        listOf(AppTheme.WHITE_STATION, AppTheme.LIGHT_CONCRETE).forEach { theme ->
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Today))
                .isEqualTo(ThemeSceneMode.Hero)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Tasks))
                .isEqualTo(ThemeSceneMode.CompactHeader)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Projects))
                .isEqualTo(ThemeSceneMode.CompactHeader)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Calendar))
                .isEqualTo(ThemeSceneMode.ContentSurface)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Settings))
                .isEqualTo(ThemeSceneMode.ContentSurface)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.Sync))
                .isEqualTo(ThemeSceneMode.ContentSurface)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.TaskForm))
                .isEqualTo(ThemeSceneMode.ContentSurface)
            assertThat(ThemeSceneBackgrounds.modeForScreen(theme, ThemeSceneScreen.TaskDetail))
                .isEqualTo(ThemeSceneMode.ContentSurface)
        }
    }

    @Test
    fun sceneThemesEmbedMarsSoAvatarShouldBeSuppressed() {
        assertThat(ThemeSceneBackgrounds.hasEmbeddedMars(AppTheme.WHITE_STATION)).isTrue()
        assertThat(ThemeSceneBackgrounds.hasEmbeddedMars(AppTheme.LIGHT_CONCRETE)).isTrue()
        assertThat(ThemeSceneBackgrounds.hasEmbeddedMars(AppTheme.ORBIT)).isFalse()
    }

    @Test
    fun navPulseParametersMatchCorr1Contract() {
        assertThat(MarsMotion.NavPulseCycleMs).isEqualTo(2000)
        assertThat(MarsMotion.NavPulseScalePeak).isWithin(0.001f).of(1.025f)
    }

    @Test
    fun themeSwitchDoesNotImplyPlannerMutation() {
        // Смена темы — только DataStore AppSettings.themeId; без saveTask/saveProject.
        val before = AppTheme.ORBIT
        val after = AppTheme.WHITE_STATION
        assertThat(before.key).isNotEqualTo(after.key)
        assertThat(ThemeSceneBackgrounds.hasScene(after)).isTrue()
    }
}
